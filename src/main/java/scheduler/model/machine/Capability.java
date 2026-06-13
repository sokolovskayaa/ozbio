package scheduler.model.machine;

public enum Capability {
    MILLING,
    TURNING,
    WELDING,
    DEEP_BORING,
    GRINDING,
    ASSEMBLY;

    public String labelRu() {
        return switch (this) {
            case MILLING -> "Фрезерная обработка (ЧПУ)";
            case TURNING -> "Токарная обработка (ЧПУ)";
            case WELDING -> "Сварка (MIG/MAG)";
            case DEEP_BORING -> "Глубокое растачивание";
            case GRINDING -> "Шлифование";
            case ASSEMBLY -> "Сборочные операции";
        };
    }
}
