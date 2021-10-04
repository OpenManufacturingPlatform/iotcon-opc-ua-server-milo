package org.omp.opcua.test.server;

import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_ConfigurationError;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.identity.CompositeValidator;
import org.eclipse.milo.opcua.sdk.server.identity.UsernameIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil;
import org.eclipse.milo.opcua.stack.core.UaRuntimeException;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.DefaultTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo;
import org.eclipse.milo.opcua.stack.core.util.CertificateUtil;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedHttpsCertificateBuilder;
import org.eclipse.milo.opcua.stack.server.EndpointConfiguration;
import org.eclipse.milo.opcua.stack.server.security.DefaultServerCertificateValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dentrassi.crypto.pem.PemKeyStoreProvider;
import io.quarkus.runtime.Startup;

@Startup
public class Server {

    private static final Logger LOG = LoggerFactory.getLogger(Server.class);

    public static final String NAME = "OMP OPC UA Test Server";
    public static final String PRODUCT_URI = "urn:omp:milo:test-server";
    public static final Map<String, String> USERS;

    static {
        USERS = new HashMap<>();
        USERS.put("milo", "open-by-default");
    }

    @ConfigProperty(name = "omp.opcua.milo.server.tcp.port", defaultValue = "12686")
    int tcpBindPort;
    @ConfigProperty(name = "omp.opcua.milo.server.tcp.address", defaultValue = "0.0.0.0")
    String tcpBindAddress;
    @ConfigProperty(name = "omp.opcua.milo.server.securityDirectory")
    Path securityDirectory;

    @ConfigProperty(name = "omp.opcua.milo.server.https.selfSigned")
    boolean httpsSelfSigned;
    @ConfigProperty(name = "omp.opcua.milo.server.https.key")
    Optional<Path> tlsKey;
    @ConfigProperty(name = "omp.opcua.milo.server.https.certificate")
    Optional<Path> tlsCrt;

    @ConfigProperty(name = "omp.opcua.milo.server.discovery.hostname")
    Optional<String> discoveryHostname;

    @Inject
    TestConfiguration configuration;

    @PostConstruct
    public void run() throws Exception {

        Files.createDirectories(this.securityDirectory);

        var hostnames = getHostnames();

        var pkiDir = this.securityDirectory.resolve("pki").toFile();

        var loader = KeyCertMaterial.createSelfSigned(hostnames);

        var certificateManager = new DefaultCertificateManager(
                loader.getServerKeyPair(),
                loader.getServerCertificateChain()
        );

        var trustListManager = new DefaultTrustListManager(pkiDir);

        var certificateValidator =
                new DefaultServerCertificateValidator(trustListManager);

        var identityValidator = new UsernameIdentityValidator(
                false,
                authChallenge -> {
                    var username = authChallenge.getUsername();
                    var password = authChallenge.getPassword();
                    var expected = USERS.get(username);
                    return (expected != null) && expected.equals(password);
                }
        );

        var certificate = certificateManager.getCertificates()
                .stream()
                .findFirst()
                .orElseThrow(() -> new UaRuntimeException(Bad_ConfigurationError, "no certificate found"));

        var applicationUri = CertificateUtil
                .getSanUri(certificate)
                .orElseThrow(() -> new UaRuntimeException(Bad_ConfigurationError, "certificate is missing the application URI"));

        var endpointConfigurations = createEndpointConfigurations(certificate, hostnames);

        final KeyPair httpsKeyPair;
        final X509Certificate httpsCertificate;

        if (!this.httpsSelfSigned) {

            var tlsKey = this.tlsKey.orElseThrow(() -> new RuntimeException("Missing TLS key: consider using 'httpsSelfSigned' if you must"));
            var tlsCert = this.tlsCrt.orElseThrow(() -> new RuntimeException("Missing TLS certificate: consider using 'httpsSelfSigned' if you must"));
            LOG.info("Checking TLS material: {} / {}", tlsKey, tlsCert);
            var store = KeyStore.getInstance("PEMCFG", new PemKeyStoreProvider());
            var cfg = String.format("source.key=%s%nsource.cert=%s%ns", tlsKey, tlsCert);
            store.load(new ByteArrayInputStream(cfg.getBytes(StandardCharsets.UTF_8)), null);
            var key = store.getKey("pem", null);
            httpsCertificate = (X509Certificate) store.getCertificate("pem");
            httpsKeyPair = new KeyPair(httpsCertificate.getPublicKey(), (PrivateKey) key);

        } else {

            LOG.info("Using self-signed HTTPS certificate");
            httpsKeyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);
            var httpsCertificateBuilder = new SelfSignedHttpsCertificateBuilder(httpsKeyPair);
            httpsCertificateBuilder.setCommonName(HostnameUtil.getHostname());
            HostnameUtil.getHostnames("0.0.0.0").forEach(httpsCertificateBuilder::addDnsName);
            this.discoveryHostname.ifPresent(httpsCertificateBuilder::addDnsName);
            httpsCertificate = httpsCertificateBuilder.build();

        }

        var serverConfig = OpcUaServerConfig.builder()
                .setApplicationUri(applicationUri)
                .setApplicationName(LocalizedText.english(NAME))
                .setEndpoints(endpointConfigurations)
                .setBuildInfo(
                        new BuildInfo(
                                PRODUCT_URI,
                                "Open Manufacturing Platform",
                                NAME,
                                OpcUaServer.SDK_VERSION,
                                "",
                                DateTime.now()))
                .setCertificateManager(certificateManager)
                .setTrustListManager(trustListManager)
                .setCertificateValidator(certificateValidator)
                .setHttpsKeyPair(httpsKeyPair)
                .setHttpsCertificate(httpsCertificate)
                .setIdentityValidator(new CompositeValidator<>(identityValidator))
                .setProductUri(PRODUCT_URI)
                .build();

        // start server

        var server = new OpcUaServer(serverConfig);
        server.startup().get();

        // add test namespace

        var testNamespace = new TestNamespace(server, this.configuration);
        testNamespace.startup();
    }

    private Set<String> getHostnames() {

        if (this.discoveryHostname.isPresent()) {

            return Collections.singleton(this.discoveryHostname.get());

        } else {

            var hostnames = new LinkedHashSet<String>();
            hostnames.add(HostnameUtil.getHostname());
            hostnames.addAll(HostnameUtil.getHostnames("0.0.0.0"));
            return hostnames;

        }

    }

    private Set<EndpointConfiguration> createEndpointConfigurations(X509Certificate certificate, Set<String> hostnames) {

        var result = new LinkedHashSet<EndpointConfiguration>();

        for (var hostname : hostnames) {
            buildEndpoint(certificate, hostname, result::add);
        }

        return result;

    }

    private void buildEndpoint(X509Certificate certificate, String hostname, Consumer<EndpointConfiguration> consumer) {

        var builder = EndpointConfiguration.newBuilder()
                .setBindAddress(this.tcpBindAddress)
                .setHostname(hostname)
                .setPath("/milo")
                .setCertificate(certificate)
                .setTransportProfile(TransportProfile.TCP_UASC_UABINARY)
                .setBindPort(this.tcpBindPort)
                .addTokenPolicies(USER_TOKEN_POLICY_USERNAME);

        // no security
        consumer.accept(
                builder.copy()
                        .setSecurityPolicy(SecurityPolicy.None)
                        .setSecurityMode(MessageSecurityMode.None)
                        .build()
        );
        // default security
        consumer.accept(
                builder.copy()
                        .setSecurityPolicy(SecurityPolicy.Basic256Sha256)
                        .setSecurityMode(MessageSecurityMode.SignAndEncrypt)
                        .build()
        );
        // no security - discovery
        consumer.accept(
                builder.copy()
                        .setPath("/milo/discovery")
                        .setSecurityPolicy(SecurityPolicy.None)
                        .setSecurityMode(MessageSecurityMode.None)
                        .build()
        );

    }

}
