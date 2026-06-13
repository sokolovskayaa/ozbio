package scheduler.model.schedule;

/** Маркер операции переналадки в расписании. */
public final class SetupIntervals {
    public static final String TASK_ID = "переналадка";

    private SetupIntervals() {}

    public static boolean isSetup(String taskId) {
        return TASK_ID.equals(taskId);
    }
}
