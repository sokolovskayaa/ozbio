package scheduler.api;

import java.util.Map;
import scheduler.model.Capability;
import scheduler.model.MachineStatus;

/** Русские подписи для демо-данных завода бурового оборудования. */
public final class DomainLabels {
    private static final Map<String, String> PARTS = Map.ofEntries(
            Map.entry("корпус-бура", "Корпус бура DN500"),
            Map.entry("вал-буровой", "Вал буровой (шпиндель)"),
            Map.entry("гидроблок", "Гидравлический блок давления"),
            Map.entry("муфта-зажимная", "Зажимная муфта BHA"),
            Map.entry("ниппель-соединительный", "Ниппель соединительный"));

    private static final Map<String, String> TASKS = Map.ofEntries(
            Map.entry("черновая-фрезеровка", "Черновая фрезеровка корпуса"),
            Map.entry("расточивание-отверстий", "Растачивание посадочных отверстий"),
            Map.entry("чистовая-фрезеровка", "Чистовая фрезеровка"),
            Map.entry("черновая-токарка", "Черновая токарная обработка"),
            Map.entry("чистовая-токарка", "Чистовая токарная обработка"),
            Map.entry("шлифование-сегментов", "Шлифование посадочных сегментов"),
            Map.entry("фрезерование-плоскостей", "Фрезерование плоскостей"),
            Map.entry("сверление-гидроканалов", "Сверление гидравлических каналов"),
            Map.entry("токарка-муфты", "Токарная обработка корпуса муфты"),
            Map.entry("сварка-шва-MIG", "Сварка шва MIG"),
            Map.entry("сборка-уплотнения", "Сборка уплотнительного узла"));

    private static final Map<String, String> MACHINES = Map.ofEntries(
            Map.entry("ФРЕЗ-ЧПУ-01", "Вертикально-фрезерный центр ЧПУ №1"),
            Map.entry("ТОКАР-ЧПУ-02", "Токарный обрабатывающий центр ЧПУ №2"),
            Map.entry("РАСТОЧ-03", "Горизонтально-расточный станок №3"),
            Map.entry("ШЛИФ-04", "Круглошлифовальный станок №4"),
            Map.entry("СВАРКА-05", "Сварочный пост MIG/MAG №5"),
            Map.entry("СБОРКА-06", "Сборочный участок №6"));

    private DomainLabels() {}

    public static String partTitle(String partId) {
        return PARTS.getOrDefault(partId, partId);
    }

    public static String taskTitle(String taskId) {
        return TASKS.getOrDefault(taskId, taskId);
    }

    public static String machineTitle(String machineId) {
        return MACHINES.getOrDefault(machineId, machineId);
    }

    public static String capabilityTitle(String capabilityName) {
        try {
            return Capability.valueOf(capabilityName).labelRu();
        } catch (IllegalArgumentException e) {
            return capabilityName;
        }
    }

    public static String statusTitle(MachineStatus status) {
        return status.labelRu();
    }

    public static String statusTitle(String statusName) {
        try {
            return statusTitle(MachineStatus.valueOf(statusName));
        } catch (IllegalArgumentException e) {
            return statusName;
        }
    }
}
