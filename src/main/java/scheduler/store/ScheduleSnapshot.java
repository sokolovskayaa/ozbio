package scheduler.store;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import scheduler.model.Assignment;
import scheduler.model.MachineBlock;
import scheduler.model.Order;

/** DTO для {@code data/schedule.json}. */
public class ScheduleSnapshot {
    Instant factoryStartedAt;
    SimulationClockSettings simulationClock = new SimulationClockSettings();
    List<MachineSnapshot> machines = new ArrayList<>();
    List<MachineGroupSnapshot> machineGroups = new ArrayList<>();
    /** Справочник деталей: приоритет + последовательность задач (задаётся при старте). */
    Map<String, PartDefinitionSnapshot> partDefinitions = new LinkedHashMap<>();
    List<Order> orders = new ArrayList<>();
    List<Assignment> assignments = new ArrayList<>();
    List<MachineBlock> machineBlocks = new ArrayList<>();
    /** Конец последней закрытой смены по группе станков (groupId → instant). */
    Map<String, Instant> lastClosedShiftEndByGroup = new LinkedHashMap<>();
    SchedulingSettings scheduling = new SchedulingSettings();

    public static final class SchedulingSettings {
        /** Перекрытие пакетов между операциями на разных станках (§5). По умолчанию выкл. */
        boolean overlapBatches = false;
    }

    public static final class SimulationClockSettings {
        boolean enabled = true;
        Instant currentTime;
    }
}
