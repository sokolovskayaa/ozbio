package scheduler.model.machine;

/**
 * IDLE/BUSY — выводятся из расписания и {@code currentTime}.
 * DOWN/MAINTENANCE/SETUP — задаются вручную (JSON или API), блокируют новые назначения.
 */
public enum MachineStatus {
    IDLE,
    BUSY,
    DOWN,
    MAINTENANCE,
    SETUP;

    public String labelRu() {
        return switch (this) {
            case IDLE -> "Свободен";
            case BUSY -> "Занят";
            case DOWN -> "Аварийная остановка";
            case MAINTENANCE -> "Техобслуживание";
            case SETUP -> "Переналадка";
        };
    }
}
