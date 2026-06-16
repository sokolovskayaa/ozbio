package ru.ozbio.api;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.ozbio.api.dto.DetailResponse;
import ru.ozbio.api.dto.OperationResponse;
import ru.ozbio.api.handler.ApiExceptionHandler;
import ru.ozbio.service.DetailService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DetailControllerTest {

    @Mock
    DetailService detailService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new DetailController(detailService))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
    }

    @Test
    void createDetail_acceptsValidRequest() throws Exception {
        when(detailService.create(any()))
                .thenReturn(
                        new DetailResponse(
                                1L,
                                "Valve body",
                                List.of(
                                        new OperationResponse(10L, 1, 30, 0, 1L))));

        mockMvc.perform(
                        post("/details")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "name":"Valve body",
                                          "operations":[
                                            {"duration":30,"machineTypeId":1}
                                          ]
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.operations[0].step").value(1));
    }

    @Test
    void listDetails_returnsDetails() throws Exception {
        when(detailService.list())
                .thenReturn(
                        List.of(
                                new DetailResponse(
                                        1L,
                                        "Valve body",
                                        List.of(
                                                new OperationResponse(10L, 1, 30, 0, 1L)))));

        mockMvc.perform(get("/details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Valve body"));
    }

    @Test
    void deleteDetail_returnsNoContent() throws Exception {
        doNothing().when(detailService).delete(1L);

        mockMvc.perform(delete("/details/1")).andExpect(status().isNoContent());
    }
}
