package org.omp.opcua.test.server;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "omp.opcua.milo.test")
public class TestConfiguration {
    int numberOfSimple;
    int numberOfArray;
    int arraySize;

    public int getNumberOfSimple() {
        return this.numberOfSimple;
    }

    public int getArraySize() {
        return this.arraySize;
    }

    public int getNumberOfArray() {
        return this.numberOfArray;
    }
}
