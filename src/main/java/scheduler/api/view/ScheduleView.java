package scheduler.api.view;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import scheduler.model.machine.Capability;
import scheduler.model.machine.MachineStatus;

public record ScheduleView(
        Instant factoryStartedAt,
        ClockView clock,
        Instant generatedAt,
        List<PartCatalogView> partCatalog,
        List<MachineGroupView> machineGroups,
        List<MachineView> machines,
        List<OrderScheduleView> orders) {}

record ClockView(boolean simulationEnabled, Instant currentTime) {}

record PartCatalogView(String partId, int priority, List<TaskTemplateView> tasks) {}

record TaskTemplateView(String taskId, int sequence, String duration, String capability) {}

record MachineGroupView(String groupId, String name, String setupDuration) {}

record MachineView(
        String machineId,
        String groupId,
        String groupName,
        MachineStatus status,
        Instant availableAt,
        Set<Capability> capabilities) {}

record OrderScheduleView(
        String orderId, Instant createdAt, int priority, Instant readyAt, List<PartScheduleView> parts) {}

record PartScheduleView(
        String partId,
        int quantity,
        int priority,
        Instant partReadyAt,
        long slackMinutes,
        List<AssignmentView> assignments) {}

record AssignmentView(
        String assignmentId,
        String orderId,
        String partId,
        int unitIndex,
        String taskId,
        String machineId,
        int sequence,
        Instant plannedStart,
        Instant plannedEnd,
        String status,
        Instant actualStart,
        Instant actualEnd) {}
