package org.omp.opcua.test.server.simulation;

import java.util.Random;

import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;

public class Device1 {

    private static final Random R = new Random();

    private final Value<Double> ambientTemperature;
    private final Value<Double> temperature;
    private final Value<Boolean> active;
    private final Value<Double> powerConsumption;

    public Device1() {
        this.ambientTemperature = new Value<>(15.0);
        this.temperature = new Value<>(15.0);
        this.powerConsumption = new Value<>(0.0);
        this.active = new Value<>(false);
        tick();
    }

    public void tick() {
        double diff = this.temperature.getValue() - this.ambientTemperature.getValue() ;
        diff = diff * 0.9;
        if (this.active.getValue()) {
            this.powerConsumption.setValue(1000 + 100 * R.nextGaussian());
            diff += 2;
        } else {
            this.powerConsumption.setValue(0.0);
        }

        this.temperature.setValue(this.ambientTemperature.getValue() + diff);
    }

    public DataValue getTemperature() {
        return temperature.asDataValue();
    }

    public DataValue getAmbientTemperature() {
        return ambientTemperature.asDataValue();
    }

    public void setAmbientTemperature(DataValue dataValue) {
        var value = dataValue.getValue().getValue();
        if (value instanceof Number ) {
            this.ambientTemperature.setValue(((Number) value).doubleValue());
        }
    }

    public DataValue getPowerConsumption() {
        return powerConsumption.asDataValue();
    }

    public DataValue isActive() {
        return active.asDataValue();
    }

    public void setActive(DataValue dataValue) {
        var value = dataValue.getValue().getValue();
        this.active.setValue(Boolean.TRUE.equals(value));
    }
}
