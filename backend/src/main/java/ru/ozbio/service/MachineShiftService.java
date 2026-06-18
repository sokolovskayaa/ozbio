package ru.ozbio.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ozbio.api.dto.LinkMachinesToShiftRequest;
import ru.ozbio.persistence.MachineShiftRepository;
import ru.ozbio.persistence.ShiftRepository;
import ru.ozbio.service.exception.InvalidReferenceException;

@Service
public class MachineShiftService {

    private final MachineShiftRepository machineShiftRepository;
    private final ShiftRepository shiftRepository;

    public MachineShiftService(MachineShiftRepository machineShiftRepository, ShiftRepository shiftRepository) {
        this.machineShiftRepository = machineShiftRepository;
        this.shiftRepository = shiftRepository;
    }

    @Transactional
    public void linkMachines(long shiftTypeId, LinkMachinesToShiftRequest request) {
        validateShiftTypeExists(shiftTypeId);
        validateMachines(request.machineIds());
        machineShiftRepository.linkMachines(shiftTypeId, request.machineIds());
    }

    @Transactional
    public void unlinkMachine(long shiftTypeId, long machineId) {
        validateShiftTypeExists(shiftTypeId);
        if (!machineShiftRepository.machineExists(machineId)) {
            throw new InvalidReferenceException("machineId", machineId);
        }
        machineShiftRepository.unlinkMachine(shiftTypeId, machineId);
    }

    public List<Long> listMachineIds(long shiftTypeId) {
        validateShiftTypeExists(shiftTypeId);
        return machineShiftRepository.findMachineIdsByShiftTypeId(shiftTypeId);
    }

    private void validateShiftTypeExists(long shiftTypeId) {
        if (!shiftRepository.existsById(shiftTypeId)) {
            throw new InvalidReferenceException("shiftTypeId", shiftTypeId);
        }
    }

    private void validateMachines(List<Long> machineIds) {
        Set<Long> uniqueIds = new HashSet<>(machineIds);
        if (uniqueIds.size() != machineIds.size()) {
            throw new IllegalArgumentException("Duplicate machineId in request");
        }
        Set<Long> existing = machineShiftRepository.findExistingMachineIds(machineIds);
        for (long machineId : machineIds) {
            if (!existing.contains(machineId)) {
                throw new InvalidReferenceException("machineId", machineId);
            }
        }
    }
}
