package scheduler.store.core;

import java.util.List;
import java.util.LinkedHashMap;
import scheduler.model.order.Order;
import scheduler.model.order.Part;
import scheduler.engine.policy.OrderPriorities;
import scheduler.store.json.ScheduleSnapshot;
import scheduler.store.snapshot.MachineGroupSnapshot;
import scheduler.store.snapshot.MachineSnapshot;
import scheduler.store.snapshot.PartDefinitionSnapshot;

/** Преобразование {@link ScheduleStore} ↔ {@link ScheduleSnapshot}. */
public final class ScheduleSnapshotMapper {
    private ScheduleSnapshotMapper() {}

    public static ScheduleStore fromSnapshot(ScheduleSnapshot snapshot) {
        ScheduleStore store = new ScheduleStore();
        store.setFactoryStartedAt(snapshot.factoryStartedAt);
        if (snapshot.machineGroups != null) {
            snapshot.machineGroups.forEach(g -> {
                if (g != null && g.groupId != null) {
                    store.putMachineGroup(g.groupId, g.toGroup());
                }
            });
        }
        if (store.machineGroupsEmpty()) {
            store.putAllMachineGroups(DemoFactoryCatalog.defaultMachineGroups());
        }
        if (snapshot.machines != null) {
            snapshot.machines.forEach(
                    m -> store.addMachine(m.toMachine(DemoFactoryCatalog.defaultGroupForMachine(m.machineId()))));
        }
        if (snapshot.orders != null) {
            snapshot.orders.stream().map(ScheduleSnapshotMapper::normalizeOrder).forEach(store::addOrderRaw);
            store.sortOrders();
        }
        if (snapshot.assignments != null) {
            snapshot.assignments.stream()
                    .map(AssignmentNormalization::normalize)
                    .forEach(store::addAssignment);
        }
        if (snapshot.partDefinitions != null) {
            snapshot.partDefinitions.forEach((id, def) -> {
                if (def != null && def.tasks != null) {
                    store.putPartDefinitionRaw(id, new PartDefinition(def.priority, def.tasks));
                }
            });
        }
        if (store.machinesEmpty()) {
            DemoFactoryCatalog.defaultMachines(store.factoryStartedAt()).forEach(store::addMachine);
        }
        return store;
    }

    public static ScheduleSnapshot toSnapshot(ScheduleStore store) {
        ScheduleSnapshot snapshot = new ScheduleSnapshot();
        snapshot.factoryStartedAt = store.factoryStartedAt();
        snapshot.machines = store.machines().stream().map(MachineSnapshot::from).toList();
        snapshot.machineGroups =
                store.machineGroups().values().stream().map(MachineGroupSnapshot::from).toList();
        snapshot.partDefinitions = new LinkedHashMap<>();
        store.partDefinitions().forEach((id, def) -> {
            PartDefinitionSnapshot dto = new PartDefinitionSnapshot();
            dto.priority = def.priority();
            dto.tasks = def.tasks();
            snapshot.partDefinitions.put(id, dto);
        });
        snapshot.orders = store.orders();
        snapshot.assignments = store.assignments();
        return snapshot;
    }

    private static Order normalizeOrder(Order order) {
        List<Part> parts = order.parts().stream()
                .map(p -> p.quantity() < 1 ? new Part(p.partId(), 1, p.tasks()) : p)
                .toList();
        int priority = OrderPriorities.resolve(order.createdAt(), order.priority());
        return new Order(order.orderId(), order.createdAt(), parts, priority);
    }
}
