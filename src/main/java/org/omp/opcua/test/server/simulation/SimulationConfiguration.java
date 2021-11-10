package org.omp.opcua.test.server.simulation;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "omp.opcua.milo.simulation")
public class SimulationConfiguration {
    int numberOfDevices;

    public int getNumberOfDevices() {
        return this.numberOfDevices;
    }

}
