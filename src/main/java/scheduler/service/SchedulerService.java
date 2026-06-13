package scheduler.service;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import scheduler.api.MachineGroupUpdateRequest;
import scheduler.engine.FactoryZone;
import scheduler.engine.GreedyScheduler;
import scheduler.engine.OrderIds;
import scheduler.engine.OrderPriorities;
import scheduler.engine.MachineStateSync;
import scheduler.engine.ScheduleMetrics;
import scheduler.model.Assignment;
import scheduler.model.MachineGroup;
import scheduler.model.MachineStatus;
import scheduler.api.OrderRequest;
import scheduler.model.Order;
import scheduler.model.Part;
import scheduler.model.SetupIntervals;
import scheduler.model.Task;
import scheduler.model.WorkWindow;
import scheduler.store.JsonScheduleRepository;
import scheduler.store.ScheduleStore;
import scheduler.time.CurrentTimeProvider;

public class SchedulerService {
    private final ScheduleStore store;
    private final JsonScheduleRepository repository;
    private final CurrentTimeProvider time;
    private final GreedyScheduler scheduler;
    private final ShiftCloseService shiftCloseService;
    private final ShiftContextService shiftContextService;
    private final ShiftAutoCloseService shiftAutoCloseService;

    public SchedulerService(
            ScheduleStore store, JsonScheduleRepository repository, CurrentTimeProvider time) {
        this.store = store;
        this.repository = repository;
        this.time = time;
        this.scheduler = new GreedyScheduler(time);
        this.shiftCloseService = new ShiftCloseService(store, repository, time);
        this.shiftContextService = new ShiftContextService(store, time);
        this.shiftAutoCloseService = new ShiftAutoCloseService(store, repository, time);
        MachineStateSync.sync(store, time.now());
    }

    public ScheduleStore store() {
        return store;
    }

    public CurrentTimeProvider time() {
        return time;
    }

    public AddOrderResult addOrder(OrderRequest request) throws IOException {
        OrderValidator.validatePartIds(request, store);
        var parts = request.parts().stream()
                .map(line -> store.createPart(line.partId(), line.resolvedQuantity()))
                .toList();
        Instant createdAt = time.now();
        String orderId = resolveOrderId(request.orderId(), createdAt);
        Order order = new Order(orderId, createdAt, parts, OrderPriorities.fromCreatedAt(createdAt));
        OrderValidator.validate(order, store);
        store.addOrder(order);
        scheduler.scheduleOrder(order, store);
        verifyOrderFullyScheduled(order);
        persist();

        List<Assignment> forOrder = store.assignments().stream()
                .filter(a -> a.orderId().equals(order.orderId()))
                .toList();
        return new AddOrderResult(
                order.orderId(), ScheduleMetrics.readyAt(order.orderId(), forOrder), forOrder);
    }

    public void setSimulationTime(Instant newTime) throws IOException {
        if (!store.simulationClockEnabled()) {
            throw new SchedulingException(
                    "Simulation clock is disabled. Set simulationClock.enabled=true in data/schedule.json "
                            + "or use SystemCurrentTimeProvider.");
        }
        Instant current = time.now();
        if (newTime.isBefore(current)) {
            throw new SchedulingException(
                    "Simulation time can only move forward (current: "
                            + current
                            + ", requested: "
                            + newTime
                            + ")");
        }
        store.setSimulationCurrentTime(newTime);
        MachineStateSync.sync(store, newTime);
        shiftAutoCloseService.closeEmptyPendingShifts();
        persist();
    }

    public scheduler.api.ShiftContextView shiftContext() throws IOException {
        shiftAutoCloseService.closeEmptyPendingShifts();
        return shiftContextService.build();
    }

    public ShiftCloseResult closeShift(scheduler.api.ShiftCloseRequest request) throws IOException {
        return shiftCloseService.closeShift(request);
    }

    public void setMachineStatus(String machineId, MachineStatus status) throws IOException {
        var machine = store.findMachine(machineId);
        machine.setStatus(status);
        MachineStateSync.sync(store, time.now());
        persist();
    }

    public void updateMachineGroup(String groupId, MachineGroupUpdateRequest request) throws IOException {
        MachineGroup existing = store.findMachineGroup(groupId);
        List<WorkWindow> windows = existing.workWindows();
        if (request.workWindows() != null) {
            windows = request.workWindows().stream()
                    .map(w -> new WorkWindow(
                            DayOfWeek.valueOf(w.dayOfWeek()),
                            LocalTime.parse(w.start()),
                            LocalTime.parse(w.end())))
                    .toList();
        }
        Duration setup = request.setupMinutes() != null
                ? request.setupDuration()
                : existing.setupDuration();
        store.setMachineGroup(new MachineGroup(groupId, existing.name(), windows, setup));
        persist();
    }

    private void verifyOrderFullyScheduled(Order order) {
        for (Part part : order.parts()) {
            if (!ScheduleMetrics.isPartFullyScheduled(order.orderId(), part, store.assignments())) {
                throw new SchedulingException(
                        "Incomplete schedule for part " + part.partId() + " in order " + order.orderId());
            }
            for (Task task : part.tasks()) {
                if (SetupIntervals.isSetup(task.taskId())) {
                    continue;
                }
                int scheduled = ScheduleMetrics.unitsScheduledForTask(
                        order.orderId(), part.partId(), task.taskId(), store.assignments());
                if (scheduled < part.quantity()) {
                    throw new SchedulingException(
                            "Task "
                                    + task.taskId()
                                    + " scheduled for "
                                    + scheduled
                                    + " of "
                                    + part.quantity()
                                    + " units");
                }
            }
        }
    }

    private String resolveOrderId(String requestedId, Instant createdAt) {
        if (requestedId != null && !requestedId.isBlank()) {
            return requestedId.trim();
        }
        List<String> existing =
                store.orders().stream().map(Order::orderId).toList();
        return OrderIds.nextOrderId(createdAt, FactoryZone.ZONE, existing);
    }

    private void persist() throws IOException {
        repository.save(store);
    }
}
