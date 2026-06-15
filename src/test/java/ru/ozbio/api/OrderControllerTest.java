package ru.ozbio.api;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.ozbio.api.dto.OrderDetailResponse;
import ru.ozbio.api.dto.OrderResponse;
import ru.ozbio.api.dto.OrderToolResponse;
import ru.ozbio.api.handler.ApiExceptionHandler;
import ru.ozbio.domain.OrderStatus;
import ru.ozbio.service.OrderService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    OrderService orderService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new OrderController(orderService))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
    }

    @Test
    void createOrder_acceptsValidRequest() throws Exception {
        when(orderService.create(any()))
                .thenReturn(
                        new OrderResponse(
                                1L,
                                OrderStatus.CREATED,
                                Instant.parse("2026-01-01T12:00:00Z"),
                                List.of(new OrderDetailResponse(1L, "Body", 2)),
                                List.of(new OrderToolResponse(1L, "Drill", 1))));

        mockMvc.perform(
                        post("/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "details":[{"detailId":1,"count":2}],
                                          "tools":[{"toolId":1,"count":1}]
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.details[0].count").value(2));
    }

    @Test
    void listOrders_returnsOrders() throws Exception {
        when(orderService.list())
                .thenReturn(
                        List.of(
                                new OrderResponse(
                                        1L,
                                        OrderStatus.CREATED,
                                        Instant.parse("2026-01-01T12:00:00Z"),
                                        List.of(new OrderDetailResponse(1L, "Body", 2)),
                                        List.of())));

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].status").value("CREATED"));
    }
}
