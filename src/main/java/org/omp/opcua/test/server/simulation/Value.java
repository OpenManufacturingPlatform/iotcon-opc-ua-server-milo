package org.omp.opcua.test.server.simulation;

import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

public class Value<T> {
    private T value;
    private DateTime timestamp;

    public Value(T value)  {
        this.value = value;
        this.timestamp = DateTime.now();
    }

    public T getValue() {
        return value;
    }

    public DataValue asDataValue() {
        return new DataValue.Builder()
                .setValue(new Variant(this.value))
                .setSourceTime(this.timestamp)
                .build();
    }

    public void setValue(T value) {
        if ( !this.value.equals(value)) {
            this.value = value;
            this.timestamp = DateTime.now();
        }
    }
}
