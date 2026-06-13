package scheduler.store.core;

import java.time.Duration;
import java.util.List;
import scheduler.model.machine.Capability;
import scheduler.model.order.Task;

/** Справочник деталей по умолчанию для JSON-репозитория (unit-тесты без PostgreSQL). */
public final class CatalogSeeder {
    private CatalogSeeder() {}

    public static void seedPartDefinitions(ScheduleStore store) {
        store.setPartDefinition(
                "корпус-бура",
                new PartDefinition(
                        10,
                        List.of(
                                new Task("черновая-фрезеровка", 0, Duration.ofMinutes(90), Capability.MILLING),
                                new Task("расточивание-отверстий", 1, Duration.ofMinutes(120), Capability.DEEP_BORING),
                                new Task("чистовая-фрезеровка", 2, Duration.ofMinutes(60), Capability.MILLING))));
        store.setPartDefinition(
                "вал-буровой",
                new PartDefinition(
                        8,
                        List.of(
                                new Task("черновая-токарка", 0, Duration.ofMinutes(70), Capability.TURNING),
                                new Task("чистовая-токарка", 1, Duration.ofMinutes(45), Capability.TURNING),
                                new Task("шлифование-сегментов", 2, Duration.ofMinutes(50), Capability.GRINDING))));
        store.setPartDefinition(
                "гидроблок",
                new PartDefinition(
                        5,
                        List.of(
                                new Task("фрезерование-плоскостей", 0, Duration.ofMinutes(55), Capability.MILLING),
                                new Task("сверление-гидроканалов", 1, Duration.ofMinutes(40), Capability.DEEP_BORING))));
        store.setPartDefinition(
                "муфта-зажимная",
                new PartDefinition(
                        3,
                        List.of(
                                new Task("токарка-муфты", 0, Duration.ofMinutes(35), Capability.TURNING),
                                new Task("сварка-шва-MIG", 1, Duration.ofMinutes(25), Capability.WELDING))));
        store.setPartDefinition(
                "ниппель-соединительный",
                new PartDefinition(
                        4,
                        List.of(new Task("сборка-уплотнения", 0, Duration.ofMinutes(30), Capability.ASSEMBLY))));
    }
}
