package scheduler.engine.planning;

import scheduler.model.order.Task;

/** Следующая готовая к планированию работа: штука + операция. */
public record ReadyWork(int unitIndex, Task task) {}
