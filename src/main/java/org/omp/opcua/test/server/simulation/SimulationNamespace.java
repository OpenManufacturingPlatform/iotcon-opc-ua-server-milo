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

public class SimulationNamespace extends ManagedNamespaceWithLifecycle {
    public static final String NAMESPACE_URI = "urn:omp:milo:simulation-namespace";
    private final SimulationConfiguration configuration;
    private final SubscriptionModel subscriptionModel;
    private final List<Runnable> tasks = new ArrayList<>();
    private final ScheduledExecutorService executor;

    public SimulationNamespace(final OpcUaServer server, final SimulationConfiguration configuration) {
        super(server, NAMESPACE_URI);
        this.configuration = configuration;

        this.subscriptionModel = new SubscriptionModel(server, this);
        this.executor = Executors.newScheduledThreadPool(1);

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

        var folder = createFolder(base.getNodeId(), "OMP/Simulation/" + name, name, name);
        var simulation = createFolder(folder.getNodeId(), "OMP/Simulation/" + name + "/Simulation Properties", "Simulation Properties", "Simulation Properties");
        var physical = createFolder(folder.getNodeId(), "OMP/Simulation/" + name + "/Physical Properties", "Physical Properties", "Physical Properties");
        var control = createFolder(folder.getNodeId(), "OMP/Simulation/" + name + "/Control", "Control", "Control");

        registerVariable(simulation, name, "ambientTemperatureSetpoint", "Ambient Temperature Setpoint", Identifiers.Double, device::getAmbientTemperatureSetpoint, device::setAmbientTemperatureSetpoint);

        registerVariable(physical, name, "temperature", "Temperature", Identifiers.Double, device::getTemperature, null);
        registerVariable(physical, name, "ambientTemperature", "Ambient Temperature", Identifiers.Double, device::getAmbientTemperature, null);
        registerVariable(physical, name, "powerConsumption", "Power Consumption", Identifiers.Double, device::getPowerConsumption, null);
        registerVariable(control, name, "active", "Active", Identifiers.Boolean, device::isActive, device::setActive);

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
        var index = getServer().getNamespaceTable().getIndex(TestNamespace.NAMESPACE_URI);
        if (index == null) {
            throw new RuntimeException("Missing namespace: " + TestNamespace.NAMESPACE_URI);
        }
        NodeId parentNodeId = new NodeId(index, "OMP");

        return createFolder(parentNodeId, "OMP/Simulation", "Simulation", "Simulation");
    }

    private UaFolderNode createFolder(
            NodeId parentNodeId,
            String nodeId,
            String name,
            String label
    ) {
        NodeId folderNodeId = newNodeId(nodeId);
        UaFolderNode folderNode = new UaFolderNode(
                getNodeContext(),
                folderNodeId,
                newQualifiedName(name),
                LocalizedText.english(label)
        );
        getNodeManager().addNode(folderNode);

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
