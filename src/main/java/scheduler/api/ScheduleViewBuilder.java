package scheduler.api;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import scheduler.engine.AssignmentFilters;
import scheduler.store.PartDefinition;
import scheduler.engine.ScheduleMetrics;
import scheduler.model.Assignment;
import scheduler.model.MachineGroup;
import scheduler.model.Order;
import scheduler.model.Part;
import scheduler.model.WorkWindow;
import scheduler.store.ScheduleStore;
import scheduler.time.CurrentTimeProvider;

public final class ScheduleViewBuilder {
    private ScheduleViewBuilder() {}

    public static ScheduleView build(ScheduleStore store, CurrentTimeProvider time) {
        List<MachineGroupView> groups = store.machineGroups().values().stream()
                .sorted(Comparator.comparing(MachineGroup::groupId))
                .map(ScheduleViewBuilder::toGroupView)
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
            Instant orderReady = ScheduleMetrics.readyAt(order.orderId(), orderAssignments);
            List<PartScheduleView> parts = new ArrayList<>();
            for (Part part : order.parts()) {
                List<Assignment> partAssignments = orderAssignments.stream()
                        .filter(a -> a.partId().equals(part.partId()))
                        .sorted(Comparator.comparing(Assignment::plannedStart))
                        .toList();
                if (partAssignments.isEmpty()) {
                    continue;
                }
                Instant partReady = ScheduleMetrics.partReadyAt(
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

        ClockView clock = new ClockView(time.isSimulation(), time.now());
        List<PartCatalogView> catalog = store.partDefinitions().entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().priority(), a.getValue().priority()))
                .map(e -> toCatalogView(e.getKey(), e.getValue()))
                .toList();
        return new ScheduleView(store.factoryStartedAt(), clock, time.now(), catalog, groups, machines, orders);
    }

    private static MachineGroupView toGroupView(MachineGroup group) {
        List<WorkWindowView> windows = group.workWindows().stream()
                .sorted(Comparator.comparing(WorkWindow::dayOfWeek).thenComparing(WorkWindow::start))
                .map(w -> new WorkWindowView(
                        dayLabel(w.dayOfWeek()),
                        w.dayOfWeek().name(),
                        w.start().toString(),
                        w.end().toString()))
                .toList();
        return new MachineGroupView(
                group.groupId(), group.name(), windows, group.setupDuration().toString());
    }

    private static String dayLabel(java.time.DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "Пн";
            case TUESDAY -> "Вт";
            case WEDNESDAY -> "Ср";
            case THURSDAY -> "Чт";
            case FRIDAY -> "Пт";
            case SATURDAY -> "Сб";
            case SUNDAY -> "Вс";
        };
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
}
