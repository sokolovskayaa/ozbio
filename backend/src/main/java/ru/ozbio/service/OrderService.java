package ru.ozbio.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ozbio.api.dto.CreateOrderRequest;
import ru.ozbio.api.dto.OrderDetailResponse;
import ru.ozbio.api.dto.OrderResponse;
import ru.ozbio.api.dto.OrderToolResponse;
import ru.ozbio.persistence.OrderRepository;
import ru.ozbio.service.exception.InvalidReferenceException;
import ru.ozbio.service.model.OrderDetailLine;
import ru.ozbio.service.model.OrderSummary;
import ru.ozbio.service.model.OrderToolLine;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        validateRequest(request);

        OrderSummary order = orderRepository.insert(request.toCommand());
        return toResponse(
                order,
                orderRepository.findDetailsByOrderId(order.id()),
                orderRepository.findToolsByOrderId(order.id()));
    }

    public List<OrderResponse> list() {
        List<OrderSummary> orders = orderRepository.findAll();
        var detailsByOrderId =
                orderRepository.findDetailsByOrderIds(orders.stream().map(OrderSummary::id).toList());
        var toolsByOrderId =
                orderRepository.findToolsByOrderIds(orders.stream().map(OrderSummary::id).toList());

        return orders.stream()
                .map(
                        order ->
                                toResponse(
                                        order,
                                        detailsByOrderId.getOrDefault(order.id(), List.of()),
                                        toolsByOrderId.getOrDefault(order.id(), List.of())))
                .toList();
    }

    private void validateRequest(CreateOrderRequest request) {
        if (request.details().isEmpty() && request.tools().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one detail or tool");
        }

        Set<Long> detailIds = new HashSet<>();
        for (var detail : request.details()) {
            if (!detailIds.add(detail.detailId())) {
                throw new IllegalArgumentException("Duplicate detailId: " + detail.detailId());
            }
            if (!orderRepository.detailExists(detail.detailId())) {
                throw new InvalidReferenceException("detailId", detail.detailId());
            }
        }

        Set<Long> toolIds = new HashSet<>();
        for (var tool : request.tools()) {
            if (!toolIds.add(tool.toolId())) {
                throw new IllegalArgumentException("Duplicate toolId: " + tool.toolId());
            }
            if (!orderRepository.toolExists(tool.toolId())) {
                throw new InvalidReferenceException("toolId", tool.toolId());
            }
        }
    }

    private OrderResponse toResponse(
            OrderSummary order, List<OrderDetailLine> details, List<OrderToolLine> tools) {
        List<OrderDetailResponse> detailResponses =
                details.stream()
                        .map(d -> new OrderDetailResponse(d.detailId(), d.detailName(), d.count()))
                        .toList();
        List<OrderToolResponse> toolResponses =
                tools.stream()
                        .map(t -> new OrderToolResponse(t.toolId(), t.toolName(), t.count()))
                        .toList();
        return new OrderResponse(order.id(), order.status(), order.createdAt(), detailResponses, toolResponses);
    }
}
