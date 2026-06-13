package scheduler.api.view;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import scheduler.engine.metrics.AssignmentFilters;
import scheduler.store.core.PartDefinition;
import scheduler.engine.metrics.OrderProgress;
import scheduler.model.schedule.Assignment;
import scheduler.model.machine.MachineGroup;
import scheduler.model.order.Order;
import scheduler.model.order.Part;
import scheduler.store.core.ScheduleStore;
import scheduler.time.CurrentTimeProvider;

public final class ScheduleViewBuilder {
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Europe/Moscow"));

    private ScheduleViewBuilder() {}

    public static ScheduleView build(ScheduleStore store, CurrentTimeProvider time) {
        List<MachineGroupView> groups = store.machineGroups().values().stream()
                .sorted(Comparator.comparing(MachineGroup::groupId))
                .map(g -> new MachineGroupView(g.groupId(), g.name(), g.setupDuration().toString()))
                .toList();

        List<MachineView> machines = store.machines().stream()
                .map(m -> {
                    MachineGroup group = store.findGroupForMachine(m).orElse(null);
                    String groupName = group != null ? group.name() : m.groupId();
                    return new MachineView(
                            m.machineId(),
                            m.groupId(),
                            groupName,
                            m.status(),
                            m.availableAt(),
                            m.capabilities());
                })
                .toList();

        List<OrderScheduleView> orders = new ArrayList<>();
        for (Order order : store.orders()) {
            List<Assignment> orderAssignments = AssignmentFilters.active(store.assignments()).stream()
                    .filter(a -> a.orderId().equals(order.orderId()))
                    .toList();
            if (orderAssignments.isEmpty()) {
                continue;
            }
            Instant orderReady = OrderProgress.readyAt(order.orderId(), orderAssignments);
            List<PartScheduleView> parts = new ArrayList<>();
            for (Part part : order.parts()) {
                List<Assignment> partAssignments = orderAssignments.stream()
                        .filter(a -> a.partId().equals(part.partId()))
                        .sorted(Comparator.comparing(Assignment::plannedStart))
                        .toList();
                if (partAssignments.isEmpty()) {
                    continue;
                }
                Instant partReady = OrderProgress.partReadyAt(
                        order.orderId(), part.partId(), orderAssignments);
                long slackMinutes = Duration.between(partReady, orderReady).toMinutes();
                parts.add(new PartScheduleView(
                        part.partId(),
                        part.quantity(),
                        store.partPriority(part.partId()),
                        partReady,
                        slackMinutes,
                        partAssignments.stream().map(ScheduleViewBuilder::toAssignmentView).toList()));
            }
            orders.add(new OrderScheduleView(
                    order.orderId(), order.createdAt(), order.priority(), orderReady, parts));
        }

        List<PartCatalogView> catalog = store.partDefinitions().entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().priority(), a.getValue().priority()))
                .map(e -> toCatalogView(e.getKey(), e.getValue()))
                .toList();
        Instant now = time.now();
        return new ScheduleView(
                store.factoryStartedAt(),
                new ClockView(false, now),
                now,
                catalog,
                groups,
                machines,
                orders);
    }

    private static PartCatalogView toCatalogView(String partId, PartDefinition def) {
        List<TaskTemplateView> tasks = def.tasks().stream()
                .map(t -> new TaskTemplateView(
                        t.taskId(),
                        t.sequence(),
                        t.duration().toString(),
                        t.requiredCapability().name()))
                .toList();
        return new PartCatalogView(partId, def.priority(), tasks);
    }

    private static AssignmentView toAssignmentView(Assignment a) {
        return new AssignmentView(
                a.assignmentId(),
                a.orderId(),
                a.partId(),
                a.unitIndex(),
                a.taskId(),
                a.machineId(),
                a.sequence(),
                a.plannedStart(),
                a.plannedEnd(),
                a.status().name(),
                a.actualStart(),
                a.actualEnd());
    }

    static String formatInstant(Instant instant) {
        return instant == null ? "" : FMT.format(instant);
    }
}
