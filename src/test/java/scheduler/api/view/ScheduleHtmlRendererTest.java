package scheduler.api.view;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import scheduler.engine.policy.OrderPriorities;
import scheduler.model.machine.Capability;
import scheduler.model.machine.MachineStatus;

class ScheduleHtmlRendererTest {

    @Test
    void render_containsOrderAndMachineTable() {
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
                new ClockView(false, t),
                t,
                List.of(new PartCatalogView(
                        "вал-буровой",
                        8,
                        List.of(new TaskTemplateView("черновая-токарка", 0, "PT70M", "TURNING")))),
                List.of(new MachineGroupView("cnc", "ЧПУ", "PT30M")),
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
        assertTrue(html.contains("З-2026-0142"));
        assertTrue(html.contains("Черновая токарная обработка"));
        assertTrue(html.contains("Расписание по станкам"));
        assertTrue(html.contains("machine-timeline"));
        assertTrue(html.contains("/css/schedule.css"));
        assertTrue(html.contains("/js/schedule-actions.js"));
    }
}
