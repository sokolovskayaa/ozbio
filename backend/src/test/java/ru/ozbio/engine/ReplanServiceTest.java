package ru.ozbio.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ozbio.domain.OrderStatus;
import ru.ozbio.engine.model.PlanningBatch;
import ru.ozbio.persistence.DetailRepository;
import ru.ozbio.persistence.OrderRepository;
import ru.ozbio.persistence.ToolRepository;
import ru.ozbio.service.model.OperationLine;
import ru.ozbio.service.model.OrderDetailLine;
import ru.ozbio.service.model.OrderSummary;
import ru.ozbio.service.model.OrderToolLine;
import ru.ozbio.service.model.ToolDetailLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplanServiceTest {

    @Mock
    OrderRepository orderRepository;

    @Mock
    ToolRepository toolRepository;

    @Mock
    DetailRepository detailRepository;

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
                        new PlanningBatch(1L, 10),
                        new PlanningBatch(2L, 10),
                        new PlanningBatch(3L, 10));
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

        assertThat(batches).containsExactly(new PlanningBatch(7L, 6));
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
                .containsExactly(new PlanningBatch(1L, 5), new PlanningBatch(2L, 3));
    }
}
