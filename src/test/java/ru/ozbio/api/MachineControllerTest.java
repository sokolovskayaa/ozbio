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
import ru.ozbio.api.dto.MachineResponse;
import ru.ozbio.api.dto.MachineTypeResponse;
import ru.ozbio.api.handler.ApiExceptionHandler;
import ru.ozbio.service.MachineService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MachineControllerTest {

    @Mock
    MachineService machineService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new MachineController(machineService))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
    }

    @Test
    void createType_acceptsValidRequest() throws Exception {
        when(machineService.createType(any())).thenReturn(new MachineTypeResponse(1L, "Lathe"));

        mockMvc.perform(
                        post("/machines/types")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"typeName":"Lathe"}
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.typeName").value("Lathe"));
    }

    @Test
    void create_acceptsValidRequest() throws Exception {
        when(machineService.create(any())).thenReturn(new MachineResponse(1L, 2L, "Lathe"));

        mockMvc.perform(
                        post("/machines")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"machineTypeId":2}
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.machineTypeId").value(2));
    }

    @Test
    void listTypes_returnsMachineTypes() throws Exception {
        when(machineService.listTypes()).thenReturn(List.of(new MachineTypeResponse(1L, "Lathe")));

        mockMvc.perform(get("/machines/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].typeName").value("Lathe"));
    }

    @Test
    void list_returnsMachines() throws Exception {
        when(machineService.list()).thenReturn(List.of(new MachineResponse(1L, 2L, "Lathe")));

        mockMvc.perform(get("/machines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].machineTypeName").value("Lathe"));
    }

    @Test
    void deleteType_returnsNoContent() throws Exception {
        doNothing().when(machineService).deleteType(1L);

        mockMvc.perform(delete("/machines/types/1")).andExpect(status().isNoContent());
    }

    @Test
    void delete_returnsNoContent() throws Exception {
        doNothing().when(machineService).delete(1L);

        mockMvc.perform(delete("/machines/1")).andExpect(status().isNoContent());
    }
}
