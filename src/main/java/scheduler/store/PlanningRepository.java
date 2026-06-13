package scheduler.store;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import scheduler.model.machine.Machine;
import scheduler.model.machine.MachineGroup;
import scheduler.model.machine.Capability;
import scheduler.model.order.Order;
import scheduler.model.order.Task;
import scheduler.model.schedule.Assignment;

/** Точечные чтения и записи для планирования заказов. */
public interface PlanningRepository {

    Instant factoryStartedAt() throws IOException;

    boolean partExists(String partId) throws IOException;

    int partPriority(String partId) throws IOException;

    List<Task> partTasks(String partId) throws IOException;

    boolean hasOperationalMachineForCapability(Capability capability) throws IOException;

    List<Machine> findOperationalMachines(Capability capability) throws IOException;

    Machine findMachine(String machineId) throws IOException;

    Optional<MachineGroup> groupForMachine(String machineId) throws IOException;

    boolean orderExists(String orderId) throws IOException;

    List<String> listOrderIds() throws IOException;

    List<Order> ordersWithPriorityAbove(int priority) throws IOException;

    List<Assignment> assignmentsForOrder(String orderId) throws IOException;

    Optional<Instant> orderReadyAt(String orderId) throws IOException;

    Instant machineAvailableFrom(String machineId, Instant now) throws IOException;

    Optional<Assignment> lastWorkOnMachine(String machineId) throws IOException;

    void syncOperationalMachines(Instant now) throws IOException;

    void insertOrder(Order order) throws IOException;

    void insertAssignment(Assignment assignment) throws IOException;

    void updateMachineAvailableAt(String machineId, Instant availableAt) throws IOException;
}
