package ru.ozbio.api;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.ozbio.api.dto.ToolDetailResponse;
import ru.ozbio.api.dto.ToolResponse;
import ru.ozbio.api.handler.ApiExceptionHandler;
import ru.ozbio.service.ToolService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ToolControllerTest {

    @Mock
    ToolService toolService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new ToolController(toolService))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
    }

    @Test
    void createTool_acceptsValidRequest() throws Exception {
        when(toolService.create(any()))
                .thenReturn(
                        new ToolResponse(
                                1L,
                                "Drill",
                                Duration.ofMinutes(45),
                                List.of(new ToolDetailResponse(1L, "Body", 2))));

        mockMvc.perform(
                        post("/tools")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "name":"Drill",
                                          "assembleDuration":"PT45M",
                                          "details":[{"detailId":1,"count":2}]
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.details[0].count").value(2));
    }

    @Test
    void listTools_returnsTools() throws Exception {
        when(toolService.list())
                .thenReturn(
                        List.of(
                                new ToolResponse(
                                        1L,
                                        "Drill",
                                        Duration.ofMinutes(45),
                                        List.of(new ToolDetailResponse(1L, "Body", 2)))));

        mockMvc.perform(get("/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Drill"));
    }

    @Test
    void deleteTool_returnsNoContent() throws Exception {
        doNothing().when(toolService).delete(1L);

        mockMvc.perform(delete("/tools/1")).andExpect(status().isNoContent());
    }
}
