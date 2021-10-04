package org.omp.opcua.test.server;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;

import com.google.common.net.InetAddresses;

public final class KeyCertMaterial {

    private final Material material;

    private static class Material {
        private final X509Certificate[] certificateChain;
        private final KeyPair keyPair;

        Material(KeyPair keyPair, X509Certificate[] certificateChain) {
            this.keyPair = keyPair;
            this.certificateChain = certificateChain;
        }
    }

    private KeyCertMaterial(final Material material) {
        this.material = material;
    }

    public X509Certificate[] getServerCertificateChain() {
        return this.material.certificateChain;
    }

    public KeyPair getServerKeyPair() {
        return this.material.keyPair;
    }

    /**
     * Create new, self-signed key-cert material.
     *
     * @param hostnames The hostnames (and IP addresses to add).
     * @return The newly created, self-signed key material.
     * @throws Exception If anything goes wrong.
     */
    @SuppressWarnings("UnstableApiUsage")
    public static KeyCertMaterial createSelfSigned(Set<String> hostnames) throws Exception {

        var keyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);

        var applicationUri = "urn:omp:milo:tests:server:" + UUID.randomUUID();
        var builder = new SelfSignedCertificateBuilder(keyPair)
                .setCommonName("OMP OPC UA Test Server")
                .setOrganization("Red Hat, Inc")
                .setLocalityName("Raleigh")
                .setStateName("NC")
                .setCountryCode("US")
                .setApplicationUri(applicationUri);

        for (var hostname : hostnames) {
            try {
                InetAddresses.forString(hostname);
                builder.addIpAddress(hostname);
            } catch (Exception e) {
                builder.addDnsName(hostname);
            }
        }

        return new KeyCertMaterial(new Material(keyPair, new X509Certificate[]{builder.build()}));
    }

    /**
     * Load key material from a keystore.
     *
     * @param source The file to load from
     * @param type The type to load (e.g. "PKCS")
     * @param storePassword The password of the store.
     * @param keyAlias The alias of the key entry.
     * @param keyPassword The password of the key
     * @param certificateAlias The alias of the certificate entry.
     * @return The loaded key material.
     * @throws Exception If anything goes wrong.
     */
    public static KeyCertMaterial load(
            Path source,
            String type,
            char[] storePassword,
            String keyAlias,
            char[] keyPassword,
            String certificateAlias
    ) throws Exception {

        var keyStore = KeyStore.getInstance(type);

        try (InputStream input = Files.newInputStream(source)) {
            keyStore.load(input, storePassword);
        }

        var privateKey = keyStore.getKey(keyAlias, keyPassword);
        if (!(privateKey instanceof PrivateKey)) {
            throw new Exception(String.format("Unable to find private key for alias: %s", keyAlias));
        }

        var certificate = keyStore.getCertificate(certificateAlias);
        if (certificate == null) {
            throw new Exception(String.format("Unable to find certificate for alias: %s", certificateAlias));
        }
        var keyPair = new KeyPair(certificate.getPublicKey(), (PrivateKey) privateKey);

        var chain = keyStore.getCertificateChain(certificateAlias);
        if (chain == null) {
            throw new Exception(String.format("Unable to find a certificate chain for alias: %s", certificateAlias));
        }
        var certificateChain = new ArrayList<X509Certificate>();
        for (var cert : chain ) {
            if (!(cert instanceof  X509Certificate)) {
                throw new Exception(String.format("Certificate chain contained a non-X509 certificate: %s", cert));
            }
            certificateChain.add((X509Certificate) cert);
        }
        return new KeyCertMaterial(new Material(keyPair, certificateChain.toArray(X509Certificate[]::new)));

    }
}
