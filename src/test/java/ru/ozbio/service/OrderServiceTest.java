package ru.ozbio.service;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ozbio.api.dto.CreateOrderRequest;
import ru.ozbio.api.dto.OrderDetailRequest;
import ru.ozbio.api.dto.OrderToolRequest;
import ru.ozbio.domain.OrderStatus;
import ru.ozbio.persistence.OrderRepository;
import ru.ozbio.service.exception.InvalidReferenceException;
import ru.ozbio.service.model.CreateOrderCommand;
import ru.ozbio.service.model.OrderDetailLine;
import ru.ozbio.service.model.OrderSummary;
import ru.ozbio.service.model.OrderToolLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    OrderRepository orderRepository;

    @InjectMocks
    OrderService orderService;

    @Test
    void create_persistsOrderWithLines() {
        when(orderRepository.detailExists(1L)).thenReturn(true);
        when(orderRepository.toolExists(2L)).thenReturn(true);
        when(orderRepository.insert(any(CreateOrderCommand.class)))
                .thenReturn(
                        new OrderSummary(10L, OrderStatus.CREATED, Instant.parse("2026-01-01T12:00:00Z")));
        when(orderRepository.findDetailsByOrderId(10L))
                .thenReturn(List.of(new OrderDetailLine(1L, "Body", 3)));
        when(orderRepository.findToolsByOrderId(10L))
                .thenReturn(List.of(new OrderToolLine(2L, "Drill", 1)));

        var response =
                orderService.create(
                        new CreateOrderRequest(
                                List.of(new OrderDetailRequest(1L, 3)),
                                List.of(new OrderToolRequest(2L, 1))));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(response.details()).hasSize(1);
        assertThat(response.tools()).hasSize(1);
        verify(orderRepository).insert(any(CreateOrderCommand.class));
    }

    @Test
    void create_rejectsEmptyOrder() {
        assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(List.of(), List.of())))
                .isInstanceOf(IllegalArgumentException.class);

        verify(orderRepository, never()).insert(any());
    }

    @Test
    void create_rejectsUnknownDetail() {
        when(orderRepository.detailExists(99L)).thenReturn(false);

        assertThatThrownBy(
                        () ->
                                orderService.create(
                                        new CreateOrderRequest(
                                                List.of(new OrderDetailRequest(99L, 1)), List.of())))
                .isInstanceOf(InvalidReferenceException.class);

        verify(orderRepository, never()).insert(any());
    }
}
