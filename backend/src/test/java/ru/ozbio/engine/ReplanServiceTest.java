package ru.ozbio.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ozbio.domain.OrderStatus;
import ru.ozbio.engine.exception.NoMachineAvailableException;
import ru.ozbio.engine.model.PlannedScheduleItem;
import ru.ozbio.engine.model.PlanningBatch;
import ru.ozbio.persistence.DetailRepository;
import ru.ozbio.persistence.MachineRepository;
import ru.ozbio.persistence.OrderRepository;
import ru.ozbio.persistence.ToolRepository;
import ru.ozbio.service.model.MachineSummary;
import ru.ozbio.service.model.OperationLine;
import ru.ozbio.service.model.OrderDetailLine;
import ru.ozbio.service.model.OrderSummary;
import ru.ozbio.service.model.OrderToolLine;
import ru.ozbio.service.model.ToolDetailLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplanServiceTest {

    private static final Instant PLANNING_START = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    OrderRepository orderRepository;

    @Mock
    ToolRepository toolRepository;

    @Mock
    DetailRepository detailRepository;

    @Mock
    MachineRepository machineRepository;

    @InjectMocks
    ReplanService replanService;

    @Test
    void calculateOperationsForPlanning_buildsBatchesPerDetailRoute() {
        when(orderRepository.findAllForPlanning())
                .thenReturn(
                        List.of(
                                new OrderSummary(
                                        1L, OrderStatus.CREATED, Instant.parse("2026-01-01T10:00:00Z"))));
        when(orderRepository.findDetailsByOrderIds(List.of(1L)))
                .thenReturn(Map.of(1L, List.of(new OrderDetailLine(100L, "Body", 10))));
        when(orderRepository.findToolsByOrderIds(List.of(1L))).thenReturn(Map.of());
        when(detailRepository.findOperationsByDetailIds(any()))
                .thenReturn(
                        Map.of(
                                100L,
                                List.of(
                                        new OperationLine(1L, 1, Duration.ofMinutes(30), Duration.ZERO, 5L),
                                        new OperationLine(2L, 2, Duration.ofMinutes(20), Duration.ZERO, 5L),
                                        new OperationLine(3L, 3, Duration.ofMinutes(10), Duration.ZERO, 6L))));

        List<PlanningBatch> batches = replanService.calculateOperationsForPlanning();

        assertThat(batches)
                .containsExactly(
                        new PlanningBatch(1L, 1L, 10, null),
                        new PlanningBatch(1L, 2L, 10, 1L),
                        new PlanningBatch(1L, 3L, 10, 2L));
    }

    @Test
    void calculateOperationsForPlanning_expandsToolsIntoDetailDemand() {
        when(orderRepository.findAllForPlanning())
                .thenReturn(
                        List.of(
                                new OrderSummary(
                                        1L, OrderStatus.CREATED, Instant.parse("2026-01-01T10:00:00Z"))));
        when(orderRepository.findDetailsByOrderIds(List.of(1L))).thenReturn(Map.of());
        when(orderRepository.findToolsByOrderIds(List.of(1L)))
                .thenReturn(Map.of(1L, List.of(new OrderToolLine(50L, "Drill", 2))));
        when(toolRepository.findDetailsByToolIds(any()))
                .thenReturn(Map.of(50L, List.of(new ToolDetailLine(100L, "Body", 3))));
        when(detailRepository.findOperationsByDetailIds(any()))
                .thenReturn(
                        Map.of(
                                100L,
                                List.of(new OperationLine(7L, 1, Duration.ofMinutes(15), Duration.ZERO, 5L))));

        List<PlanningBatch> batches = replanService.calculateOperationsForPlanning();

        assertThat(batches).containsExactly(new PlanningBatch(1L, 7L, 6, null));
    }

    @Test
    void calculateOperationsForPlanning_preservesOrderPriorityAcrossOrders() {
        when(orderRepository.findAllForPlanning())
                .thenReturn(
                        List.of(
                                new OrderSummary(
                                        1L, OrderStatus.CREATED, Instant.parse("2026-01-01T10:00:00Z")),
                                new OrderSummary(
                                        2L, OrderStatus.PLANNED, Instant.parse("2026-01-02T10:00:00Z"))));
        when(orderRepository.findDetailsByOrderIds(List.of(1L, 2L)))
                .thenReturn(
                        Map.of(
                                1L, List.of(new OrderDetailLine(100L, "A", 5)),
                                2L, List.of(new OrderDetailLine(200L, "B", 3))));
        when(orderRepository.findToolsByOrderIds(List.of(1L, 2L))).thenReturn(Map.of());
        when(detailRepository.findOperationsByDetailIds(any()))
                .thenReturn(
                        Map.of(
                                100L,
                                List.of(new OperationLine(1L, 1, Duration.ofMinutes(10), Duration.ZERO, 5L)),
                                200L,
                                List.of(new OperationLine(2L, 1, Duration.ofMinutes(10), Duration.ZERO, 6L))));

        List<PlanningBatch> batches = replanService.calculateOperationsForPlanning();

        assertThat(batches)
                .containsExactly(new PlanningBatch(1L, 1L, 5, null), new PlanningBatch(2L, 2L, 3, null));
    }

    @Test
    void replan_respectsPrecedenceBetweenOperations() {
        stubSingleOrderTwoStepRoute();

        when(detailRepository.findOperationsByIds(Set.of(1L, 2L)))
                .thenReturn(
                        Map.of(
                                1L,
                                new OperationLine(1L, 1, Duration.ofMinutes(30), Duration.ZERO, 5L),
                                2L,
                                new OperationLine(
                                        2L, 2, Duration.ofMinutes(10), Duration.ofMinutes(5), 5L)));
        when(machineRepository.findMachinesByMachineTypeIds(Set.of(5L)))
                .thenReturn(Map.of(5L, List.of(new MachineSummary(10L, 5L, "Lathe"))));

        List<PlannedScheduleItem> schedule = replanService.replan(PLANNING_START);

        Instant op1End = PLANNING_START.plus(Duration.ofMinutes(60));
        assertThat(schedule)
                .containsExactly(
                        new PlannedScheduleItem(1L, 1L, 10L, 2, PLANNING_START, op1End),
                        new PlannedScheduleItem(
                                1L,
                                2L,
                                10L,
                                2,
                                op1End,
                                op1End.plus(Duration.ofMinutes(25))));
    }

    @Test
    void replan_picksEarliestFreeMachine() {
        when(orderRepository.findAllForPlanning())
                .thenReturn(
                        List.of(
                                new OrderSummary(
                                        1L, OrderStatus.CREATED, Instant.parse("2026-01-01T10:00:00Z"))));
        when(orderRepository.findDetailsByOrderIds(List.of(1L)))
                .thenReturn(
                        Map.of(
                                1L,
                                List.of(
                                        new OrderDetailLine(100L, "A", 1),
                                        new OrderDetailLine(200L, "B", 1))));
        when(orderRepository.findToolsByOrderIds(List.of(1L))).thenReturn(Map.of());
        when(detailRepository.findOperationsByDetailIds(any()))
                .thenReturn(
                        Map.of(
                                100L,
                                List.of(new OperationLine(1L, 1, Duration.ofMinutes(10), Duration.ZERO, 5L)),
                                200L,
                                List.of(new OperationLine(2L, 1, Duration.ofMinutes(10), Duration.ZERO, 5L))));
        when(detailRepository.findOperationsByIds(Set.of(1L, 2L)))
                .thenReturn(
                        Map.of(
                                1L,
                                new OperationLine(1L, 1, Duration.ofMinutes(10), Duration.ZERO, 5L),
                                2L,
                                new OperationLine(2L, 1, Duration.ofMinutes(10), Duration.ZERO, 5L)));
        when(machineRepository.findMachinesByMachineTypeIds(Set.of(5L)))
                .thenReturn(
                        Map.of(
                                5L,
                                List.of(
                                        new MachineSummary(10L, 5L, "Lathe A"),
                                        new MachineSummary(11L, 5L, "Lathe B"))));

        List<PlannedScheduleItem> schedule = replanService.replan(PLANNING_START);

        assertThat(schedule.get(0).machineId()).isEqualTo(10L);
        assertThat(schedule.get(1).machineId()).isEqualTo(11L);
        assertThat(schedule.get(1).start()).isEqualTo(PLANNING_START);
    }

    @Test
    void replan_throwsWhenNoMachineAvailable() {
        stubSingleOrderTwoStepRoute();
        when(detailRepository.findOperationsByIds(Set.of(1L, 2L)))
                .thenReturn(
                        Map.of(
                                1L,
                                new OperationLine(1L, 1, Duration.ofMinutes(30), Duration.ZERO, 5L),
                                2L,
                                new OperationLine(2L, 2, Duration.ofMinutes(10), Duration.ZERO, 5L)));
        when(machineRepository.findMachinesByMachineTypeIds(Set.of(5L))).thenReturn(Map.of());

        assertThatThrownBy(() -> replanService.replan(PLANNING_START))
                .isInstanceOf(NoMachineAvailableException.class)
                .hasMessageContaining("machine type 5");
    }

    private void stubSingleOrderTwoStepRoute() {
        when(orderRepository.findAllForPlanning())
                .thenReturn(
                        List.of(
                                new OrderSummary(
                                        1L, OrderStatus.CREATED, Instant.parse("2026-01-01T10:00:00Z"))));
        when(orderRepository.findDetailsByOrderIds(List.of(1L)))
                .thenReturn(Map.of(1L, List.of(new OrderDetailLine(100L, "Body", 2))));
        when(orderRepository.findToolsByOrderIds(List.of(1L))).thenReturn(Map.of());
        when(detailRepository.findOperationsByDetailIds(any()))
                .thenReturn(
                        Map.of(
                                100L,
                                List.of(
                                        new OperationLine(1L, 1, Duration.ofMinutes(30), Duration.ZERO, 5L),
                                        new OperationLine(2L, 2, Duration.ofMinutes(10), Duration.ZERO, 5L))));
    }
}
