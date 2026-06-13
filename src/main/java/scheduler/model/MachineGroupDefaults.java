package scheduler.model;

import java.time.Duration;
import java.util.Map;

/**
 * Среднее статическое время переналадки по типу группы станков.
 * Задаётся в {@code data/schedule.json} → {@code machineGroups[].setupMinutes} до запуска.
 */
public final class MachineGroupDefaults {
    /** ЧПУ: смена инструмента, привязка детали. */
    public static final int CNC_SETUP_MINUTES = 30;
    /** Расточка / шлифование: оснастка, измерение. */
    public static final int HEAVY_SETUP_MINUTES = 45;
    /** Сварка / сборка: присадки, оснастка поста. */
    public static final int FINISH_SETUP_MINUTES = 20;

    private static final Map<String, Integer> SETUP_MINUTES_BY_GROUP = Map.of(
            "cnc", CNC_SETUP_MINUTES,
            "heavy", HEAVY_SETUP_MINUTES,
            "finish", FINISH_SETUP_MINUTES);

    private MachineGroupDefaults() {}

    public static Duration setupDuration(String groupId) {
        Integer minutes = SETUP_MINUTES_BY_GROUP.get(groupId);
        if (minutes == null || minutes <= 0) {
            return Duration.ZERO;
        }
        return Duration.ofMinutes(minutes);
    }
}
