package org.omp.opcua.test.server.simulation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
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
import org.omp.opcua.test.server.TestNamespace;

import io.smallrye.common.annotation.Identifier;

public class SimulationNamespace extends ManagedNamespaceWithLifecycle {
    public static final String NAMESPACE_URI = "urn:omp:milo:simulation-namespace";
    private final SimulationConfiguration configuration;
    private final SubscriptionModel subscriptionModel;
    private final DataTypeDictionaryManager dictionaryManager;
    private final List<Runnable> tasks = new ArrayList<>();
    private final ScheduledExecutorService executor;

    public SimulationNamespace(final OpcUaServer server, final SimulationConfiguration configuration) {
        super(server, NAMESPACE_URI);
        this.configuration = configuration;

        this.subscriptionModel = new SubscriptionModel(server, this);
        this.dictionaryManager = new DataTypeDictionaryManager(getNodeContext(), NAMESPACE_URI);
        this.executor = Executors.newScheduledThreadPool(1);

        getLifecycleManager().addLifecycle(this.dictionaryManager);
        getLifecycleManager().addLifecycle(this.subscriptionModel);

        getLifecycleManager().addStartupTask(this::populateNamespace);
        getLifecycleManager().addShutdownTask(this::stopTicking);
    }

    void tick() {
        for (Runnable r : this.tasks) {
            r.run();
        }
    }

    void stopTicking() {
        this.executor.shutdown();
    }

    private void populateNamespace() {
        var base = createBaseFolder();
        for (int i = 0; i < this.configuration.numberOfDevices; i++) {
            var device = new Device1();
            registerDevice(base, i, device);
        }
        this.executor.scheduleAtFixedRate(this::tick, 0, 1, TimeUnit.SECONDS);
    }

    private void registerDevice(UaFolderNode base, int idx, Device1 device) {

        var name = String.format("Device %04d", idx);

        var folder = new UaFolderNode(
                getNodeContext(),
                newNodeId("OMP/Simulation/" + name),
                newQualifiedName(name),
                LocalizedText.english(name)
        );
        getNodeManager().addNode(folder);
        base.addOrganizes(folder);

        registerVariable(folder, name,  "temperature", "Temperature", Identifiers.Double, device::getTemperature, null);
        registerVariable(folder, name,  "ambientTemperature", "Ambient temperature", Identifiers.Double, device::getAmbientTemperature, device::setAmbientTemperature);
        registerVariable(folder, name,  "powerConsumption", "Power Consumption", Identifiers.Double, device::getPowerConsumption, null);
        registerVariable(folder, name,  "active", "Active", Identifiers.Boolean, device::isActive, device::setActive);

        this.tasks.add(device::tick);
    }

    private void registerVariable(
            UaFolderNode folder,
            String deviceName,
            String name,
            String label,
            NodeId dataType,
            Supplier<DataValue> extractor,
            Consumer<DataValue> injector
            ) {

        var accessLevel = EnumSet.of(AccessLevel.CurrentRead);

        if (injector != null) {
            accessLevel.add(AccessLevel.CurrentWrite);
        }

        var node = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
                .setNodeId(newNodeId("OMP/Simulation/" + deviceName + "/" + name))
                .setAccessLevel(accessLevel)
                .setUserAccessLevel(accessLevel)
                .setBrowseName(newQualifiedName(name))
                .setDisplayName(LocalizedText.english(label))
                .setDataType(dataType)
                .setTypeDefinition(Identifiers.BaseDataVariableType)
                .build();

        var chain = node.getFilterChain();

        chain.addLast(
                AttributeFilters.getValue(
                        ctx -> extractor.get()
                )
        );

        if (injector != null) {
            chain.addLast(AttributeFilters.setValue((ctx, value) -> {
                injector.accept(value);
            }));
        }

        getNodeManager().addNode(node);
        folder.addOrganizes(node);
    }

    private UaFolderNode createBaseFolder() {

        NodeId folderNodeId = newNodeId("OMP.Simulation");
        UaFolderNode folderNode = new UaFolderNode(
                getNodeContext(),
                folderNodeId,
                newQualifiedName("OMP Simulation"),
                LocalizedText.english("OMP Simulation")
        );
        getNodeManager().addNode(folderNode);

        var index = getServer().getNamespaceTable().getIndex(TestNamespace.NAMESPACE_URI);
        NodeId parentNodeId = new NodeId(index, "OMP");
        folderNode.addReference(new Reference(
                folderNode.getNodeId(),
                Identifiers.Organizes,
                parentNodeId.expanded(),
                false
        ));

        return folderNode;
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
