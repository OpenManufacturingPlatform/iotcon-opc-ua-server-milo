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

import java.lang.reflect.Array;
import java.util.List/*
 * Copyright 2021 Red Hat, Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.dtd.DataTypeDictionaryManager;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilters;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

public class TestNamespace extends ManagedNamespaceWithLifecycle {

    public static final String NAMESPACE_URI = "urn:omp:milo:test-namespace";
    private static final Random R = new Random();

    private final TestConfiguration configuration;
    private final DataTypeDictionaryManager dictionaryManager;
    private final SubscriptionModel subscriptionModel;

    private static class TestType {
        private final String name;
        private final NodeId typeId;
        private final Supplier<Object> generator;
        private final Function<Integer, Object> arrayCreator;

        TestType(final String name,
                 final NodeId typeId,
                 final Function<Integer, Object> arrayCreator,
                 final Supplier<Object> generator) {
            this.name = name;
            this.typeId = typeId;
            this.arrayCreator = arrayCreator;
            this.generator = generator;
        }

        public String getName() {
            return this.name;
        }

        public NodeId getTypeId() {
            return this.typeId;
        }

        public DataValue next() {
            return new DataValue(new Variant(this.generator.get()));
        }

        public DataValue nextArray(int count) {
            var value = this.arrayCreator.apply(count);
            for (int i = 0; i < count; i++) {
                Array.set(value, i, this.generator.get());
            }
            return new DataValue(new Variant(value));
        }
    }

    private static final TestType[] SINGLE_TYPES = new TestType[]{
            new TestType("Int64", Identifiers.Int64, Long[]::new, R::nextLong),
            new TestType("Float", Identifiers.Float, Double[]::new, R::nextDouble),
            new TestType("Boolean", Identifiers.Boolean, Boolean[]::new, R::nextBoolean),
    };

    // Array types, for now we use the same as the single types.
    private static final TestType[] ARRAY_TYPES = SINGLE_TYPES;

    TestNamespace(final OpcUaServer server, final TestConfiguration configuration) {
        super(server, NAMESPACE_URI);
        this.configuration = configuration;

        this.subscriptionModel = new SubscriptionModel(server, this);
        this.dictionaryManager = new DataTypeDictionaryManager(getNodeContext(), NAMESPACE_URI);

        getLifecycleManager().addLifecycle(this.dictionaryManager);
        getLifecycleManager().addLifecycle(this.subscriptionModel);

        getLifecycleManager().addStartupTask(this::populateNamespace);
    }

    private void populateNamespace() {
        var base = createBaseFolder();
        populateSingle(base, SINGLE_TYPES, this.configuration.getNumberOfSimple());
        populateArray(base, ARRAY_TYPES, this.configuration.getNumberOfArray(), this.configuration.getArraySize());
    }

    private UaFolderNode createBaseFolder() {
        NodeId folderNodeId = newNodeId("OMP");
        UaFolderNode folderNode = new UaFolderNode(
                getNodeContext(),
                folderNodeId,
                newQualifiedName("OMP"),
                LocalizedText.english("OMP")
        );
        getNodeManager().addNode(folderNode);

        folderNode.addReference(new Reference(
                folderNode.getNodeId(),
                Identifiers.Organizes,
                Identifiers.ObjectsFolder.expanded(),
                false
        ));

        return folderNode;
    }

    private static UInteger[] toDimensions(int dimensions) {
        if (dimensions <= 0) {
            return null;
        } else {
            return new UInteger[]{UInteger.valueOf(dimensions)};
        }
    }

    private void populateType(
            final UaFolderNode base,
            final String prefix,
            final TestType[] types,
            final int instances,
            final int dimensions,
            final Function<TestType, Supplier<DataValue>> generator) {

        var folder = new UaFolderNode(
                getNodeContext(),
                newNodeId("OMP/" + prefix),
                newQualifiedName(prefix + "Type"),
                LocalizedText.english(prefix + "Type")
        );
        getNodeManager().addNode(folder);
        base.addOrganizes(folder);

        var dim = toDimensions(dimensions);

        for (int i = 0; i < instances; i++) {
            var name = String.format("%s%06d", prefix, i);

            var instanceFolder = new UaFolderNode(
                    getNodeContext(),
                    newNodeId("OMP/" + prefix + "/" + name),
                    newQualifiedName(name),
                    LocalizedText.english(String.format("%sType(%s)", prefix, name))
            );
            getNodeManager().addNode(instanceFolder);
            folder.addOrganizes(instanceFolder);

            for (TestType t : types) {

                var node = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
                        .setNodeId(newNodeId("OMP/" + prefix + "/" + name + "/" + t.getName()))
                        .setAccessLevel(AccessLevel.READ_WRITE)
                        .setUserAccessLevel(AccessLevel.READ_WRITE)
                        .setBrowseName(newQualifiedName(t.getName()))
                        .setDisplayName(LocalizedText.english(t.getName()))
                        .setDataType(t.getTypeId())
                        .setArrayDimensions(dim)
                        .setTypeDefinition(Identifiers.BaseDataVariableType)
                        .build();

                var gen = generator.apply(t);

                node.getFilterChain().addLast(
                        AttributeFilters.getValue(
                                ctx -> gen.get()
                        )
                );

                getNodeManager().addNode(node);
                instanceFolder.addOrganizes(node);

            }

        }
    }

    private void populateSingle(final UaFolderNode base, TestType[] types, final int instances) {
        populateType(base, "Single", types, instances, 0, t -> t::next);
    }

    private void populateArray(final UaFolderNode base, TestType[] types, final int instances, final int arraySize) {
        populateType(base, "Array", types, instances, arraySize, t -> () -> t.nextArray(arraySize));
    }

    @Override
    public void onDataItemsCreated(final List<DataItem> dataItems) {
        this.subscriptionModel.onDataItemsCreated(dataItems);
    }

    @Override
    public void onDataItemsModified(final List<DataItem> dataItems) {
        this.subscriptionModel.onDataItemsModified(dataItems);
    }

    @Override
    public void onDataItemsDeleted(final List<DataItem> dataItems) {
        this.subscriptionModel.onDataItemsDeleted(dataItems);
    }

    @Override
    public void onMonitoringModeChanged(final List<MonitoredItem> monitoredItems) {
        this.subscriptionModel.onMonitoringModeChanged(monitoredItems);
    }
}
