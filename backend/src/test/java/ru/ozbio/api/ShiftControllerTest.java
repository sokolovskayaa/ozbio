package ru.ozbio.api;

import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.ozbio.api.dto.ShiftTypeResponse;
import ru.ozbio.api.handler.ApiExceptionHandler;
import ru.ozbio.service.ShiftService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ShiftControllerTest {

    @Mock
    ShiftService shiftService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new ShiftController(shiftService))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
    }

    @Test
    void create_acceptsValidRequest() throws Exception {
        when(shiftService.create(any()))
                .thenReturn(new ShiftTypeResponse(1L, 1, LocalTime.of(8, 0), LocalTime.of(16, 0)));

        mockMvc.perform(
                        post("/shifts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "dayOfWeek": 1,
                                          "startTime": "08:00:00",
                                          "endTime": "16:00:00"
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.dayOfWeek").value(1))
                .andExpect(jsonPath("$.startTime").exists())
                .andExpect(jsonPath("$.endTime").exists());
    }

    @Test
    void delete_returnsNoContent() throws Exception {
        doNothing().when(shiftService).delete(1L);

        mockMvc.perform(delete("/shifts/1")).andExpect(status().isNoContent());
    }
}
