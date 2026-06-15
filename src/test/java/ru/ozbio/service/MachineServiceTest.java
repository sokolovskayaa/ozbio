package ru.ozbio.service;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ozbio.api.dto.CreateMachineRequest;
import ru.ozbio.api.dto.CreateMachineTypeRequest;
import ru.ozbio.persistence.MachineRepository;
import ru.ozbio.service.exception.InvalidReferenceException;
import ru.ozbio.service.model.MachineSummary;
import ru.ozbio.service.model.MachineTypeSummary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MachineServiceTest {

    @Mock
    MachineRepository machineRepository;

    @InjectMocks
    MachineService machineService;

    @Test
    void createType_persistsMachineType() {
        when(machineRepository.insertType("Lathe")).thenReturn(new MachineTypeSummary(1L, "Lathe"));

        var response = machineService.createType(new CreateMachineTypeRequest("Lathe"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.typeName()).isEqualTo("Lathe");
    }

    @Test
    void listTypes_returnsAllMachineTypes() {
        when(machineRepository.findAllTypes())
                .thenReturn(List.of(new MachineTypeSummary(1L, "Lathe"), new MachineTypeSummary(2L, "Mill")));

        assertThat(machineService.listTypes()).hasSize(2);
    }

    @Test
    void list_returnsAllMachines() {
        when(machineRepository.findAllMachines())
                .thenReturn(List.of(new MachineSummary(1L, 1L, "Lathe")));

        assertThat(machineService.list()).hasSize(1);
    }

    @Test
    void deleteType_callsRepository() {
        machineService.deleteType(1L);

        verify(machineRepository).deleteTypeById(1L);
    }

    @Test
    void create_persistsMachine() {
        when(machineRepository.machineTypeExists(1L)).thenReturn(true);
        when(machineRepository.insertMachine(1L))
                .thenReturn(new MachineSummary(10L, 1L, "Lathe"));

        var response = machineService.create(new CreateMachineRequest(1L));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.machineTypeName()).isEqualTo("Lathe");
        verify(machineRepository).insertMachine(1L);
        verify(machineRepository, never()).findMachineById(10L);
    }

    @Test
    void create_rejectsUnknownMachineType() {
        when(machineRepository.machineTypeExists(99L)).thenReturn(false);

        assertThatThrownBy(() -> machineService.create(new CreateMachineRequest(99L)))
                .isInstanceOf(InvalidReferenceException.class);

        verify(machineRepository, never()).insertMachine(99L);
    }

    @Test
    void delete_callsRepository() {
        machineService.delete(10L);

        verify(machineRepository).deleteMachineById(10L);
    }
}
