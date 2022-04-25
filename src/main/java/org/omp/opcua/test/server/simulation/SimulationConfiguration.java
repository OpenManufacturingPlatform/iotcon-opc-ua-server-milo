package org.omp.opcua.test.server.simulation;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "omp.opcua.milo.simulation",  namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
public interface SimulationConfiguration {
    int numberOfDevices();
}
