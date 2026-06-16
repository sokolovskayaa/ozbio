package ru.ozbio.api;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.ozbio.api.dto.ScheduleItemResponse;
import ru.ozbio.api.handler.ApiExceptionHandler;
import ru.ozbio.service.ScheduleService;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ScheduleControllerTest {

    @Mock
    ScheduleService scheduleService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new ScheduleController(scheduleService))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
    }

    @Test
    void current_returnsSchedule() throws Exception {
        Instant start = Instant.parse("2026-01-01T10:00:00Z");
        Instant end = Instant.parse("2026-01-01T11:00:00Z");
        when(scheduleService.current())
                .thenReturn(
                        List.of(new ScheduleItemResponse(1L, 10L, 5L, 3, start, end)));

        mockMvc.perform(get("/schedule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(1))
                .andExpect(jsonPath("$[0].operationId").value(10))
                .andExpect(jsonPath("$[0].machineId").value(5))
                .andExpect(jsonPath("$[0].count").value(3))
                .andExpect(jsonPath("$[0].start").exists())
                .andExpect(jsonPath("$[0].end").exists());
    }
}
