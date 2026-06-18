package ru.ozbio.api;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.ozbio.api.dto.MachineShiftClosableItemResponse;
import ru.ozbio.api.dto.MachineShiftClosableResponse;
import ru.ozbio.api.dto.MachineShiftClosableShiftTypeResponse;
import ru.ozbio.api.handler.ApiExceptionHandler;
import ru.ozbio.service.MachineShiftCloseService;
import ru.ozbio.service.MachineShiftQueryService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MachineShiftControllerTest {

    @Mock
    MachineShiftQueryService machineShiftQueryService;

    @Mock
    MachineShiftCloseService machineShiftCloseService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new MachineShiftController(
                                        machineShiftQueryService, machineShiftCloseService))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
    }

    @Test
    void listClosable_returnsItems() throws Exception {
        when(machineShiftQueryService.findClosable())
                .thenReturn(
                        new MachineShiftClosableResponse(
                                List.of(
                                        new MachineShiftClosableItemResponse(
                                                42L,
                                                3L,
                                                1L,
                                                LocalDate.of(2026, 5, 22),
                                                Instant.parse("2026-05-22T05:00:00Z"),
                                                Instant.parse("2026-05-22T13:00:00Z"),
                                                "EXPECTED",
                                                new MachineShiftClosableShiftTypeResponse(
                                                        5, LocalTime.of(8, 0), LocalTime.of(16, 0))))));

        mockMvc.perform(get("/machine-shifts/closable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(42))
                .andExpect(jsonPath("$.items[0].machineId").value(3))
                .andExpect(jsonPath("$.items[0].shiftTypeId").value(1))
                .andExpect(jsonPath("$.items[0].workDate[0]").value(2026))
                .andExpect(jsonPath("$.items[0].status").value("EXPECTED"))
                .andExpect(jsonPath("$.items[0].shiftType.dayOfWeek").value(5))
                .andExpect(jsonPath("$.items[0].shiftType.startTime").exists())
                .andExpect(jsonPath("$.items[0].shiftType.endTime").exists());
    }

    @Test
    void listClosable_returnsEmptyList() throws Exception {
        when(machineShiftQueryService.findClosable()).thenReturn(new MachineShiftClosableResponse(List.of()));

        mockMvc.perform(get("/machine-shifts/closable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void close_returnsNoContent() throws Exception {
        doNothing().when(machineShiftCloseService).close(eq(42L), any());

        mockMvc.perform(
                        post("/machine-shifts/42/close")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "completions": [
                                            { "operationId": 10, "count": 12 }
                                          ]
                                        }
                                        """))
                .andExpect(status().isNoContent());
    }

    @Test
    void close_emptyCompletions_returnsNoContent() throws Exception {
        doNothing().when(machineShiftCloseService).close(eq(42L), any());

        mockMvc.perform(
                        post("/machine-shifts/42/close")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        { "completions": [] }
                                        """))
                .andExpect(status().isNoContent());
    }
}
