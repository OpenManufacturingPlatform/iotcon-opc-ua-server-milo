package org.omp.opcua.test.server.simulation;

import java.util.Random;

public class SmoothJitter {
    final double[] data;
    final Random r;
    final double bandwidth;

    int idx;
    double sum;

    public SmoothJitter(Random r, int values, double bandwidth) {
        this.data = new double[values];
        this.bandwidth = bandwidth;
        for (int i = 0; i < values; i++) {
            this.data[i] = r.nextGaussian() * bandwidth;
            this.sum += this.data[i];
        }
        this.r = r;
    }

    public double next() {
        idx = ++idx % data.length;

        sum -= data[idx];
        data[idx] = r.nextGaussian() * bandwidth;
        sum += data[idx];

        return sum / ((double) data.length);
    }

}