package scheduler.store.core;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import scheduler.model.machine.Capability;
import scheduler.model.machine.Machine;
import scheduler.model.machine.MachineGroup;
import scheduler.model.machine.MachineGroupDefaults;
import scheduler.model.machine.MachineStatus;

/** Демо-каталог станков и групп для пустого snapshot. */
public final class DemoFactoryCatalog {
    private DemoFactoryCatalog() {}

    public static Map<String, MachineGroup> defaultMachineGroups() {
        Map<String, MachineGroup> groups = new LinkedHashMap<>();
        groups.put(
                "cnc",
                new MachineGroup(
                        "cnc",
                        "ЧПУ (фрезерный и токарный)",
                        MachineGroupDefaults.setupDuration("cnc")));
        groups.put(
                "heavy",
                new MachineGroup(
                        "heavy",
                        "Тяжёлое оборудование (расточка, шлифование)",
                        MachineGroupDefaults.setupDuration("heavy")));
        groups.put(
                "finish",
                new MachineGroup(
                        "finish",
                        "Сварка и сборка",
                        MachineGroupDefaults.setupDuration("finish")));
        return groups;
    }

    public static String defaultGroupForMachine(String machineId) {
        return switch (machineId) {
            case "ФРЕЗ-ЧПУ-01", "ТОКАР-ЧПУ-02" -> "cnc";
            case "РАСТОЧ-03", "ШЛИФ-04" -> "heavy";
            case "СВАРКА-05", "СБОРКА-06" -> "finish";
            default -> "cnc";
        };
    }

    public static List<Machine> defaultMachines(Instant factoryStartedAt) {
        return List.of(
                new Machine(
                        "ФРЕЗ-ЧПУ-01",
                        "cnc",
                        java.util.Set.of(Capability.MILLING),
                        factoryStartedAt,
                        MachineStatus.IDLE),
                new Machine(
                        "ТОКАР-ЧПУ-02",
                        "cnc",
                        java.util.Set.of(Capability.TURNING),
                        factoryStartedAt,
                        MachineStatus.IDLE),
                new Machine(
                        "РАСТОЧ-03",
                        "heavy",
                        java.util.Set.of(Capability.DEEP_BORING),
                        factoryStartedAt,
                        MachineStatus.IDLE),
                new Machine(
                        "ШЛИФ-04",
                        "heavy",
                        java.util.Set.of(Capability.GRINDING),
                        factoryStartedAt,
                        MachineStatus.IDLE),
                new Machine(
                        "СВАРКА-05",
                        "finish",
                        java.util.Set.of(Capability.WELDING),
                        factoryStartedAt,
                        MachineStatus.IDLE),
                new Machine(
                        "СБОРКА-06",
                        "finish",
                        java.util.Set.of(Capability.ASSEMBLY),
                        factoryStartedAt,
                        MachineStatus.IDLE));
    }
}
