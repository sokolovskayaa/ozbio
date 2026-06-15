package ru.ozbio.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ozbio.api.dto.CreateMachineRequest;
import ru.ozbio.api.dto.CreateMachineTypeRequest;
import ru.ozbio.api.dto.MachineResponse;
import ru.ozbio.api.dto.MachineTypeResponse;
import ru.ozbio.persistence.MachineRepository;
import ru.ozbio.service.exception.InvalidReferenceException;
import ru.ozbio.service.model.MachineTypeSummary;

@Service
public class MachineService {

    private final MachineRepository machineRepository;

    public MachineService(MachineRepository machineRepository) {
        this.machineRepository = machineRepository;
    }

    @Transactional
    public MachineTypeResponse createType(CreateMachineTypeRequest request) {
        MachineTypeSummary type = machineRepository.insertType(request.toCommand().typeName());
        return toResponse(type);
    }

    public List<MachineTypeResponse> listTypes() {
        return machineRepository.findAllTypes().stream().map(this::toResponse).toList();
    }

    @Transactional
    public void deleteType(long id) {
        machineRepository.deleteTypeById(id);
    }

    @Transactional
    public MachineResponse create(CreateMachineRequest request) {
        if (!machineRepository.machineTypeExists(request.machineTypeId())) {
            throw new InvalidReferenceException("machineTypeId", request.machineTypeId());
        }
        return toResponse(machineRepository.insertMachine(request.machineTypeId()));
    }

    public List<MachineResponse> list() {
        return machineRepository.findAllMachines().stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(long id) {
        machineRepository.deleteMachineById(id);
    }

    private MachineTypeResponse toResponse(MachineTypeSummary type) {
        return new MachineTypeResponse(type.id(), type.typeName());
    }

    private MachineResponse toResponse(ru.ozbio.service.model.MachineSummary machine) {
        return new MachineResponse(machine.id(), machine.machineTypeId(), machine.machineTypeName());
    }
}
