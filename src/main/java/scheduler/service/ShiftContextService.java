package scheduler.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import scheduler.api.DomainLabels;
import scheduler.api.ShiftContextView;
import scheduler.api.ShiftContextView.MachineShiftRowView;
import scheduler.api.ShiftContextView.OperationCountView;
import scheduler.api.ShiftContextView.ShiftCloseRowView;
import scheduler.api.ShiftContextView.ShiftInfoView;
import scheduler.engine.FactoryZone;
import scheduler.engine.ShiftCalendar;
import scheduler.engine.ShiftCalendar.ShiftWindow;
import scheduler.model.Assignment;
import scheduler.model.Machine;
import scheduler.model.MachineGroup;
import scheduler.store.ScheduleStore;
import scheduler.time.CurrentTimeProvider;

public class ShiftContextService {
    private final ScheduleStore store;
    private final CurrentTimeProvider time;

    public ShiftContextService(ScheduleStore store, CurrentTimeProvider time) {
        this.store = store;
        this.time = time;
    }

    public ShiftContextView build() {
        Instant now = time.now();
        List<ShiftInfoView> pending = collectPendingShifts(now);
        pending.sort(Comparator.comparing(ShiftInfoView::shiftEnd));

        boolean stale = !pending.isEmpty();
        ShiftInfoView active;
        if (!pending.isEmpty()) {
            active = pending.getFirst();
        } else {
            active = findCurrentShift(now).orElse(null);
        }

        List<ShiftCloseRowView> closeRows = buildCloseRows(pending);
        return new ShiftContextView(stale, pending.size(), List.copyOf(pending), active, closeRows);
    }

    private List<ShiftCloseRowView> buildCloseRows(List<ShiftInfoView> pending) {
        List<ShiftCloseRowView> rows = new ArrayList<>();
        for (ShiftInfoView shift : pending) {
            for (MachineShiftRowView machine : shift.machines()) {
                for (OperationCountView op : machine.operations()) {
                    rows.add(new ShiftCloseRowView(
                            shift.groupId(),
                            shift.groupName(),
                            shift.shiftStart(),
                            shift.shiftEnd(),
                            machine.machineId(),
                            machine.machineTitle(),
                            op.taskId(),
                            op.taskTitle(),
                            op.plannedCount(),
                            op.defaultCompletedCount()));
                }
            }
        }
        return List.copyOf(rows);
    }

    private List<ShiftInfoView> collectPendingShifts(Instant now) {
        List<ShiftInfoView> all = new ArrayList<>();
        for (ShiftPendingShifts.Entry entry : ShiftPendingShifts.list(store, now)) {
            MachineGroup group = store.findMachineGroup(entry.groupId());
            all.add(toShiftInfo(group, entry.window(), true));
        }
        return all;
    }

    private Optional<ShiftInfoView> findCurrentShift(Instant now) {
        List<ShiftInfoView> ongoing = store.machineGroups().values().stream()
                .map(group -> ShiftCalendar.shiftWindowContaining(now, group, FactoryZone.ZONE)
                        .filter(w -> ShiftAssignments.isInsideOngoingShift(store, group.groupId(), now, w))
                        .map(w -> toShiftInfo(group, w, false)))
                .flatMap(Optional::stream)
                .toList();
        Optional<ShiftInfoView> withWork = ongoing.stream()
                .filter(s -> !s.machines().isEmpty())
                .min(Comparator.comparing(ShiftInfoView::shiftEnd));
        if (withWork.isPresent()) {
            return withWork;
        }
        return ongoing.stream().min(Comparator.comparing(ShiftInfoView::shiftEnd));
    }

    private ShiftInfoView toShiftInfo(MachineGroup group, ShiftWindow window, boolean overdue) {
        List<MachineShiftRowView> machines = buildMachineRows(group.groupId(), window.start(), window.end());
        return new ShiftInfoView(
                group.groupId(),
                group.name(),
                window.start(),
                window.end(),
                overdue,
                machines);
    }

    private List<MachineShiftRowView> buildMachineRows(String groupId, Instant shiftStart, Instant shiftEnd) {
        Map<String, List<OperationCountView>> opsByMachine = new LinkedHashMap<>();
        Map<ShiftAssignments.AggregateKey, List<Assignment>> aggregates =
                ShiftAssignments.aggregateByMachineAndTask(store, groupId, shiftStart, shiftEnd);
        for (var entry : aggregates.entrySet()) {
            ShiftAssignments.AggregateKey key = entry.getKey();
            int planned = entry.getValue().size();
            opsByMachine
                    .computeIfAbsent(key.machineId(), id -> new ArrayList<>())
                    .add(new OperationCountView(
                            key.taskId(),
                            DomainLabels.taskTitle(key.taskId()),
                            planned,
                            planned));
        }

        return opsByMachine.entrySet().stream()
                .map(e -> new MachineShiftRowView(
                        e.getKey(),
                        DomainLabels.machineTitle(e.getKey()),
                        List.copyOf(e.getValue())))
                .sorted(Comparator.comparing(MachineShiftRowView::machineId))
                .toList();
    }
}
