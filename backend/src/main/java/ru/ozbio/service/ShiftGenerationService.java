package ru.ozbio.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ozbio.config.ShiftGenerationProperties;
import ru.ozbio.engine.shift.ShiftWindowCalculator;
import ru.ozbio.persistence.MachineShiftCalendarRepository;
import ru.ozbio.persistence.MachineShiftRepository;
import ru.ozbio.persistence.ShiftRepository;
import ru.ozbio.service.model.MachineShiftKey;
import ru.ozbio.service.model.MachineShiftTypeLink;
import ru.ozbio.service.model.ShiftGenerationResult;
import ru.ozbio.service.model.ShiftTypeSummary;

@Service
public class ShiftGenerationService {

    private final ShiftGenerationProperties properties;
    private final ShiftRepository shiftRepository;
    private final MachineShiftRepository machineShiftRepository;
    private final MachineShiftCalendarRepository machineShiftCalendarRepository;
    private final ShiftWindowCalculator shiftWindowCalculator;

    public ShiftGenerationService(
            ShiftGenerationProperties properties,
            ShiftRepository shiftRepository,
            MachineShiftRepository machineShiftRepository,
            MachineShiftCalendarRepository machineShiftCalendarRepository,
            ShiftWindowCalculator shiftWindowCalculator) {
        this.properties = properties;
        this.shiftRepository = shiftRepository;
        this.machineShiftRepository = machineShiftRepository;
        this.machineShiftCalendarRepository = machineShiftCalendarRepository;
        this.shiftWindowCalculator = shiftWindowCalculator;
    }

    @Transactional
    public ShiftGenerationResult generate() {
        ZoneId zone = ZoneId.of(properties.factoryZoneId());
        LocalDate today = LocalDate.now(zone);
        LocalDate horizonEnd = today.plusDays(properties.horizonDays() - 1L);

        Map<Long, ShiftTypeSummary> shiftTypesById =
                shiftRepository.findAll().stream()
                        .collect(Collectors.toMap(ShiftTypeSummary::id, Function.identity()));
        List<MachineShiftTypeLink> links = machineShiftRepository.findAllLinks();

        Set<MachineShiftKey> expectedKeys = new HashSet<>();
        int upserted = 0;

        for (LocalDate workDate = today; !workDate.isAfter(horizonEnd); workDate = workDate.plusDays(1)) {
            int dayOfWeek = workDate.getDayOfWeek().getValue();
            for (MachineShiftTypeLink link : links) {
                ShiftTypeSummary shiftType = shiftTypesById.get(link.shiftTypeId());
                if (shiftType == null || shiftType.dayOfWeek() != dayOfWeek) {
                    continue;
                }

                var window =
                        shiftWindowCalculator.calculate(
                                workDate, shiftType.startTime(), shiftType.endTime(), zone);
                MachineShiftKey key =
                        new MachineShiftKey(link.machineId(), link.shiftTypeId(), workDate);
                machineShiftCalendarRepository.upsertExpected(key, window.start(), window.end());
                expectedKeys.add(key);
                upserted++;
            }
        }

        int deleted = machineShiftCalendarRepository.deleteStaleExpected(today, horizonEnd, expectedKeys);
        return new ShiftGenerationResult(upserted, deleted);
    }
}
