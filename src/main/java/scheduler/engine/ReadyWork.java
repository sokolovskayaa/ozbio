package scheduler.engine;

import scheduler.model.Task;

/** Следующая готовая к планированию работа: штука + операция. */
public record ReadyWork(int unitIndex, Task task) {}
