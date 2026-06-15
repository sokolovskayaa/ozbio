package ru.ozbio.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ozbio.api.dto.CreateMachineRequest;
import ru.ozbio.api.dto.CreateMachineTypeRequest;
import ru.ozbio.persistence.MachineRepository;
import ru.ozbio.service.exception.InvalidReferenceException;
import ru.ozbio.service.exception.MachineNotFoundException;
import ru.ozbio.service.exception.MachineTypeInUseException;
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
    void deleteType_rejectsReferencedType() {
        when(machineRepository.machineTypeExists(1L)).thenReturn(true);
        when(machineRepository.machineTypeIsReferenced(1L)).thenReturn(true);

        assertThatThrownBy(() -> machineService.deleteType(1L)).isInstanceOf(MachineTypeInUseException.class);
    }

    @Test
    void create_persistsMachine() {
        when(machineRepository.machineTypeExists(1L)).thenReturn(true);
        when(machineRepository.insertMachine(1L)).thenReturn(10L);
        when(machineRepository.findMachineById(10L))
                .thenReturn(Optional.of(new MachineSummary(10L, 1L, "Lathe")));

        var response = machineService.create(new CreateMachineRequest(1L));

        assertThat(response.machineTypeName()).isEqualTo("Lathe");
    }

    @Test
    void create_rejectsUnknownMachineType() {
        when(machineRepository.machineTypeExists(99L)).thenReturn(false);

        assertThatThrownBy(() -> machineService.create(new CreateMachineRequest(99L)))
                .isInstanceOf(InvalidReferenceException.class);

        verify(machineRepository, never()).insertMachine(99L);
    }

    @Test
    void delete_removesMachine() {
        when(machineRepository.machineExists(10L)).thenReturn(true);
        when(machineRepository.deleteMachineById(10L)).thenReturn(true);

        machineService.delete(10L);

        verify(machineRepository).deleteMachineById(10L);
    }

    @Test
    void delete_throwsWhenMachineNotFound() {
        when(machineRepository.machineExists(99L)).thenReturn(false);

        assertThatThrownBy(() -> machineService.delete(99L)).isInstanceOf(MachineNotFoundException.class);
    }
}
