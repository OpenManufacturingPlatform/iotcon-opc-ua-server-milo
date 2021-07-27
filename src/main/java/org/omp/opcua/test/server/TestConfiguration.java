/*
 * Copyright 2021 Red Hat, Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
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
