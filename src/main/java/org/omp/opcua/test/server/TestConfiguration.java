package org.omp.opcua.test.server;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "omp.opcua.milo.test",  namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
public interface TestConfiguration {
     int numberOfSimple();
     int arraySize();
     int numberOfArray();
}
