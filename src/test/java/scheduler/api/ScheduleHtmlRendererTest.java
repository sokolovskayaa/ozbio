package scheduler.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import scheduler.engine.OrderPriorities;
import scheduler.model.Capability;
import scheduler.model.MachineStatus;

class ScheduleHtmlRendererTest {

    @Test
    void render_containsOrdersTimelineAndMachineSchedule() {
        Instant t = Instant.parse("2026-05-22T08:00:00Z");
        AssignmentView assignment = new AssignmentView(
                "a1",
                "З-2026-0142",
                "вал-буровой",
                0,
                "черновая-токарка",
                "ТОКАР-ЧПУ-02",
                0,
                t,
                t.plusSeconds(3600),
                "PLANNED",
                null,
                null);
        ScheduleView view = new ScheduleView(
                t,
                new ClockView(true, t.plusSeconds(3600)),
                t,
                List.of(new PartCatalogView(
                        "вал-буровой",
                        8,
                        List.of(new TaskTemplateView("черновая-токарка", 0, "PT70M", "TURNING")))),
                List.of(new MachineGroupView(
                        "cnc",
                        "ЧПУ",
                        List.of(new WorkWindowView("Пт", "FRIDAY", "08:00", "20:00")),
                        "PT30M")),
                List.of(new MachineView(
                        "ТОКАР-ЧПУ-02",
                        "cnc",
                        "ЧПУ",
                        MachineStatus.IDLE,
                        t,
                        Set.of(Capability.TURNING))),
                List.of(new OrderScheduleView(
                        "З-2026-0142",
                        t,
                        OrderPriorities.fromCreatedAt(t),
                        t.plusSeconds(7200),
                        List.of(new PartScheduleView(
                                "вал-буровой",
                                1,
                                8,
                                t.plusSeconds(7200),
                                0,
                                List.of(assignment))))));

        String html = ScheduleHtmlRenderer.render(view);

        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("Заказ З-2026-0142"));
        assertTrue(html.contains("id=\"shift-close\""));
        assertTrue(html.contains("id=\"shift-close-form\""));
        assertTrue(html.contains("id=\"shift-stale-banner\""));
        assertTrue(html.contains("id=\"shift-count-rows\""));
        assertTrue(html.contains("shift-done-count"));
        assertFalse(html.contains("data-order-id"));
        assertTrue(html.indexOf("id=\"orders\"") < html.indexOf("id=\"machines\""));
        assertTrue(html.contains("id=\"machine-groups\""));
        assertTrue(html.contains("factory-overview"));
        assertTrue(html.contains("shift-summary"));
        assertTrue(html.contains("id=\"machine-schedule-panel\""));
        assertTrue(html.contains("machine-bar"));
        assertTrue(html.contains("id=\"add-order-form\""));
        assertFalse(html.contains("id=\"order-id\""));
        assertTrue(html.contains("присваивается автоматически"));
    }

    @Test
    void render_simulationDisabled_hidesControls() {
        Instant t = Instant.parse("2026-05-22T08:00:00Z");
        ScheduleView view = new ScheduleView(
                t,
                new ClockView(false, t),
                t,
                List.of(),
                List.of(),
                List.of(),
                List.of());

        String html = ScheduleHtmlRenderer.render(view);

        assertTrue(!html.contains("id=\"add-order-form\""));
        assertTrue(html.contains("Симуляция времени отключена"));
    }

    @Test
    void render_groupsSameOperationType_showsQuantityInOrdersSection() {
        Instant t = Instant.parse("2026-05-22T08:00:00Z");
        List<AssignmentView> threeUnits = List.of(
                new AssignmentView(
                        "a1",
                        "O1",
                        "P1",
                        0,
                        "T1",
                        "M1",
                        0,
                        t,
                        t.plusSeconds(3600),
                        "PLANNED",
                        null,
                        null),
                new AssignmentView(
                        "a2",
                        "O1",
                        "P1",
                        1,
                        "T1",
                        "M1",
                        0,
                        t.plusSeconds(7200),
                        t.plusSeconds(10800),
                        "PLANNED",
                        null,
                        null),
                new AssignmentView(
                        "a3",
                        "O1",
                        "P1",
                        2,
                        "T1",
                        "M1",
                        0,
                        t.plusSeconds(14400),
                        t.plusSeconds(18000),
                        "PLANNED",
                        null,
                        null));
        ScheduleView view = new ScheduleView(
                t,
                new ClockView(true, t),
                t,
                List.of(),
                List.of(),
                List.of(new MachineView("M1", "cnc", "ЧПУ", MachineStatus.IDLE, t, Set.of(Capability.MILLING))),
                List.of(new OrderScheduleView(
                        "O1",
                        t,
                        OrderPriorities.fromCreatedAt(t),
                        t.plusSeconds(18000),
                        List.of(new PartScheduleView("P1", 3, 5, t.plusSeconds(18000), 0, threeUnits)))));

        String html = ScheduleHtmlRenderer.render(view);

        assertTrue(html.contains("title=\"Количество: 3 шт.\""));
        assertTrue(html.contains("Количество: 3 шт."));
        assertTrue(html.contains("class=\"qty\">3 шт.</span>"));
        assertTrue(html.contains("class=\"order-parts\""));
        assertTrue(html.contains("<strong>3 шт.</strong>"));
        assertTrue(html.contains("<th>на 1 шт.</th>"));
        assertTrue(html.contains("<th>на 3 шт.</th>"));
        assertTrue(html.contains("1 ч"));
        assertTrue(html.contains("3 ч"));
        assertTrue(!html.contains("<th>Шт.</th>"));
    }

    @Test
    void render_perUnitDuration_usesCatalogNotCalendarSpanOutlier() {
        Instant t = Instant.parse("2026-05-22T08:00:00Z");
        List<AssignmentView> assignments = List.of(
                new AssignmentView(
                        "a1", "O1", "P1", 0, "T1", "M1", 0, t, t.plusSeconds(4200), "PLANNED", null, null),
                new AssignmentView(
                        "a2",
                        "O1",
                        "P1",
                        7,
                        "T1",
                        "M1",
                        0,
                        t.plusSeconds(3600),
                        t.plusSeconds(3600 + 3670 * 60L),
                        "PLANNED",
                        null,
                        null));
        ScheduleView view = new ScheduleView(
                t,
                new ClockView(true, t),
                t,
                List.of(new PartCatalogView(
                        "P1", 5, List.of(new TaskTemplateView("T1", 0, "PT1H10M", "TURNING")))),
                List.of(),
                List.of(new MachineView("M1", "cnc", "ЧПУ", MachineStatus.IDLE, t, Set.of(Capability.TURNING))),
                List.of(new OrderScheduleView(
                        "O1",
                        t,
                        OrderPriorities.fromCreatedAt(t),
                        t.plusSeconds(3600 + 3670 * 60L),
                        List.of(new PartScheduleView("P1", 50, 5, t.plusSeconds(3600 + 3670 * 60L), 0, assignments)))));

        String html = ScheduleHtmlRenderer.render(view);

        assertTrue(html.contains("1 ч 10 мин"));
        assertTrue(html.contains("class=\"dur\""));
    }
}
