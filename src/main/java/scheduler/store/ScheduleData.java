package scheduler.store;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import scheduler.model.machine.Machine;
import scheduler.model.machine.MachineGroup;
import scheduler.model.order.Order;
import scheduler.model.schedule.Assignment;
import scheduler.store.core.PartDefinition;

/** Снимок данных для построения {@link scheduler.api.view.ScheduleView} без {@code ScheduleStore}. */
public record ScheduleData(
        Instant factoryStartedAt,
        Map<String, PartDefinition> catalog,
        Map<String, MachineGroup> machineGroups,
        List<Machine> machines,
        List<Order> orders,
        List<Assignment> assignments) {}
