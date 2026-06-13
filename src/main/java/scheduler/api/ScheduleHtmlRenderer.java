package scheduler.api;

import com.google.gson.Gson;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import scheduler.engine.FactoryZone;
import scheduler.engine.ShiftCalendar;
import scheduler.engine.ShiftCalendar.TimeSegment;
import scheduler.model.MachineGroup;
import scheduler.model.WorkWindow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import scheduler.model.Capability;
import scheduler.model.SetupIntervals;

public final class ScheduleHtmlRenderer {
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final Gson GSON = new Gson();

    private ScheduleHtmlRenderer() {}

    public static String render(ScheduleView view) {
        TimelineBounds bounds = computeTimelineBounds(view);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"ru\"><head><meta charset=\"UTF-8\">");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        html.append("<title>Расписание — завод бурового оборудования</title>");
        html.append("<style>");
        html.append(CSS);
        html.append("</style></head><body>");

        html.append("<header><h1>Завод бурового оборудования</h1>");
        html.append("<p class=\"meta\">План производства · сформировано ")
                .append(esc(FMT.format(view.generatedAt())))
                .append("</p>");
        html.append("<div class=\"cards\">");
        html.append(card("Старт смены", FMT.format(view.factoryStartedAt())));
        html.append(card(
                "Сейчас",
                FMT.format(view.clock().currentTime())
                        + (view.clock().simulationEnabled() ? " <span class=\"tag\">симуляция</span>" : "")));
        html.append(card("Заказов в очереди", String.valueOf(view.orders().size())));
        html.append(card("Станков", String.valueOf(view.machines().size())));
        html.append("</div>");
        appendPageNav(html);
        appendFactoryOverviewStrip(html, view, view.clock().currentTime());
        html.append("</header>");

        Instant now = view.clock().currentTime();
        appendControlsPanel(html, view, now);
        appendOrdersSection(html, view, now);
        appendShiftCloseSection(html, view, now);
        appendMachinesParkSection(html, view);
        appendMachineGroupsSection(html, view);
        appendMachineScheduleSection(html, view, bounds, now);
        appendCatalogSection(html, view);

        html.append("<script id=\"order-parts-json\" type=\"application/json\">");
        html.append(safeJsonForScript(GSON.toJson(partsByOrder(view))));
        html.append("</script>");
        html.append("<script id=\"part-titles-json\" type=\"application/json\">");
        html.append(safeJsonForScript(buildPartTitlesForScript(view)));
        html.append("</script>");
        html.append("<script id=\"schedule-meta-json\" type=\"application/json\">");
        html.append(safeJsonForScript(buildScheduleMeta(view, bounds, now)));
        html.append("</script>");
        html.append("<script id=\"machine-groups-json\" type=\"application/json\">");
        html.append(safeJsonForScript(GSON.toJson(view.machineGroups())));
        html.append("</script>");
        html.append("<script id=\"part-catalog-json\" type=\"application/json\">");
        html.append(safeJsonForScript(buildPartCatalogForScript(view)));
        html.append("</script>");
        html.append(ACTIONS_SCRIPT);
        html.append(FILTER_SCRIPT);
        html.append("</body></html>");
        return html.toString();
    }

    private static Map<String, List<String>> partsByOrder(ScheduleView view) {
        Map<String, List<String>> map = new TreeMap<>();
        for (OrderScheduleView order : view.orders()) {
            LinkedHashSet<String> parts = new LinkedHashSet<>();
            for (PartScheduleView part : order.parts()) {
                parts.add(part.partId());
            }
            map.put(order.orderId(), List.copyOf(parts));
        }
        return map;
    }

    private static void appendControlsPanel(StringBuilder html, ScheduleView view, Instant now) {
        html.append("<section id=\"controls\" class=\"controls-panel\"><h2>Управление планом</h2>");
        if (!view.clock().simulationEnabled()) {
            html.append("<p class=\"hint\">Симуляция времени отключена — добавление заказов и сдвиг ")
                    .append("времени доступны только через API.</p></section>");
            return;
        }
        html.append("<p class=\"hint\">Номер заказа присваивается автоматически (З-год-номер). ")
                .append("Планирование от текущего времени симуляции; время — только вперёд.</p>");
        html.append("<div id=\"controls-message\" class=\"controls-message hidden\" role=\"status\"></div>");
        html.append("<div class=\"controls-grid\">");

        html.append("<form id=\"add-order-form\" class=\"control-card\">");
        html.append("<h3>Новый заказ</h3>");
        html.append("<fieldset class=\"order-lines-field\"><legend>Детали</legend>");
        html.append("<div id=\"order-lines\" class=\"order-lines\"></div>");
        html.append("<button type=\"button\" class=\"btn-secondary\" id=\"order-add-line\">")
                .append("+ Добавить деталь</button>");
        html.append("</fieldset>");
        html.append("<button type=\"submit\" class=\"btn-primary\" id=\"order-submit\">")
                .append("Добавить заказ</button>");
        html.append("</form>");

        html.append("<form id=\"advance-time-form\" class=\"control-card\">");
        html.append("<h3>Время симуляции</h3>");
        html.append("<p class=\"sim-now\">Сейчас: <strong id=\"sim-now-label\">")
                .append(esc(FMT.format(now)))
                .append("</strong></p>");
        html.append("<div class=\"time-advance-btns\" role=\"group\" aria-label=\"Сдвиг вперёд\">");
        html.append("<button type=\"button\" class=\"btn-secondary\" data-advance-hours=\"2\">+2 ч</button>");
        html.append("<button type=\"button\" class=\"btn-secondary\" data-advance-hours=\"8\">+8 ч</button>");
        html.append("<button type=\"button\" class=\"btn-secondary\" data-advance-hours=\"24\">+1 день</button>");
        html.append("<button type=\"button\" class=\"btn-secondary\" data-advance-hours=\"168\">+1 неделя</button>");
        html.append("</div>");
        html.append("<label class=\"control-field\">Установить на ");
        html.append("<input type=\"datetime-local\" id=\"sim-time-input\" required></label>");
        html.append("<button type=\"submit\" class=\"btn-primary\" id=\"time-submit\">Вперёд</button>");
        html.append("</form>");

        html.append("</div></section>");
    }

    private static String buildPartCatalogForScript(ScheduleView view) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PartCatalogView part : view.partCatalog()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("partId", part.partId());
            row.put("title", DomainLabels.partTitle(part.partId()));
            row.put("priority", part.priority());
            rows.add(row);
        }
        return GSON.toJson(rows);
    }

    private static void appendShiftCloseSection(StringBuilder html, ScheduleView view, Instant now) {
        html.append("<section id=\"shift-close\"><h2>Закрытие смены</h2>");
        html.append("<div id=\"shift-stale-banner\" class=\"shift-stale-banner hidden\" role=\"alert\"></div>");
        html.append("<p id=\"shift-close-hint\" class=\"hint\">Заполните факты по ")
                .append("<strong>всем станкам</strong> всех незакрытых смен (все группы). ")
                .append("Перепланирование выполняется <strong>один раз</strong> только после нажатия кнопки.</p>");
        html.append("<h3 id=\"shift-close-title\" class=\"shift-close-title\">Загрузка…</h3>");
        html.append("<form id=\"shift-close-form\" class=\"hidden\">");
        html.append("<table class=\"shift-facts\"><thead><tr>");
        html.append("<th>Группа</th><th>Станок</th><th>Операция</th><th>В плане</th><th>Сделано</th>");
        html.append("</tr></thead><tbody id=\"shift-count-rows\"></tbody></table>");
        html.append("<details class=\"shift-idle-details\"><summary>Дополнительно: простои станков</summary>");
        html.append("<div id=\"idle-blocks\">");
        html.append("<div class=\"idle-row\">");
        html.append("<label>Станок <select class=\"idle-machine\">");
        for (MachineView m : view.machines()) {
            html.append("<option value=\"")
                    .append(esc(m.machineId()))
                    .append("\">")
                    .append(esc(DomainLabels.machineTitle(m.machineId())))
                    .append("</option>");
        }
        html.append("</select></label>");
        html.append("<label>с <input type=\"datetime-local\" class=\"idle-from\" value=\"")
                .append(esc(toDatetimeLocalValue(now)))
                .append("\"></label>");
        html.append("<label>по <input type=\"datetime-local\" class=\"idle-to\" value=\"")
                .append(esc(toDatetimeLocalValue(now.plusSeconds(3600))))
                .append("\"></label>");
        html.append("</div></div>");
        html.append("<button type=\"button\" class=\"btn-secondary\" id=\"add-idle-row\">+ Простой</button>");
        html.append("</details>");
        html.append("<p class=\"shift-actions\">");
        html.append("<button type=\"submit\" class=\"btn-primary\">Закрыть все смены и перепланировать</button>");
        html.append("</p></form></section>");
    }

    private static String toDatetimeLocalValue(Instant instant) {
        return instant.atZone(FactoryZone.ZONE).toLocalDateTime().toString().substring(0, 16);
    }

    private static void appendOrdersSection(StringBuilder html, ScheduleView view, Instant now) {
        html.append("<section id=\"orders\"><h2>Заказы</h2>");
        html.append("<p class=\"hint\">Одинаковые операции объединены в таблице. Диаграмма по станкам — ")
                .append("в разделе «Расписание по станкам». Сейчас (")
                .append(esc(FMT.format(now)))
                .append(").</p>");

        for (OrderScheduleView order : view.orders()) {
            html.append("<article class=\"order\"><h3>Заказ ")
                    .append(esc(order.orderId()))
                    .append("</h3>");
            html.append("<p>Принят: ")
                    .append(esc(FMT.format(order.createdAt())))
                    .append(" · Приоритет заказа: <strong>")
                    .append(order.priority())
                    .append("</strong> · Готовность: <strong>")
                    .append(esc(FMT.format(order.readyAt())))
                    .append("</strong></p>");
            if (!order.parts().isEmpty()) {
                html.append("<p class=\"order-parts\">");
                for (int i = 0; i < order.parts().size(); i++) {
                    PartScheduleView part = order.parts().get(i);
                    if (i > 0) {
                        html.append(" · ");
                    }
                    html.append(esc(DomainLabels.partTitle(part.partId())))
                            .append(" — <strong>")
                            .append(part.quantity())
                            .append(" шт.</strong>");
                }
                html.append("</p>");
            }

            for (PartScheduleView part : order.parts()) {
                html.append("<div class=\"part\"><h4 title=\"")
                        .append(esc(quantityTooltip(part.quantity())))
                        .append("\">")
                        .append(esc(DomainLabels.partTitle(part.partId())))
                        .append(" <code class=\"id\">")
                        .append(esc(part.partId()))
                        .append("</code> <span class=\"qty\">")
                        .append(part.quantity())
                        .append(" шт.</span> <span class=\"prio\">приоритет ")
                        .append(part.priority())
                        .append("</span>");
                if (part.slackMinutes() > 0) {
                    html.append(" <span class=\"slack\">резерв ")
                            .append(part.slackMinutes())
                            .append(" мин</span>");
                }
                html.append("</h4>");

                List<AssignmentGroup> groups = groupAssignments(part.assignments());

                html.append("<table class=\"tasks\"><thead><tr>");
                html.append("<th>Операция</th><th>Станок</th><th>#</th>");
                html.append("<th>на 1 шт.</th><th>на ")
                        .append(part.quantity())
                        .append(" шт.</th>");
                html.append("<th>Начало</th><th>Конец</th>");
                html.append("</tr></thead><tbody>");
                for (AssignmentGroup g : groups) {
                    OperationDurations durations =
                            durationsForGroup(g, part.assignments(), part.quantity(), view);
                    html.append("<tr title=\"")
                            .append(esc(groupTooltip(g, part.quantity(), durations)))
                            .append("\"><td>")
                            .append(esc(SetupIntervals.isSetup(g.taskId())
                                    ? "Переналадка"
                                    : DomainLabels.taskTitle(g.taskId())))
                            .append("</td><td>")
                            .append(esc(formatMachines(g.machineIds())))
                            .append("</td><td>")
                            .append(g.sequence())
                            .append("</td><td class=\"dur\">")
                            .append(esc(formatSpanMinutes(durations.perUnitMinutes())))
                            .append("</td><td class=\"dur\">")
                            .append(esc(formatSpanMinutes(durations.allUnitsEffectiveMinutes())))
                            .append("</td><td>")
                            .append(esc(FMT.format(g.plannedStart())))
                            .append("</td><td>")
                            .append(esc(FMT.format(g.plannedEnd())))
                            .append("</td></tr>");
                }
                html.append("</tbody></table></div>");
            }
            html.append("</article>");
        }

        if (view.orders().isEmpty()) {
            html.append("<p class=\"empty\">Заказов пока нет.</p>");
        }
        html.append("</section>");
    }

    private static void appendPageNav(StringBuilder html) {
        html.append("<nav class=\"page-nav\" aria-label=\"Разделы плана\">");
        html.append("<a href=\"#machines\">Парк станков</a>");
        html.append("<a href=\"#machine-groups\">Группы и смены</a>");
        html.append("<a href=\"#machine-schedule\">Расписание по станкам</a>");
        html.append("<a href=\"#orders\">Заказы</a>");
        html.append("<a href=\"#shift-close\">Закрытие смены</a>");
        html.append("</nav>");
    }

    private static void appendFactoryOverviewStrip(StringBuilder html, ScheduleView view, Instant now) {
        html.append("<div class=\"factory-overview\">");
        for (MachineGroupView group : view.machineGroups()) {
            html.append("<div class=\"factory-overview-group\">");
            html.append("<strong>").append(esc(group.name())).append("</strong> ");
            html.append("<code class=\"id\">").append(esc(group.groupId())).append("</code>");
            html.append("<div class=\"factory-overview-shifts\">");
            String todayShift = formatTodayShiftWindow(group, now);
            if (todayShift != null) {
                html.append("<span class=\"shift-today\">Сегодня: ")
                        .append(esc(todayShift))
                        .append("</span> ");
            }
            html.append("<span class=\"shift-setup\">переналадка ")
                    .append(esc(formatDuration(group.setupDuration())))
                    .append("</span>");
            html.append("</div><ul class=\"factory-overview-machines\">");
            view.machines().stream()
                    .filter(m -> m.groupId().equals(group.groupId()))
                    .forEach(m -> {
                        html.append("<li><span class=\"machine-name\">")
                                .append(esc(DomainLabels.machineTitle(m.machineId())))
                                .append("</span> <span class=\"id\">")
                                .append(esc(m.machineId()))
                                .append("</span> — ")
                                .append(esc(DomainLabels.statusTitle(m.status())))
                                .append("</li>");
                    });
            html.append("</ul></div>");
        }
        html.append("</div>");
    }

    private static String formatTodayShiftWindow(MachineGroupView group, Instant now) {
        if (group.workWindows().isEmpty()) {
            return "круглосуточно";
        }
        DayOfWeek today = now.atZone(FactoryZone.ZONE).getDayOfWeek();
        List<String> windows = new ArrayList<>();
        for (WorkWindowView w : group.workWindows()) {
            if (DayOfWeek.valueOf(w.dayCode()).equals(today)) {
                windows.add(w.start() + "–" + w.end());
            }
        }
        if (windows.isEmpty()) {
            return "выходной";
        }
        return String.join(", ", windows);
    }

    private static void appendCurrentShiftsSummary(StringBuilder html, ScheduleView view, Instant now) {
        html.append("<div class=\"shift-summary\" role=\"status\">");
        html.append("<span class=\"shift-summary-label\">Текущие смены (МСК, от ")
                .append(esc(FMT.format(now)))
                .append("):</span> ");
        boolean any = false;
        for (MachineGroupView groupView : view.machineGroups()) {
            MachineGroup group = toMachineGroup(groupView);
            var window = ShiftCalendar.shiftWindowContaining(now, group, FactoryZone.ZONE);
            html.append("<span class=\"shift-summary-item\">");
            html.append("<strong>").append(esc(groupView.name())).append("</strong> ");
            if (window.isPresent()) {
                any = true;
                html.append(esc(FMT.format(window.get().start())))
                        .append(" — ")
                        .append(esc(FMT.format(window.get().end())));
            } else {
                String today = formatTodayShiftWindow(groupView, now);
                html.append(today != null ? esc(today) : "—");
            }
            html.append("</span> ");
        }
        if (!any) {
            html.append("<span class=\"muted\">вне рабочего окна</span>");
        }
        html.append("</div>");
    }

    private static void appendMachinesParkSection(StringBuilder html, ScheduleView view) {
        html.append("<section id=\"machines\"><h2>Парк станков</h2><table><thead><tr>");
        html.append("<th>Станок</th><th>Статус</th><th>Свободен с</th><th>Возможности</th></tr></thead><tbody>");
        for (MachineView m : view.machines()) {
            String rowClass = statusClass(m.status().name());
            html.append("<tr class=\"").append(rowClass).append("\">");
            html.append("<td><strong>")
                    .append(esc(DomainLabels.machineTitle(m.machineId())))
                    .append("</strong><br><span class=\"id\">")
                    .append(esc(m.machineId()))
                    .append("</span><br><span class=\"group-tag\">")
                    .append(esc(m.groupName()))
                    .append("</span></td>");
            html.append("<td>").append(esc(DomainLabels.statusTitle(m.status()))).append("</td>");
            html.append("<td>").append(esc(FMT.format(m.availableAt()))).append("</td>");
            html.append("<td>").append(esc(formatCapabilities(m.capabilities()))).append("</td></tr>");
        }
        html.append("</tbody></table></section>");
    }

    private static void appendMachineGroupsSection(StringBuilder html, ScheduleView view) {
        html.append("<section id=\"machine-groups\"><h2>Группы станков и смены</h2>");
        html.append("<p class=\"hint\">Работы планируются только в указанные окна (часовой пояс завода: Москва). ");
        html.append("Переналадка добавляется автоматически при смене типа операции (taskId) на станке.</p>");
        for (MachineGroupView group : view.machineGroups()) {
            html.append("<h3>")
                    .append(esc(group.name()))
                    .append(" <code class=\"id\">")
                    .append(esc(group.groupId()))
                    .append("</code></h3>");
            html.append("<p>Переналадка: <strong>")
                    .append(esc(formatDuration(group.setupDuration())))
                    .append("</strong></p>");
            if (group.workWindows().isEmpty()) {
                html.append("<p class=\"empty\">Круглосуточно</p>");
            } else {
                html.append("<table><thead><tr><th>День</th><th>Начало</th><th>Конец</th></tr></thead><tbody>");
                for (WorkWindowView w : group.workWindows()) {
                    html.append("<tr><td>")
                            .append(esc(w.dayLabel()))
                            .append("</td><td>")
                            .append(esc(w.start()))
                            .append("</td><td>")
                            .append(esc(w.end()))
                            .append("</td></tr>");
                }
                html.append("</tbody></table>");
            }
        }
        html.append("</section>");
    }

    private static String formatDuration(String isoDuration) {
        if (isoDuration == null || isoDuration.isBlank()) {
            return "0 мин";
        }
        try {
            long minutes = Duration.parse(isoDuration).toMinutes();
            return formatSpanMinutes(minutes);
        } catch (Exception e) {
            return isoDuration;
        }
    }

    /** Длительности операции: на одну штуку и суммарное эффективное время станка на все штуки. */
    private record OperationDurations(long perUnitMinutes, long allUnitsEffectiveMinutes) {}

    private static OperationDurations durationsForGroup(
            AssignmentGroup g,
            List<AssignmentView> assignments,
            int partQuantity,
            ScheduleView view) {
        List<AssignmentView> units = assignments.stream()
                .filter(a -> a.orderId().equals(g.orderId())
                        && a.partId().equals(g.partId())
                        && a.taskId().equals(g.taskId()))
                .sorted(Comparator.comparingInt(AssignmentView::unitIndex))
                .toList();
        long perUnit = effectiveUnitMinutes(g, units, view);
        long allEffective;
        if (SetupIntervals.isSetup(g.taskId())) {
            allEffective = units.stream()
                    .mapToLong(a -> Duration.between(a.plannedStart(), a.plannedEnd()).toMinutes())
                    .sum();
        } else {
            allEffective = perUnit * Math.max(1, partQuantity);
        }
        return new OperationDurations(Math.max(0, perUnit), Math.max(0, allEffective));
    }

    /**
     * Эффективное время одной операции на штуку: из справочника (PT…), иначе медиана по отрезкам.
     * Не используем max(start→end): в плане «конец» может включать ночь/выходные между сменами.
     */
    private static long effectiveUnitMinutes(AssignmentGroup g, List<AssignmentView> units, ScheduleView view) {
        if (SetupIntervals.isSetup(g.taskId())) {
            return units.stream()
                    .mapToLong(a -> Duration.between(a.plannedStart(), a.plannedEnd()).toMinutes())
                    .min()
                    .orElse(0L);
        }
        return catalogTaskDurationMinutes(view, g.partId(), g.taskId()).orElseGet(() -> medianAssignmentMinutes(units));
    }

    private static java.util.OptionalLong catalogTaskDurationMinutes(
            ScheduleView view, String partId, String taskId) {
        for (PartCatalogView part : view.partCatalog()) {
            if (!part.partId().equals(partId)) {
                continue;
            }
            for (TaskTemplateView task : part.tasks()) {
                if (task.taskId().equals(taskId)) {
                    try {
                        return java.util.OptionalLong.of(Duration.parse(task.duration()).toMinutes());
                    } catch (Exception ignored) {
                        return java.util.OptionalLong.empty();
                    }
                }
            }
        }
        return java.util.OptionalLong.empty();
    }

    private static long medianAssignmentMinutes(List<AssignmentView> units) {
        long[] mins = units.stream()
                .mapToLong(a -> Duration.between(a.plannedStart(), a.plannedEnd()).toMinutes())
                .sorted()
                .toArray();
        if (mins.length == 0) {
            return 0L;
        }
        int mid = mins.length / 2;
        if (mins.length % 2 == 1) {
            return mins[mid];
        }
        return (mins[mid - 1] + mins[mid]) / 2;
    }

    private static String formatSpanMinutes(long totalMinutes) {
        if (totalMinutes <= 0) {
            return "0 мин";
        }
        if (totalMinutes < 60) {
            return totalMinutes + " мин";
        }
        long hours = totalMinutes / 60;
        long mins = totalMinutes % 60;
        if (hours < 24) {
            return mins == 0 ? hours + " ч" : hours + " ч " + mins + " мин";
        }
        long days = hours / 24;
        hours = hours % 24;
        StringBuilder sb = new StringBuilder();
        sb.append(days).append(" дн");
        if (hours > 0) {
            sb.append(' ').append(hours).append(" ч");
        }
        if (mins > 0) {
            sb.append(' ').append(mins).append(" мин");
        }
        return sb.toString().trim();
    }

    private static void appendMachineScheduleSection(
            StringBuilder html, ScheduleView view, TimelineBounds fullBounds, Instant now) {
        html.append("<section id=\"machine-schedule\"><h2>Расписание по станкам</h2>");
        appendCurrentShiftsSummary(html, view, now);
        html.append("<p class=\"hint\">Каждая строка — станок; серый фон — вне смены; ")
                .append("<span class=\"legend-setup\">оранжевый</span> — переналадка (смена типа операции на станке). ")
                .append("По умолчанию — <strong>текущая смена</strong> (08:00–20:00 и др. по группе). ")
                .append("Масштаб: пресеты, ◀ ▶, свой диапазон. ")
                .append("«По станку» — свой интервал каждой строки. ")
                .append("Наведите на отрезок для деталей.</p>");

        appendMachineScheduleFilters(html, view);
        appendZoomControls(html);

        Map<String, List<AssignmentView>> byMachine = assignmentsByMachine(view);
        Map<String, Integer> quantityByOrderPart = quantityByOrderPart(view);
        Map<String, Long> orderCreatedMs = orderCreatedEpochMs(view);
        Map<String, Integer> partPriority = partPriorityByPartId(view);
        boolean anyWork = byMachine.values().stream().anyMatch(list -> !list.isEmpty());

        TimelineBounds defaultZoom = defaultMachineZoom(fullBounds, now, view);
        html.append("<div id=\"machine-schedule-panel\" data-full-start-ms=\"")
                .append(fullBounds.start().toEpochMilli())
                .append("\" data-full-end-ms=\"")
                .append(fullBounds.end().toEpochMilli())
                .append("\" data-view-start-ms=\"")
                .append(defaultZoom.start().toEpochMilli())
                .append("\" data-view-end-ms=\"")
                .append(defaultZoom.end().toEpochMilli())
                .append("\" data-now-ms=\"")
                .append(now.toEpochMilli())
                .append("\" data-zoom-preset=\"")
                .append(esc(defaultZoomPresetId(fullBounds)))
                .append("\">");
        for (MachineView machine : view.machines()) {
            List<AssignmentView> assignments = byMachine.getOrDefault(machine.machineId(), List.of());
            TimelineBounds rowViewport = boundsForAssignments(assignments, view, now);

            html.append("<div class=\"machine-row\" data-machine=\"")
                    .append(esc(machine.machineId()))
                    .append("\" data-group=\"")
                    .append(esc(machine.groupId()))
                    .append("\">");
            appendMachineLabel(html, machine, assignments, rowViewport, view, now);
            appendTimelineBlock(
                    html,
                    defaultZoom,
                    now,
                    assignments,
                    view,
                    quantityByOrderPart,
                    false,
                    true,
                    orderCreatedMs,
                    partPriority,
                    true,
                    rowViewport);
            html.append("</div>");
        }
        html.append("</div>");

        html.append("<p id=\"machine-filter-empty\" class=\"empty hidden\">")
                .append("Нет операций по выбранным фильтрам.</p>");

        if (!anyWork) {
            html.append("<p class=\"empty\">Нет назначенных операций — добавьте заказ через API.</p>");
        }
        html.append("</section>");
    }

    private static void appendMachineScheduleFilters(StringBuilder html, ScheduleView view) {
        html.append("<div class=\"filters\" role=\"search\">");
        html.append("<fieldset class=\"filter-field\"><legend>Заказы ");
        html.append("<span class=\"filter-hint\">ничего не отмечено — все</span></legend>");
        html.append("<div id=\"filter-orders\" class=\"filter-checkboxes\">");
        for (OrderScheduleView order : view.orders()) {
            html.append("<label class=\"filter-check\">");
            html.append("<input type=\"checkbox\" name=\"filter-order\" value=\"")
                    .append(esc(order.orderId()))
                    .append("\"> ");
            html.append(esc(order.orderId()));
            html.append("</label>");
        }
        html.append("</div></fieldset>");

        html.append("<fieldset class=\"filter-field\"><legend>Детали ");
        html.append("<span class=\"filter-hint\">ничего не отмечено — все по выбранным заказам</span>");
        html.append("</legend><div id=\"filter-parts\" class=\"filter-checkboxes\"></div></fieldset>");

        html.append("<div class=\"filter-actions\">");
        html.append("<button type=\"button\" class=\"btn-reset\" id=\"filter-reset\">Сбросить</button>");
        html.append("</div>");

        html.append("<div class=\"color-legend\" id=\"color-legend\" aria-live=\"polite\"></div>");
        html.append("</div>");
    }

    private static Map<String, Long> orderCreatedEpochMs(ScheduleView view) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (OrderScheduleView order : view.orders()) {
            map.put(order.orderId(), order.createdAt().toEpochMilli());
        }
        return map;
    }

    private static Map<String, Integer> partPriorityByPartId(ScheduleView view) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (OrderScheduleView order : view.orders()) {
            for (PartScheduleView part : order.parts()) {
                map.putIfAbsent(part.partId(), part.priority());
            }
        }
        return map;
    }

    /** Максимальный {@code sequence} в маршруте детали (для градиента операций в фильтре). */
    private static Map<String, Integer> partMaxSequenceByPartId(ScheduleView view) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (PartCatalogView part : view.partCatalog()) {
            int maxSeq = part.tasks().stream().mapToInt(TaskTemplateView::sequence).max().orElse(0);
            map.put(part.partId(), maxSeq);
        }
        return map;
    }

    private static String buildScheduleMeta(ScheduleView view, TimelineBounds fullBounds, Instant now) {
        Map<String, Object> meta = new LinkedHashMap<>();
        List<Map<String, Object>> orders = view.orders().stream()
                .sorted(Comparator.comparingInt(OrderScheduleView::priority).reversed()
                        .thenComparing(OrderScheduleView::createdAt))
                .map(o -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", o.orderId());
                    row.put("createdAtMs", o.createdAt().toEpochMilli());
                    row.put("priority", o.priority());
                    return row;
                })
                .toList();
        meta.put("orders", orders);
        meta.put("partPriority", partPriorityByPartId(view));
        meta.put("partMaxSequence", partMaxSequenceByPartId(view));
        meta.put("factoryStartedAtMs", view.factoryStartedAt().toEpochMilli());
        meta.put("nowMs", now.toEpochMilli());
        meta.put("fullStartMs", fullBounds.start().toEpochMilli());
        meta.put("fullEndMs", fullBounds.end().toEpochMilli());
        meta.put("zoomPresets", zoomPresetsMap(view, now));
        meta.put("timeZone", FactoryZone.ZONE.getId());
        return GSON.toJson(meta);
    }

    private static Map<String, long[]> zoomPresetsMap(ScheduleView view, Instant now) {
        Map<String, long[]> presets = new LinkedHashMap<>();
        presets.put("shift", boundsToMs(presetShift(now, view)));
        presets.put("day", boundsToMs(presetDay(now)));
        presets.put("3d", boundsToMs(presetDays(now, 1, 2)));
        presets.put("week", boundsToMs(presetWeek(now)));
        return presets;
    }

    private static long[] boundsToMs(TimelineBounds b) {
        return new long[] {b.start().toEpochMilli(), b.end().toEpochMilli()};
    }

    private static void appendCatalogSection(StringBuilder html, ScheduleView view) {
        html.append("<section id=\"catalog\"><h2>Справочник деталей (технология)</h2>");
        for (PartCatalogView part : view.partCatalog()) {
            html.append("<h3>")
                    .append(esc(DomainLabels.partTitle(part.partId())))
                    .append(" <code class=\"id\">")
                    .append(esc(part.partId()))
                    .append("</code> <span class=\"prio\">приоритет ")
                    .append(part.priority())
                    .append("</span></h3>");
            html.append("<table><thead><tr><th>#</th><th>Операция</th><th>Длительность</th>");
            html.append("<th>Вид работ</th></tr></thead><tbody>");
            for (TaskTemplateView t : part.tasks()) {
                html.append("<tr><td>")
                        .append(t.sequence())
                        .append("</td><td>")
                        .append(esc(DomainLabels.taskTitle(t.taskId())))
                        .append("</td><td>")
                        .append(esc(t.duration()))
                        .append("</td><td>")
                        .append(esc(DomainLabels.capabilityTitle(t.capability())))
                        .append("</td></tr>");
            }
            html.append("</tbody></table>");
        }
        html.append("</section>");
    }

    private static Map<String, List<AssignmentView>> assignmentsByMachine(ScheduleView view) {
        Map<String, List<AssignmentView>> byMachine = new LinkedHashMap<>();
        for (MachineView m : view.machines()) {
            byMachine.put(m.machineId(), new ArrayList<>());
        }
        for (OrderScheduleView order : view.orders()) {
            for (PartScheduleView part : order.parts()) {
                for (AssignmentView a : part.assignments()) {
                    byMachine.computeIfAbsent(a.machineId(), k -> new ArrayList<>()).add(a);
                }
            }
        }
        return byMachine;
    }

    private static void appendZoomControls(StringBuilder html) {
        html.append("<div class=\"zoom-bar\" role=\"toolbar\" aria-label=\"Масштаб времени\">");
        html.append("<span class=\"zoom-label\">Масштаб:</span>");
        html.append("<button type=\"button\" class=\"btn-zoom\" data-zoom=\"shift\">Смена</button>");
        html.append("<button type=\"button\" class=\"btn-zoom\" data-zoom=\"day\">День</button>");
        html.append("<button type=\"button\" class=\"btn-zoom\" data-zoom=\"3d\">3 дня</button>");
        html.append("<button type=\"button\" class=\"btn-zoom\" data-zoom=\"week\">Неделя</button>");
        html.append("<button type=\"button\" class=\"btn-zoom\" data-zoom=\"row\">По станку</button>");
        html.append("<button type=\"button\" class=\"btn-zoom\" data-zoom=\"all\">Весь план</button>");
        html.append("<span class=\"zoom-nav\" aria-label=\"Листание интервала\">");
        html.append("<button type=\"button\" class=\"btn-zoom-nav\" id=\"zoom-prev\" title=\"Предыдущий интервал\">◀</button>");
        html.append("<button type=\"button\" class=\"btn-zoom-nav\" id=\"zoom-next\" title=\"Следующий интервал\">▶</button>");
        html.append("</span>");
        html.append("<span class=\"zoom-range\" id=\"zoom-range-label\"></span>");
        html.append("</div>");
        html.append("<div class=\"zoom-custom\" role=\"group\" aria-label=\"Свой диапазон\">");
        html.append("<span class=\"zoom-label\">Интервал:</span>");
        html.append("<label class=\"zoom-dt\">с <input type=\"datetime-local\" id=\"zoom-custom-start\"></label>");
        html.append("<label class=\"zoom-dt\">по <input type=\"datetime-local\" id=\"zoom-custom-end\"></label>");
        html.append("<button type=\"button\" class=\"btn-zoom\" id=\"zoom-custom-apply\">Показать</button>");
        html.append("</div>");
    }

    private static void appendTimelineBlock(
            StringBuilder html,
            TimelineBounds viewport,
            Instant now,
            List<AssignmentView> assignments,
            ScheduleView view,
            Map<String, Integer> quantityByOrderPart,
            boolean showBarText,
            boolean filterableBars,
            Map<String, Long> orderCreatedMs,
            Map<String, Integer> partPriority,
            boolean machineRow) {
        appendTimelineBlock(
                html,
                viewport,
                now,
                assignments,
                view,
                quantityByOrderPart,
                showBarText,
                filterableBars,
                orderCreatedMs,
                partPriority,
                machineRow,
                null);
    }

    private static void appendTimelineBlock(
            StringBuilder html,
            TimelineBounds viewport,
            Instant now,
            List<AssignmentView> assignments,
            ScheduleView view,
            Map<String, Integer> quantityByOrderPart,
            boolean showBarText,
            boolean filterableBars,
            Map<String, Long> orderCreatedMs,
            Map<String, Integer> partPriority,
            boolean machineRow,
            TimelineBounds rowViewport) {
        Map<String, MachineGroup> groupsById = machineGroupsById(view);
        Map<String, String> machineGroupId = machineToGroupId(view);

        html.append("<div class=\"timeline-block\">");
        html.append("<div class=\"timeline");
        if (machineRow) {
            html.append(" machine-timeline");
        }
        html.append("\" data-start-ms=\"")
                .append(viewport.start().toEpochMilli())
                .append("\" data-end-ms=\"")
                .append(viewport.end().toEpochMilli())
                .append("\" data-now-ms=\"")
                .append(now.toEpochMilli())
                .append("\" data-span=\"")
                .append(viewport.spanSeconds())
                .append("\"");
        if (machineRow && rowViewport != null) {
            html.append(" data-row-start-ms=\"")
                    .append(rowViewport.start().toEpochMilli())
                    .append("\" data-row-end-ms=\"")
                    .append(rowViewport.end().toEpochMilli())
                    .append("\"");
        }
        if (!assignments.isEmpty()) {
            String gid = machineGroupId.get(assignments.getFirst().machineId());
            if (gid != null) {
                html.append(" data-group=\"").append(esc(gid)).append("\"");
            }
        }
        html.append(">");

        Map<String, PartCalendarSpan> partSpansOnMachine = partCalendarSpansOnMachine(assignments);
        for (AssignmentView a : assignments) {
            MachineGroup group = groupsById.get(machineGroupId.get(a.machineId()));
            int qty = quantityByOrderPart.getOrDefault(orderPartKey(a.orderId(), a.partId()), 1);
            PartCalendarSpan partSpan = partSpansOnMachine.get(orderPartKey(a.orderId(), a.partId()));
            String tooltip = assignmentTooltip(a, qty, partSpan);
            boolean setup = SetupIntervals.isSetup(a.taskId());
            for (TimeSegment segment : segmentsForAssignment(a, group, view)) {
                double left = percentExact(viewport.start(), viewport.spanSeconds(), segment.start());
                double width = Math.max(
                        0.2,
                        percentDurationExact(
                                viewport.start(), viewport.spanSeconds(), segment.start(), segment.end()));
                html.append("<div class=\"bar");
                if (filterableBars) {
                    html.append(" machine-bar");
                    if (setup) {
                        html.append(" setup-bar");
                    }
                }
                html.append("\" style=\"left:")
                        .append(formatPercent(left))
                        .append(";width:")
                        .append(formatPercent(width))
                        .append("\"");
                html.append(" data-start-ms=\"")
                        .append(segment.start().toEpochMilli())
                        .append("\" data-end-ms=\"")
                        .append(segment.end().toEpochMilli())
                        .append("\" data-planned-start-ms=\"")
                        .append(a.plannedStart().toEpochMilli())
                        .append("\" data-planned-end-ms=\"")
                        .append(a.plannedEnd().toEpochMilli())
                        .append("\"");
                if (filterableBars) {
                    html.append(" data-order=\"")
                            .append(esc(a.orderId()))
                            .append("\" data-part=\"")
                            .append(esc(a.partId()))
                            .append("\" data-order-created=\"")
                            .append(orderCreatedMs.getOrDefault(a.orderId(), 0L))
                            .append("\" data-part-priority=\"")
                            .append(partPriority.getOrDefault(a.partId(), 0))
                            .append("\" data-sequence=\"")
                            .append(a.sequence())
                            .append("\"");
                }
                html.append(" title=\"").append(esc(tooltip)).append("\">");
                if (showBarText && width >= 3) {
                    html.append(esc(SetupIntervals.isSetup(a.taskId())
                            ? "Переналадка"
                            : DomainLabels.taskTitle(a.taskId())));
                }
                html.append("</div>");
            }
        }
        appendNowMarker(html, viewport.start(), viewport.spanSeconds(), now);
        html.append("</div>");
        appendTimeAxis(html, viewport);
        html.append("</div>");
    }

    private static Map<String, MachineGroup> machineGroupsById(ScheduleView view) {
        Map<String, MachineGroup> map = new LinkedHashMap<>();
        for (MachineGroupView gv : view.machineGroups()) {
            map.put(gv.groupId(), toMachineGroup(gv));
        }
        return map;
    }

    private static Map<String, String> machineToGroupId(ScheduleView view) {
        Map<String, String> map = new LinkedHashMap<>();
        for (MachineView m : view.machines()) {
            map.put(m.machineId(), m.groupId());
        }
        return map;
    }

    private static MachineGroup toMachineGroup(MachineGroupView view) {
        List<WorkWindow> windows = view.workWindows().stream()
                .map(w -> new WorkWindow(
                        DayOfWeek.valueOf(w.dayCode()), LocalTime.parse(w.start()), LocalTime.parse(w.end())))
                .toList();
        Duration setup = Duration.ZERO;
        try {
            setup = Duration.parse(view.setupDuration());
        } catch (Exception ignored) {
            // keep zero
        }
        return new MachineGroup(view.groupId(), view.name(), windows, setup);
    }

    private static List<TimeSegment> segmentsForAssignment(
            AssignmentView a, MachineGroup group, ScheduleView view) {
        Instant start = a.actualStart() != null ? a.actualStart() : a.plannedStart();
        Instant end = a.actualEnd() != null ? a.actualEnd() : a.plannedEnd();
        if (a.actualEnd() != null && !end.isBefore(start)) {
            return List.of(new TimeSegment(start, end));
        }
        Duration workDuration;
        if (SetupIntervals.isSetup(a.taskId())) {
            workDuration = group != null ? group.setupDuration() : Duration.between(a.plannedStart(), a.plannedEnd());
        } else {
            workDuration = taskDuration(view, a.taskId());
            if (workDuration.isZero()) {
                return List.of(new TimeSegment(start, end));
            }
        }
        return ShiftCalendar.workSegments(start, workDuration, group, FactoryZone.ZONE);
    }

    private static Duration taskDuration(ScheduleView view, String taskId) {
        for (PartCatalogView part : view.partCatalog()) {
            for (TaskTemplateView task : part.tasks()) {
                if (task.taskId().equals(taskId)) {
                    return Duration.parse(task.duration());
                }
            }
        }
        return Duration.ZERO;
    }

    private record PartCalendarSpan(String orderId, String partId, Instant start, Instant end) {}

    private static Map<String, PartCalendarSpan> partCalendarSpansOnMachine(List<AssignmentView> assignments) {
        Map<String, PartCalendarSpan> map = new LinkedHashMap<>();
        for (AssignmentView a : assignments) {
            String key = orderPartKey(a.orderId(), a.partId());
            PartCalendarSpan existing = map.get(key);
            if (existing == null) {
                map.put(key, new PartCalendarSpan(a.orderId(), a.partId(), a.plannedStart(), a.plannedEnd()));
            } else {
                Instant start = existing.start().isBefore(a.plannedStart()) ? existing.start() : a.plannedStart();
                Instant end = existing.end().isAfter(a.plannedEnd()) ? existing.end() : a.plannedEnd();
                map.put(key, new PartCalendarSpan(a.orderId(), a.partId(), start, end));
            }
        }
        return map;
    }

    private static void appendMachineLabel(
            StringBuilder html,
            MachineView machine,
            List<AssignmentView> assignments,
            TimelineBounds rowViewport,
            ScheduleView view,
            Instant now) {
        html.append("<div class=\"machine-label\"><strong>")
                .append(esc(DomainLabels.machineTitle(machine.machineId())))
                .append("</strong><br><span class=\"id\">")
                .append(esc(machine.machineId()))
                .append("</span><br><span class=\"group-tag\">")
                .append(esc(machine.groupName()))
                .append("</span>");
        String shiftToday = shiftWindowLabelForMachine(machine, view, now);
        if (!shiftToday.isEmpty()) {
            html.append(" <span class=\"shift-tag\">").append(esc(shiftToday)).append("</span>");
        }
        if (!assignments.isEmpty() && rowViewport != null) {
            html.append("<div class=\"machine-calendar-spans\">");
            html.append("<div class=\"machine-span-all\">Все детали (календарь): ")
                    .append(esc(formatCalendarRange(rowViewport.start(), rowViewport.end())))
                    .append("</div>");
            Map<String, PartCalendarSpan> byPart = partCalendarSpansOnMachine(assignments);
            if (!byPart.isEmpty()) {
                html.append("<ul class=\"machine-span-by-part\">");
                byPart.values().stream()
                        .sorted(Comparator.comparing(PartCalendarSpan::orderId)
                                .thenComparing(PartCalendarSpan::partId))
                        .forEach(span -> {
                            html.append("<li class=\"machine-span-part\" data-order=\"")
                                    .append(esc(span.orderId()))
                                    .append("\" data-part=\"")
                                    .append(esc(span.partId()))
                                    .append("\" data-start-ms=\"")
                                    .append(span.start().toEpochMilli())
                                    .append("\" data-end-ms=\"")
                                    .append(span.end().toEpochMilli())
                                    .append("\"><span class=\"part-name\">")
                                    .append(esc(DomainLabels.partTitle(span.partId())))
                                    .append("</span> <span class=\"part-range\">")
                                    .append(esc(formatCalendarRange(span.start(), span.end())))
                                    .append("</span></li>");
                        });
                html.append("</ul>");
            }
            html.append("</div>");
        }
        html.append("</div>");
    }

    private static String formatCalendarRange(Instant start, Instant end) {
        return FMT.format(start) + " — " + FMT.format(end);
    }

    private static String shiftWindowLabelForMachine(MachineView machine, ScheduleView view, Instant now) {
        for (MachineGroupView groupView : view.machineGroups()) {
            if (!groupView.groupId().equals(machine.groupId())) {
                continue;
            }
            String today = formatTodayShiftWindow(groupView, now);
            return today != null ? "смена " + today : "";
        }
        return "";
    }

    private static String assignmentTooltip(AssignmentView a, int partQuantity, PartCalendarSpan partOnMachine) {
        StringBuilder sb = new StringBuilder();
        sb.append("Заказ: ").append(a.orderId()).append('\n');
        sb.append("Деталь: ")
                .append(DomainLabels.partTitle(a.partId()))
                .append(" (")
                .append(a.partId())
                .append(")\n");
        sb.append("Количество: ").append(partQuantity).append(" шт.\n");
        sb.append("Операция: ")
                .append(SetupIntervals.isSetup(a.taskId())
                        ? "Переналадка"
                        : DomainLabels.taskTitle(a.taskId()))
                .append('\n');
        sb.append("Станок: ").append(DomainLabels.machineTitle(a.machineId())).append('\n');
        sb.append("Штука: ").append(a.unitIndex() + 1).append('\n');
        sb.append("Эта операция (календарь): ")
                .append(formatCalendarRange(a.plannedStart(), a.plannedEnd()))
                .append('\n');
        if (partOnMachine != null) {
            sb.append("Деталь на этом станке (календарь): ")
                    .append(formatCalendarRange(partOnMachine.start(), partOnMachine.end()));
        }
        return sb.toString();
    }

    private static TimelineBounds boundsForAssignments(
            List<AssignmentView> assignments, ScheduleView view, Instant now) {
        if (assignments.isEmpty()) {
            Instant end = now.plus(Duration.ofHours(2));
            return withPadding(new TimelineBounds(now, end, Duration.between(now, end).getSeconds()));
        }
        Map<String, MachineGroup> groupsById = machineGroupsById(view);
        Map<String, String> machineGroupId = machineToGroupId(view);
        Instant start = assignments.stream()
                .flatMap(a -> segmentsForAssignment(
                                a, groupsById.get(machineGroupId.get(a.machineId())), view)
                        .stream())
                .map(TimeSegment::start)
                .min(Comparator.naturalOrder())
                .orElse(now);
        Instant end = assignments.stream()
                .flatMap(a -> segmentsForAssignment(
                                a, groupsById.get(machineGroupId.get(a.machineId())), view)
                        .stream())
                .map(TimeSegment::end)
                .max(Comparator.naturalOrder())
                .orElse(now.plus(Duration.ofHours(1)));
        if (!now.isBefore(start)) {
            end = end.isAfter(now) ? end : now.plus(Duration.ofMinutes(30));
        }
        long span = Math.max(60, Duration.between(start, end).getSeconds());
        return withPadding(new TimelineBounds(start, end, span));
    }

    /** Группа: один тип операции (заказ + деталь + taskId), интервал — от первой до последней штуки. */
    private record AssignmentGroup(
            String orderId,
            String partId,
            String taskId,
            int sequence,
            Instant plannedStart,
            Instant plannedEnd,
            List<String> machineIds,
            int unitCount) {}

    private static List<AssignmentGroup> groupAssignments(List<AssignmentView> assignments) {
        record Key(String orderId, String partId, String taskId) {}

        Map<Key, List<AssignmentView>> buckets = new LinkedHashMap<>();
        for (AssignmentView a : assignments) {
            buckets.computeIfAbsent(new Key(a.orderId(), a.partId(), a.taskId()), k -> new ArrayList<>())
                    .add(a);
        }
        List<AssignmentGroup> groups = new ArrayList<>();
        for (Map.Entry<Key, List<AssignmentView>> entry : buckets.entrySet()) {
            List<AssignmentView> list = entry.getValue();
            Instant start = list.stream()
                    .map(AssignmentView::plannedStart)
                    .min(Comparator.naturalOrder())
                    .orElseThrow();
            Instant end = list.stream()
                    .map(AssignmentView::plannedEnd)
                    .max(Comparator.naturalOrder())
                    .orElseThrow();
            List<String> machines = list.stream()
                    .map(AssignmentView::machineId)
                    .distinct()
                    .sorted()
                    .toList();
            int unitCount = (int) list.stream().map(AssignmentView::unitIndex).distinct().count();
            int sequence = list.stream().mapToInt(AssignmentView::sequence).min().orElse(0);
            Key key = entry.getKey();
            groups.add(new AssignmentGroup(
                    key.orderId(), key.partId(), key.taskId(), sequence, start, end, machines, unitCount));
        }
        groups.sort(Comparator.comparing(AssignmentGroup::plannedStart));
        return groups;
    }

    private static Map<String, Integer> quantityByOrderPart(ScheduleView view) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (OrderScheduleView order : view.orders()) {
            for (PartScheduleView part : order.parts()) {
                map.put(orderPartKey(order.orderId(), part.partId()), part.quantity());
            }
        }
        return map;
    }

    private static String orderPartKey(String orderId, String partId) {
        return orderId + "\0" + partId;
    }

    private static String quantityTooltip(int quantity) {
        return "Количество: " + quantity + " шт.";
    }

    private static String shortBarLabel(AssignmentGroup g) {
        return SetupIntervals.isSetup(g.taskId())
                ? "Переналадка"
                : DomainLabels.taskTitle(g.taskId());
    }

    private static String groupTooltip(AssignmentGroup g, int partQuantity, OperationDurations durations) {
        StringBuilder sb = new StringBuilder();
        sb.append("Заказ: ").append(g.orderId()).append('\n');
        sb.append("Деталь: ")
                .append(DomainLabels.partTitle(g.partId()))
                .append(" (")
                .append(g.partId())
                .append(")\n");
        sb.append("Количество: ").append(partQuantity).append(" шт.\n");
        sb.append("На 1 шт.: ").append(formatSpanMinutes(durations.perUnitMinutes())).append('\n');
        sb.append("На все ")
                .append(partQuantity)
                .append(" шт.: ")
                .append(formatSpanMinutes(durations.allUnitsEffectiveMinutes()));
        if (SetupIntervals.isSetup(g.taskId())) {
            sb.append(" (суммарное время переналадок на станке)\n");
        } else {
            sb.append(" (")
                    .append(partQuantity)
                    .append(" × ")
                    .append(formatSpanMinutes(durations.perUnitMinutes()))
                    .append(", эффективное время станка)\n");
        }
        if (g.unitCount() > 1) {
            sb.append("На шкале: от первой до последней штуки (")
                    .append(g.unitCount())
                    .append(" шт. подряд в этой операции).\n");
        }
        sb.append("Операция: ")
                .append(SetupIntervals.isSetup(g.taskId())
                        ? "Переналадка"
                        : DomainLabels.taskTitle(g.taskId()))
                .append('\n');
        sb.append("Станок: ").append(formatMachines(g.machineIds())).append('\n');
        sb.append(FMT.format(g.plannedStart())).append(" — ").append(FMT.format(g.plannedEnd()));
        return sb.toString();
    }

    private static String formatMachines(List<String> machineIds) {
        return machineIds.stream().map(DomainLabels::machineTitle).reduce((a, b) -> a + "; " + b).orElse("");
    }

    private static String formatCapabilities(Set<Capability> capabilities) {
        return capabilities.stream().map(Capability::labelRu).sorted().reduce((a, b) -> a + "; " + b).orElse("");
    }

    private record TimelineBounds(Instant start, Instant end, long spanSeconds) {}

    private static TimelineBounds boundsForGroups(List<AssignmentGroup> groups, Instant now) {
        if (groups.isEmpty()) {
            Instant end = now.plus(Duration.ofHours(2));
            return withPadding(new TimelineBounds(now, end, Duration.between(now, end).getSeconds()));
        }
        Instant start = groups.stream()
                .map(AssignmentGroup::plannedStart)
                .min(Comparator.naturalOrder())
                .orElse(now);
        Instant end = groups.stream()
                .map(AssignmentGroup::plannedEnd)
                .max(Comparator.naturalOrder())
                .orElse(now.plus(Duration.ofHours(1)));
        if (!now.isBefore(start)) {
            end = end.isAfter(now) ? end : now.plus(Duration.ofMinutes(30));
        }
        long span = Math.max(60, Duration.between(start, end).getSeconds());
        return withPadding(new TimelineBounds(start, end, span));
    }

    private static TimelineBounds withPadding(TimelineBounds bounds) {
        long pad = Math.max(300, (long) (bounds.spanSeconds() * 0.08));
        Instant start = bounds.start().minusSeconds(pad);
        Instant end = bounds.end().plusSeconds(pad);
        long span = Duration.between(start, end).getSeconds();
        return new TimelineBounds(start, end, span);
    }

    private static TimelineBounds defaultMachineZoom(TimelineBounds full, Instant now, ScheduleView view) {
        long span = full.spanSeconds();
        if (span <= 86400) {
            return full;
        }
        return presetShift(now, view);
    }

    private static String defaultZoomPresetId(TimelineBounds full) {
        if (full.spanSeconds() <= 86400) {
            return "all";
        }
        return "shift";
    }

    /**
     * Окно текущей смены по группам станков (объединение окон, в которые попадает {@code now}).
     * Иначе при «конце смены» пресет «−2 ч … +10 ч» скрывает операции с утра.
     */
    private static TimelineBounds presetShift(Instant now, ScheduleView view) {
        Instant shiftStart = null;
        Instant shiftEnd = null;
        for (MachineGroupView groupView : view.machineGroups()) {
            MachineGroup group = toMachineGroup(groupView);
            var window = ShiftCalendar.shiftWindowContaining(now, group, FactoryZone.ZONE);
            if (window.isEmpty()) {
                continue;
            }
            Instant start = window.get().start();
            Instant end = window.get().end();
            if (shiftStart == null) {
                shiftStart = start;
                shiftEnd = end;
            } else {
                if (start.isBefore(shiftStart)) {
                    shiftStart = start;
                }
                if (end.isAfter(shiftEnd)) {
                    shiftEnd = end;
                }
            }
        }
        if (shiftStart == null || shiftEnd == null) {
            return new TimelineBounds(
                    now.minus(Duration.ofHours(2)),
                    now.plus(Duration.ofHours(10)),
                    Duration.ofHours(12).getSeconds());
        }
        long pad = 300;
        Instant start = shiftStart.minusSeconds(pad);
        Instant end = shiftEnd.plusSeconds(pad);
        return new TimelineBounds(start, end, Duration.between(start, end).getSeconds() + 2 * pad);
    }

    private static TimelineBounds presetDay(Instant now) {
        ZonedDateTime z = now.atZone(FactoryZone.ZONE);
        ZonedDateTime dayStart = z.truncatedTo(ChronoUnit.DAYS);
        ZonedDateTime dayEnd = dayStart.plusDays(1);
        return new TimelineBounds(dayStart.toInstant(), dayEnd.toInstant(), 86400);
    }

    private static TimelineBounds presetDays(Instant now, int daysBefore, int daysAfter) {
        Instant start = now.minus(Duration.ofDays(daysBefore));
        Instant end = now.plus(Duration.ofDays(daysAfter));
        return new TimelineBounds(start, end, Duration.between(start, end).getSeconds());
    }

    private static TimelineBounds presetWeek(Instant now) {
        return presetDays(now, 2, 5);
    }

    private record AxisTick(Instant instant, String align) {}

    private static void appendTimeAxis(StringBuilder html, TimelineBounds viewport) {
        html.append("<div class=\"time-axis\">");
        for (AxisTick tick : timeAxisTicksPlaced(viewport)) {
            html.append("<span class=\"tick tick-").append(tick.align()).append("\"");
            if ("end".equals(tick.align())) {
                html.append(" style=\"right:0\"");
            } else {
                html.append(" style=\"left:")
                        .append(formatPercent(percentExact(
                                viewport.start(), viewport.spanSeconds(), tick.instant())))
                        .append("\"");
            }
            html.append(">")
                    .append(esc(formatAxisTick(tick.instant(), viewport.spanSeconds(), tick.align())))
                    .append("</span>");
        }
        html.append("</div>");
    }

    private static List<AxisTick> timeAxisTicksPlaced(TimelineBounds viewport) {
        long span = viewport.spanSeconds();
        if (span <= 0) {
            return List.of();
        }
        long stepSeconds = axisStepSeconds(span);
        List<AxisTick> ticks = new ArrayList<>();
        ticks.add(new AxisTick(viewport.start(), "start"));

        Instant cursor = viewport.start().plusSeconds(stepSeconds);
        int lastPct = 0;
        while (cursor.isBefore(viewport.end()) && ticks.size() < 11) {
            int pct = percent(viewport.start(), span, cursor);
            if (pct - lastPct >= 12 && pct <= 88) {
                ticks.add(new AxisTick(cursor, "mid"));
                lastPct = pct;
            }
            cursor = cursor.plusSeconds(stepSeconds);
        }
        ticks.add(new AxisTick(viewport.end(), "end"));
        return ticks;
    }

    private static long axisStepSeconds(long spanSeconds) {
        if (spanSeconds <= 6 * 3600) {
            return 3600;
        }
        if (spanSeconds <= 2 * 86400) {
            return 3 * 3600;
        }
        if (spanSeconds <= 10 * 86400) {
            return 86400;
        }
        return 2 * 86400;
    }

    private static String formatAxisTick(Instant instant, long spanSeconds, String align) {
        ZoneId zone = FactoryZone.ZONE;
        if ("mid".equals(align) && spanSeconds > 2 * 86400) {
            return DateTimeFormatter.ofPattern("dd.MM").withZone(zone).format(instant);
        }
        DateTimeFormatter fmt = spanSeconds <= 86400
                ? DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
                : DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(zone);
        return fmt.format(instant);
    }

    private static TimelineBounds computeTimelineBounds(ScheduleView view) {
        Instant timelineStart = view.factoryStartedAt();
        Instant timelineEnd = view.orders().stream()
                .flatMap(o -> o.parts().stream())
                .flatMap(p -> p.assignments().stream())
                .map(AssignmentView::plannedEnd)
                .max(Comparator.naturalOrder())
                .orElse(view.clock().currentTime());
        if (view.clock().currentTime().isAfter(timelineEnd)) {
            timelineEnd = view.clock().currentTime();
        }
        if (!timelineEnd.isAfter(timelineStart)) {
            timelineEnd = timelineStart.plus(Duration.ofHours(1));
        }
        long spanSeconds = Duration.between(timelineStart, timelineEnd).getSeconds();
        if (spanSeconds <= 0) {
            spanSeconds = 3600;
        }
        return new TimelineBounds(timelineStart, timelineEnd, spanSeconds);
    }

    private static void appendNowMarker(
            StringBuilder html, Instant start, long spanSeconds, Instant now) {
        if (now.isBefore(start)) {
            return;
        }
        int left = percent(start, spanSeconds, now);
        if (left > 100) {
            return;
        }
        html.append("<div class=\"now\" style=\"left:")
                .append(formatPercent(percentExact(start, spanSeconds, now)))
                .append("\"></div>");
    }

    private static int percent(Instant start, long spanSeconds, Instant instant) {
        return (int) Math.round(percentExact(start, spanSeconds, instant));
    }

    private static double percentExact(Instant start, long spanSeconds, Instant instant) {
        if (spanSeconds <= 0) {
            return 0;
        }
        long offset = Duration.between(start, instant).getSeconds();
        return Math.min(100, Math.max(0, (offset * 100.0) / spanSeconds));
    }

    private static double percentDurationExact(Instant start, long spanSeconds, Instant from, Instant to) {
        return Math.max(0.05, percentExact(start, spanSeconds, to) - percentExact(start, spanSeconds, from));
    }

    private static int percentDuration(Instant start, long spanSeconds, Instant from, Instant to) {
        return (int) Math.max(1, Math.round(percentDurationExact(start, spanSeconds, from, to)));
    }

    private static String formatPercent(double value) {
        return String.format(Locale.US, "%.2f%%", value);
    }

    private static String card(String label, String value) {
        return "<div class=\"card\"><span>" + esc(label) + "</span><strong>" + value + "</strong></div>";
    }

    private static String statusClass(String status) {
        return switch (status) {
            case "DOWN", "MAINTENANCE", "SETUP" -> "warn";
            case "BUSY" -> "busy";
            default -> "";
        };
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** JSON внутри &lt;script&gt;: без HTML-escaping (иначе ломается JSON.parse). */
    private static String safeJsonForScript(String json) {
        if (json == null) {
            return "";
        }
        return json.replace("</", "<\\/");
    }

    private static final String ACTIONS_SCRIPT =
            """
            <script>
            (function(){
              const addOrderForm=document.getElementById('add-order-form');
              const advanceTimeForm=document.getElementById('advance-time-form');
              const shiftCloseForm=document.getElementById('shift-close-form');
              if(!addOrderForm&&!advanceTimeForm&&!shiftCloseForm)return;

              const msgEl=document.getElementById('controls-message');
              let scheduleMeta={nowMs:0,timeZone:'Europe/Moscow'};
              let partCatalog=[];
              try{scheduleMeta=JSON.parse(document.getElementById('schedule-meta-json').textContent||'{}');}catch(e){}
              try{partCatalog=JSON.parse(document.getElementById('part-catalog-json').textContent||'[]');}catch(e){}
              const TZ=scheduleMeta.timeZone||'Europe/Moscow';
              let nowMs=Number(scheduleMeta.nowMs)||Date.now();

              function showMsg(text,isError){
                if(!msgEl)return;
                msgEl.textContent=text;
                msgEl.className='controls-message '+(isError?'error':'ok');
                msgEl.classList.remove('hidden');
              }
              function hideMsg(){if(msgEl)msgEl.classList.add('hidden');}

              function msToDatetimeLocal(ms){
                const s=new Date(ms).toLocaleString('sv-SE',{timeZone:TZ,hour12:false});
                return s.slice(0,16).replace(' ','T');
              }
              function datetimeLocalToMs(value){
                if(!value)return NaN;
                return Date.parse(value+':00+03:00');
              }
              function refreshNowUi(){
                const label=document.getElementById('sim-now-label');
                const input=document.getElementById('sim-time-input');
                if(label)label.textContent=msToDatetimeLocal(nowMs).replace('T',' ');
                if(input){
                  input.min=msToDatetimeLocal(nowMs);
                  if(!input.value||datetimeLocalToMs(input.value)<=nowMs){
                    input.value=msToDatetimeLocal(nowMs+3600000);
                  }
                }
              }
              refreshNowUi();

              function buildPartSelect(selectedId){
                const sel=document.createElement('select');
                sel.required=true;
                sel.className='order-part-select';
                const empty=document.createElement('option');
                empty.value='';
                empty.textContent='— выберите деталь —';
                sel.appendChild(empty);
                partCatalog.forEach(function(p){
                  const opt=document.createElement('option');
                  opt.value=p.partId;
                  opt.textContent=p.title+(p.priority!=null?' (приор. '+p.priority+')':'');
                  if(p.partId===selectedId)opt.selected=true;
                  sel.appendChild(opt);
                });
                return sel;
              }

              function addOrderLine(partId,qty){
                const box=document.getElementById('order-lines');
                if(!box)return;
                const row=document.createElement('div');
                row.className='order-line';
                const selWrap=document.createElement('label');
                selWrap.className='order-line-part';
                selWrap.textContent='Деталь ';
                selWrap.appendChild(buildPartSelect(partId));
                const qtyWrap=document.createElement('label');
                qtyWrap.className='order-line-qty';
                qtyWrap.textContent='Кол-во ';
                const qtyIn=document.createElement('input');
                qtyIn.type='number';
                qtyIn.min='1';
                qtyIn.max='9999';
                qtyIn.value=String(qty!=null?qty:1);
                qtyIn.required=true;
                qtyIn.className='order-qty-input';
                qtyWrap.appendChild(qtyIn);
                const rm=document.createElement('button');
                rm.type='button';
                rm.className='btn-icon';
                rm.title='Удалить строку';
                rm.textContent='×';
                rm.addEventListener('click',function(){
                  if(box.querySelectorAll('.order-line').length>1)row.remove();
                });
                row.appendChild(selWrap);
                row.appendChild(qtyWrap);
                row.appendChild(rm);
                box.appendChild(row);
              }

              const addLineBtn=document.getElementById('order-add-line');
              if(addLineBtn){
                addLineBtn.addEventListener('click',function(){addOrderLine();});
                const lines=document.getElementById('order-lines');
                if(lines&&!lines.children.length){
                  const defaultPart=partCatalog.length?partCatalog[0].partId:null;
                  addOrderLine(defaultPart,1);
                }
              }

              if(addOrderForm){
                addOrderForm.addEventListener('submit',function(ev){
                  ev.preventDefault();
                  hideMsg();
                  const parts=[];
                  const seen=new Set();
                  let duplicatePart=false;
                  document.querySelectorAll('#order-lines .order-line').forEach(function(row){
                    const pid=row.querySelector('.order-part-select')?.value;
                    const q=parseInt(row.querySelector('.order-qty-input')?.value||'1',10);
                    if(!pid)return;
                    if(seen.has(pid)){
                      showMsg('Деталь «'+pid+'» указана дважды — объедините в одну строку',true);
                      duplicatePart=true;
                      return;
                    }
                    seen.add(pid);
                    parts.push({partId:pid,quantity:q});
                  });
                  if(duplicatePart)return;
                  if(!parts.length){showMsg('Добавьте хотя бы одну деталь',true);return;}
                  const submitBtn=document.getElementById('order-submit');
                  if(submitBtn)submitBtn.disabled=true;
                  fetch('/orders',{
                    method:'POST',
                    headers:{'Content-Type':'application/json'},
                    body:JSON.stringify({parts:parts})
                  }).then(function(r){
                    return r.json().then(function(j){return {ok:r.ok,status:r.status,body:j};});
                  }).then(function(res){
                    if(submitBtn)submitBtn.disabled=false;
                    if(!res.ok){
                      showMsg(res.body&&res.body.error?res.body.error:('Ошибка '+res.status),true);
                      return;
                    }
                    const id=res.body&&res.body.orderId?res.body.orderId:'';
                    showMsg('Заказ '+(id||'')+' добавлен',false);
                    setTimeout(function(){location.reload();},600);
                  }).catch(function(err){
                    if(submitBtn)submitBtn.disabled=false;
                    showMsg('Сеть: '+err,true);
                  });
                });
              }

              function advanceToMs(targetMs){
                if(targetMs<=nowMs){
                  showMsg('Время можно переводить только вперёд (сейчас '+msToDatetimeLocal(nowMs)+')',true);
                  return Promise.resolve();
                }
                const btn=document.getElementById('time-submit');
                if(btn)btn.disabled=true;
                const iso=new Date(targetMs).toISOString();
                return fetch('/time',{
                  method:'PUT',
                  headers:{'Content-Type':'application/json'},
                  body:JSON.stringify({currentTime:iso})
                }).then(function(r){
                  return r.json().then(function(j){return {ok:r.ok,body:j};});
                }).then(function(res){
                  if(btn)btn.disabled=false;
                  if(!res.ok){
                    showMsg(res.body&&res.body.error?res.body.error:('Ошибка времени'),true);
                    return;
                  }
                  nowMs=targetMs;
                  refreshNowUi();
                  showMsg('Время: '+msToDatetimeLocal(nowMs).replace('T',' '),false);
                  setTimeout(function(){location.reload();},500);
                }).catch(function(err){
                  if(btn)btn.disabled=false;
                  showMsg('Сеть: '+err,true);
                });
              }

              document.querySelectorAll('[data-advance-hours]').forEach(function(btn){
                btn.addEventListener('click',function(){
                  hideMsg();
                  const h=Number(btn.getAttribute('data-advance-hours'))||0;
                  advanceToMs(nowMs+h*3600000);
                });
              });

              if(advanceTimeForm){
                advanceTimeForm.addEventListener('submit',function(ev){
                  ev.preventDefault();
                  hideMsg();
                  const input=document.getElementById('sim-time-input');
                  const targetMs=datetimeLocalToMs(input&&input.value);
                  if(isNaN(targetMs)){showMsg('Укажите дату и время',true);return;}
                  advanceToMs(targetMs);
                });
              }

              function formatShiftRange(startIso,endIso){
                const fmt=function(iso){
                  const d=new Date(iso);
                  return d.toLocaleString('ru-RU',{timeZone:TZ,day:'2-digit',month:'2-digit',
                    hour:'2-digit',minute:'2-digit'});
                };
                return fmt(startIso)+' — '+fmt(endIso);
              }

              function renderShiftContext(ctx){
                const banner=document.getElementById('shift-stale-banner');
                const titleEl=document.getElementById('shift-close-title');
                const tbody=document.getElementById('shift-count-rows');
                const form=document.getElementById('shift-close-form');
                if(!ctx||!tbody||!form)return;
                const rows=ctx.closeRows||[];
                if(ctx.stale&&banner){
                  banner.classList.remove('hidden');
                  banner.textContent='Расписание неактуально: не закрыто смен — '+
                    ctx.pendingShiftCount+'. Заполните таблицу по всем станкам и закройте смены одной кнопкой.';
                  document.body.classList.add('schedule-stale');
                }else if(banner){
                  banner.classList.add('hidden');
                  document.body.classList.remove('schedule-stale');
                }
                if(!rows.length){
                  if(titleEl)titleEl.textContent='Нет смен для закрытия';
                  form.classList.add('hidden');
                  return;
                }
                if(titleEl){
                  titleEl.textContent='Закрыть смены ('+ctx.pendingShiftCount+'): факты по всем станкам';
                }
                tbody.innerHTML='';
                let maxShiftEnd=null;
                rows.forEach(function(row){
                  if(row.shiftEnd&&(!maxShiftEnd||row.shiftEnd>maxShiftEnd))maxShiftEnd=row.shiftEnd;
                  const tr=document.createElement('tr');
                  tr.setAttribute('data-group-id',row.groupId);
                  tr.setAttribute('data-machine-id',row.machineId);
                  tr.setAttribute('data-task-id',row.taskId);
                  const shiftHint=formatShiftRange(row.shiftStart,row.shiftEnd);
                  tr.innerHTML='<td title="'+shiftHint+'">'+row.groupName+'</td><td>'+row.machineTitle+
                    '</td><td>'+row.taskTitle+'</td><td class="num">'+row.plannedCount+
                    '</td><td><input type="number" class="shift-done-count" min="0" max="'+
                    row.plannedCount+'" value="'+row.defaultCompletedCount+'" required></td>';
                  tbody.appendChild(tr);
                });
                form.dataset.shiftEnd=maxShiftEnd||'';
                form.classList.remove('hidden');
              }

              function loadShiftContext(){
                fetch('/shifts/context').then(function(r){return r.json();})
                  .then(renderShiftContext)
                  .catch(function(){});
              }
              loadShiftContext();

              const addIdleBtn=document.getElementById('add-idle-row');
              if(addIdleBtn){
                addIdleBtn.addEventListener('click',function(){
                  const box=document.getElementById('idle-blocks');
                  const first=box&&box.querySelector('.idle-row');
                  if(box&&first)box.appendChild(first.cloneNode(true));
                });
              }

              if(shiftCloseForm){
                shiftCloseForm.addEventListener('submit',function(ev){
                  ev.preventDefault();
                  hideMsg();
                  const machineTaskCounts=[];
                  shiftCloseForm.querySelectorAll('#shift-count-rows tr[data-machine-id]').forEach(function(row){
                    const input=row.querySelector('.shift-done-count');
                    if(!input)return;
                    machineTaskCounts.push({
                      groupId:row.getAttribute('data-group-id'),
                      machineId:row.getAttribute('data-machine-id'),
                      taskId:row.getAttribute('data-task-id'),
                      completedCount:Number(input.value)
                    });
                  });
                  if(!machineTaskCounts.length){
                    showMsg('Нет строк для закрытия смены',true);
                    return;
                  }
                  const idleBlocks=[];
                  document.querySelectorAll('#idle-blocks .idle-row').forEach(function(row){
                    const machine=row.querySelector('.idle-machine');
                    const from=row.querySelector('.idle-from');
                    const to=row.querySelector('.idle-to');
                    if(!machine||!from||!to||!from.value||!to.value)return;
                    idleBlocks.push({
                      machineId:machine.value,
                      from:new Date(datetimeLocalToMs(from.value)).toISOString(),
                      to:new Date(datetimeLocalToMs(to.value)).toISOString(),
                      reason:'простой'
                    });
                  });
                  const payload={
                    closeAllPendingShifts:true,
                    shiftEnd:shiftCloseForm.dataset.shiftEnd||null,
                    machineTaskCounts:machineTaskCounts,
                    idleBlocks:idleBlocks
                  };
                  const btn=shiftCloseForm.querySelector('button[type=submit]');
                  if(btn)btn.disabled=true;
                  fetch('/shifts/close',{
                    method:'POST',
                    headers:{'Content-Type':'application/json'},
                    body:JSON.stringify(payload)
                  }).then(function(r){
                    return r.json().then(function(j){return {ok:r.ok,body:j};});
                  }).then(function(res){
                    if(btn)btn.disabled=false;
                    if(!res.ok){
                      showMsg(res.body&&res.body.error?res.body.error:'Ошибка закрытия смены',true);
                      return;
                    }
                    showMsg('Смены закрыты, перепланировано заказов: '+
                      (res.body.replannedOrderIds?res.body.replannedOrderIds.length:0),false);
                    setTimeout(function(){location.reload();},800);
                  }).catch(function(err){
                    if(btn)btn.disabled=false;
                    showMsg('Сеть: '+err,true);
                  });
                });
              }
            })();
            </script>
            """;

    private static final String FILTER_SCRIPT =
            """
            <script>
            (function(){
              const ordersBox=document.getElementById('filter-orders');
              const partsBox=document.getElementById('filter-parts');
              const resetBtn=document.getElementById('filter-reset');
              const panel=document.getElementById('machine-schedule-panel');
              const emptyMsg=document.getElementById('machine-filter-empty');
              const legendEl=document.getElementById('color-legend');
              let orderParts={};
              let partTitles={};
              let scheduleMeta={orders:[],partPriority:{}};
              try{orderParts=JSON.parse(document.getElementById('order-parts-json').textContent||'{}');}catch(e){}
              try{partTitles=JSON.parse(document.getElementById('part-titles-json').textContent||'{}');}catch(e){}
              try{scheduleMeta=JSON.parse(document.getElementById('schedule-meta-json').textContent||'{}');}catch(e){}

              const allOrderIds=(scheduleMeta.orders||[]).map(function(o){return o.id;});
              const zoomPresets=scheduleMeta.zoomPresets||{};
              const zoomLabelEl=document.getElementById('zoom-range-label');
              const zoomPrevBtn=document.getElementById('zoom-prev');
              const zoomNextBtn=document.getElementById('zoom-next');
              const zoomCustomStart=document.getElementById('zoom-custom-start');
              const zoomCustomEnd=document.getElementById('zoom-custom-end');
              const zoomCustomApply=document.getElementById('zoom-custom-apply');
              const TZ=scheduleMeta.timeZone||'Europe/Moscow';

              function partLabel(id){return partTitles[id]||id;}
              function partPriority(id){
                const p=scheduleMeta.partPriority||{};
                return p[id]!=null?Number(p[id]):Number((panel.querySelector('[data-part=\"'+id+'\"]')||{}).dataset?.partPriority||0);
              }
              function orderCreatedMs(id){
                const o=(scheduleMeta.orders||[]).find(function(x){return x.id===id;});
                return o?Number(o.createdAtMs):0;
              }

              function selectedValues(box){
                return Array.from(box.querySelectorAll('input[type=checkbox]:checked')).map(function(i){return i.value;});
              }

              function partKey(oid,pid){return oid+'::'+pid;}

              function rebuildPartFilters(){
                const prev=new Set(selectedValues(partsBox));
                const selectedOrders=selectedValues(ordersBox);
                const orderScope=selectedOrders.length?selectedOrders:allOrderIds;
                const showOrderInLabel=allOrderIds.length>1&&(selectedOrders.length===0||selectedOrders.length>1);
                partsBox.innerHTML='';
                orderScope.forEach(function(oid){
                  (orderParts[oid]||[]).forEach(function(pid){
                    const key=partKey(oid,pid);
                    const label=document.createElement('label');
                    label.className='filter-check';
                    const input=document.createElement('input');
                    input.type='checkbox';
                    input.name='filter-part';
                    input.value=key;
                    input.checked=prev.has(key);
                    label.appendChild(input);
                    label.appendChild(document.createTextNode(
                      showOrderInLabel?partLabel(pid)+' ('+oid+')':partLabel(pid)));
                    partsBox.appendChild(label);
                  });
                });
              }

              function barVisible(bar,orderIds,partKeys){
                if(orderIds.length&&!orderIds.includes(bar.dataset.order))return false;
                if(partKeys.length&&!partKeys.includes(partKey(bar.dataset.order,bar.dataset.part)))return false;
                return true;
              }

              function colorForRank(rank,count){
                if(count<=1)return 'hsl(0,72%,42%)';
                const hue=Math.round((rank/(count-1))*120);
                return 'hsl('+hue+',72%,42%)';
              }

              function uniquePartIdsFromKeys(partKeys){
                return partKeys.map(function(k){return k.split('::')[1];}).filter(function(p,i,a){
                  return p&&a.indexOf(p)===i;
                });
              }

              function maxRouteSequence(partId,visibleBars){
                const fromMeta=(scheduleMeta.partMaxSequence||{})[partId];
                if(fromMeta!=null)return Number(fromMeta);
                let max=0;
                visibleBars.forEach(function(bar){
                  const s=Number(bar.dataset.sequence);
                  if(!isNaN(s)&&s>=0)max=Math.max(max,s);
                });
                return max;
              }

              function resolveColorMode(orderIds){
                if(allOrderIds.length<=1)return 'part';
                if(orderIds.length===0)return 'order';
                if(orderIds.length>1)return 'order';
                return 'part';
              }

              function applyBarColors(visibleBars,mode,orderIds,partIds){
                if(!visibleBars.length)return;
                const uniqueParts=uniquePartIdsFromKeys(partIds);
                if(partIds.length>0&&uniqueParts.length===1){
                  const partId=uniqueParts[0];
                  const maxSeq=maxRouteSequence(partId,visibleBars);
                  const stepCount=maxSeq+1;
                  visibleBars.forEach(function(bar){
                    if(bar.classList.contains('setup-bar')){bar.style.background='#ef6c00';return;}
                    const seq=Number(bar.dataset.sequence);
                    if(isNaN(seq)||seq<0){bar.style.background='#1565c0';return;}
                    bar.style.background=colorForRank(seq,stepCount);
                  });
                  legendEl.textContent='Цвет по операциям маршрута («'+partLabel(partId)+'»): красный — первая, зелёный — последняя.';
                  return;
                }
                if(mode==='order'){
                  const scope=orderIds.length?orderIds.slice():allOrderIds.slice();
                  scope.sort(function(a,b){return orderCreatedMs(a)-orderCreatedMs(b);});
                  const rankById=new Map(scope.map(function(id,i){return [id,i];}));
                  visibleBars.forEach(function(bar){
                    if(bar.classList.contains('setup-bar')){bar.style.background='#ef6c00';return;}
                    const rank=rankById.get(bar.dataset.order)||0;
                    bar.style.background=colorForRank(rank,scope.length);
                  });
                  legendEl.textContent='Цвет по заказам: красный — раньше принят (выше в очереди), зелёный — позже.';
                }else{
                  const partSet=new Set(visibleBars.map(function(b){return b.dataset.part;}));
                  let scope=Array.from(partSet);
                  if(partIds.length){
                    scope=partIds.map(function(k){return k.split('::')[1];}).filter(function(p,i,a){
                      return p&&partSet.has(p)&&a.indexOf(p)===i;
                    });
                  }
                  scope.sort(function(a,b){return partPriority(b)-partPriority(a);});
                  const rankById=new Map(scope.map(function(id,i){return [id,i];}));
                  visibleBars.forEach(function(bar){
                    if(bar.classList.contains('setup-bar')){bar.style.background='#ef6c00';return;}
                    const rank=rankById.get(bar.dataset.part)||0;
                    bar.style.background=colorForRank(rank,scope.length);
                  });
                  legendEl.textContent='Цвет по деталям: красный — выше приоритет детали в заказе, зелёный — ниже.';
                }
              }

              function applyFilters(){
                const orderIds=selectedValues(ordersBox);
                const partIds=selectedValues(partsBox);
                const mode=resolveColorMode(orderIds);
                let anyVisible=false;
                const visibleBars=[];
                panel.querySelectorAll('.machine-row').forEach(function(row){
                  let rowHas=false;
                  row.querySelectorAll('.machine-bar').forEach(function(bar){
                    const show=barVisible(bar,orderIds,partIds)&&bar.dataset.outOfView!=='1';
                    bar.style.display=show?'':'none';
                    if(show){
                      rowHas=true;
                      if(bar.classList.contains('setup-bar')){bar.style.background='#ef6c00';}
                      else{visibleBars.push(bar);}
                    }
                  });
                  const filtering=orderIds.length||partIds.length;
                  row.classList.toggle('filtered-out',filtering&&!rowHas);
                  if(rowHas)anyVisible=true;
                });
                applyBarColors(visibleBars,mode,orderIds,partIds);
                updateMachinePartSpans(orderIds,partIds);
                const filtering=orderIds.length||partIds.length;
                emptyMsg.classList.toggle('hidden',!filtering||anyVisible);
              }

              function updateMachinePartSpans(orderIds,partIds){
                const filterParts=partIds.length>0;
                const filterOrders=orderIds.length>0;
                panel.querySelectorAll('.machine-row').forEach(function(row){
                  const allEl=row.querySelector('.machine-span-all');
                  let visiblePartCount=0;
                  row.querySelectorAll('.machine-span-part').forEach(function(li){
                    const key=partKey(li.dataset.order,li.dataset.part);
                    const matchOrder=!filterOrders||orderIds.includes(li.dataset.order);
                    const matchPart=!filterParts||partKeys.includes(key);
                    let hasBar=false;
                    row.querySelectorAll('.machine-bar').forEach(function(bar){
                      if(bar.style.display==='none')return;
                      if(bar.dataset.order===li.dataset.order&&bar.dataset.part===li.dataset.part){
                        hasBar=true;
                      }
                    });
                    const show=matchOrder&&matchPart&&hasBar;
                    li.classList.toggle('hidden',!show);
                    if(show)visiblePartCount++;
                  });
                  if(allEl){
                    allEl.classList.toggle('hidden',filterParts&&visiblePartCount>0);
                  }
                });
              }

              ordersBox.addEventListener('change',function(){
                rebuildPartFilters();
                applyFilters();
              });
              partsBox.addEventListener('change',applyFilters);
              resetBtn.addEventListener('click',function(){
                ordersBox.querySelectorAll('input').forEach(function(i){i.checked=false;});
                rebuildPartFilters();
                partsBox.querySelectorAll('input').forEach(function(i){i.checked=false;});
                applyFilters();
              });
              function panelViewport(){
                const panel=document.getElementById('machine-schedule-panel');
                return {
                  start:Number(panel.dataset.viewStartMs||panel.dataset.fullStartMs),
                  end:Number(panel.dataset.viewEndMs||panel.dataset.fullEndMs)
                };
              }

              function formatRangeLabel(startMs,endMs){
                const f=function(ms){
                  return new Date(ms).toLocaleString('ru-RU',{
                    timeZone:'Europe/Moscow',day:'2-digit',month:'2-digit',
                    hour:'2-digit',minute:'2-digit'});
                };
                return f(startMs)+' — '+f(endMs);
              }

              function axisStepMs(span){
                if(span<=6*3600000)return 3600000;
                if(span<=2*86400000)return 3*3600000;
                if(span<=10*86400000)return 86400000;
                return 2*86400000;
              }
              function formatTickLabel(ms,span,align){
                const d=new Date(ms);
                if(align==='mid'&&span>2*86400000){
                  return d.toLocaleDateString('ru-RU',{timeZone:'Europe/Moscow',day:'2-digit',month:'2-digit'});
                }
                return span<=86400000
                  ? d.toLocaleTimeString('ru-RU',{timeZone:'Europe/Moscow',hour:'2-digit',minute:'2-digit'})
                  : d.toLocaleString('ru-RU',{timeZone:'Europe/Moscow',day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit'});
              }
              function rebuildTimeAxis(tl){
                const block=tl.closest('.timeline-block');
                if(!block)return;
                const axis=block.querySelector('.time-axis');
                if(!axis)return;
                const startMs=Number(tl.dataset.startMs);
                const endMs=Number(tl.dataset.endMs);
                const span=endMs-startMs;
                if(!span)return;
                const step=axisStepMs(span);
                axis.innerHTML='';
                function addTick(ms,align){
                  const tick=document.createElement('span');
                  tick.className='tick tick-'+align;
                  if(align==='end'){
                    tick.style.right='0';
                  }else{
                    const left=Math.min(100,Math.max(0,((ms-startMs)*100)/span));
                    tick.style.left=left.toFixed(2)+'%';
                  }
                  tick.textContent=formatTickLabel(ms,span,align);
                  axis.appendChild(tick);
                }
                addTick(startMs,'start');
                let lastPct=0;
                for(let t=startMs+step;t<endMs&&axis.childElementCount<12;t+=step){
                  const pct=((t-startMs)*100)/span;
                  if(pct-lastPct>=12&&pct<=88){
                    addTick(t,'mid');
                    lastPct=pct;
                  }
                }
                addTick(endMs,'end');
              }

              function layoutTimeline(tl){
                const startMs=Number(tl.dataset.startMs);
                const endMs=Number(tl.dataset.endMs);
                const span=endMs-startMs;
                if(!span)return;
                const nowMs=Number(tl.dataset.nowMs);
                let nowEl=tl.querySelector('.now');
                if(nowMs>=startMs&&nowMs<=endMs){
                  if(!nowEl){
                    nowEl=document.createElement('div');
                    nowEl.className='now';
                    tl.appendChild(nowEl);
                  }
                  nowEl.style.left=Math.min(100,Math.max(0,((nowMs-startMs)*100)/span)).toFixed(2)+'%';
                  nowEl.style.display='';
                }else if(nowEl){nowEl.style.display='none';}
                tl.querySelectorAll('.bar').forEach(function(bar){
                  const bs=Number(bar.dataset.startMs);
                  const be=Number(bar.dataset.endMs);
                  const outOfView=be<=startMs||bs>=endMs;
                  bar.dataset.outOfView=outOfView?'1':'0';
                  if(outOfView){bar.style.display='none';return;}
                  bar.style.display='';
                  const visStart=Math.max(bs,startMs);
                  const visEnd=Math.min(be,endMs);
                  const left=((visStart-startMs)*100)/span;
                  const width=Math.max(0.15,((visEnd-visStart)*100)/span);
                  const clampedLeft=Math.min(99.85,left);
                  const clampedWidth=Math.min(99.85-clampedLeft,width);
                  bar.style.left=clampedLeft.toFixed(2)+'%';
                  bar.style.width=clampedWidth.toFixed(2)+'%';
                });
              }

              function msToLocalInput(ms){
                return new Date(ms).toLocaleString('sv-SE',{timeZone:TZ}).replace(' ','T').slice(0,16);
              }
              function localInputToMs(s){
                if(!s)return NaN;
                return new Date(s+':00+03:00').getTime();
              }
              function syncCustomRangeInputs(){
                const vp=panelViewport();
                if(zoomCustomStart)zoomCustomStart.value=msToLocalInput(vp.start);
                if(zoomCustomEnd)zoomCustomEnd.value=msToLocalInput(vp.end);
              }
              function clearPresetButtonsActive(){
                document.querySelectorAll('.btn-zoom[data-zoom]').forEach(function(b){
                  b.classList.remove('active');
                });
              }
              function updateNavButtons(){
                const preset=panel.dataset.zoomPreset||'';
                const disabled=preset==='row'||preset==='all';
                if(zoomPrevBtn)zoomPrevBtn.disabled=disabled;
                if(zoomNextBtn)zoomNextBtn.disabled=disabled;
              }
              function viewportSpanMs(){
                const vp=panelViewport();
                return Math.max(60000,vp.end-vp.start);
              }
              function setSharedViewport(startMs,endMs,options){
                options=options||{};
                if(!panel)return;
                const fullStart=Number(panel.dataset.fullStartMs);
                const fullEnd=Number(panel.dataset.fullEndMs);
                let start=Math.max(fullStart,startMs);
                let end=Math.min(fullEnd,endMs);
                if(end<=start)end=Math.min(start+3600000,fullEnd);
                panel.dataset.viewStartMs=start;
                panel.dataset.viewEndMs=end;
                panel.querySelectorAll('.machine-timeline').forEach(function(tl){
                  tl.dataset.startMs=start;
                  tl.dataset.endMs=end;
                  layoutTimeline(tl);
                  rebuildTimeAxis(tl);
                });
                if(zoomLabelEl)zoomLabelEl.textContent=formatRangeLabel(start,end);
                if(!options.skipCustomSync)syncCustomRangeInputs();
                updateNavButtons();
              }
              function panViewport(direction){
                const preset=panel.dataset.zoomPreset||'';
                if(preset==='row'||preset==='all')return;
                const fullStart=Number(panel.dataset.fullStartMs);
                const fullEnd=Number(panel.dataset.fullEndMs);
                const vp=panelViewport();
                const span=viewportSpanMs();
                let start=vp.start+direction*span;
                let end=start+span;
                if(start<fullStart){
                  start=fullStart;
                  end=Math.min(fullStart+span,fullEnd);
                }
                if(end>fullEnd){
                  end=fullEnd;
                  start=Math.max(fullEnd-span,fullStart);
                }
                clearPresetButtonsActive();
                panel.dataset.zoomPreset='pan';
                setSharedViewport(start,end);
                drawShiftBackgrounds();
                applyFilters();
              }
              function applyCustomRange(){
                const startMs=localInputToMs(zoomCustomStart&&zoomCustomStart.value);
                const endMs=localInputToMs(zoomCustomEnd&&zoomCustomEnd.value);
                if(!Number.isFinite(startMs)||!Number.isFinite(endMs)||endMs<=startMs){
                  alert('Укажите корректный интервал: конец позже начала.');
                  return;
                }
                clearPresetButtonsActive();
                if(zoomCustomApply)zoomCustomApply.classList.add('active');
                panel.dataset.zoomPreset='custom';
                setSharedViewport(startMs,endMs,{skipCustomSync:true});
                drawShiftBackgrounds();
                applyFilters();
              }

              function applyZoom(preset){
                if(!panel)return;
                panel.dataset.zoomPreset=preset;
                document.querySelectorAll('.btn-zoom[data-zoom]').forEach(function(b){
                  b.classList.toggle('active',b.dataset.zoom===preset);
                });
                if(zoomCustomApply)zoomCustomApply.classList.remove('active');
                if(preset==='row'){
                  panel.querySelectorAll('.machine-timeline').forEach(function(tl){
                    const rs=Number(tl.dataset.rowStartMs);
                    const re=Number(tl.dataset.rowEndMs);
                    if(!rs||!re||rs>=re)return;
                    tl.dataset.startMs=rs;
                    tl.dataset.endMs=re;
                    layoutTimeline(tl);
                    rebuildTimeAxis(tl);
                  });
                  if(zoomLabelEl)zoomLabelEl.textContent='Свой интервал у каждого станка';
                  drawShiftBackgrounds();
                  applyFilters();
                  return;
                }
                if(preset==='all'){
                  setSharedViewport(Number(panel.dataset.fullStartMs),Number(panel.dataset.fullEndMs));
                }else if(zoomPresets[preset]){
                  const z=zoomPresets[preset];
                  setSharedViewport(z[0],z[1]);
                }
                drawShiftBackgrounds();
                applyFilters();
                updateNavButtons();
              }

              document.querySelectorAll('.btn-zoom[data-zoom]').forEach(function(btn){
                btn.addEventListener('click',function(){applyZoom(btn.dataset.zoom);});
              });
              if(zoomPrevBtn)zoomPrevBtn.addEventListener('click',function(){panViewport(-1);});
              if(zoomNextBtn)zoomNextBtn.addEventListener('click',function(){panViewport(1);});
              if(zoomCustomApply)zoomCustomApply.addEventListener('click',applyCustomRange);

              function drawShiftBackgrounds(){
                let groups=[];
                try{groups=JSON.parse(document.getElementById('machine-groups-json').textContent||'[]');}catch(e){}
                const byId={};
                groups.forEach(function(g){byId[g.groupId]=g;});
                const dayCodes=['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'];
                document.querySelectorAll('.timeline[data-group]').forEach(function(tl){
                  const startMs=Number(tl.dataset.startMs);
                  const endMs=Number(tl.dataset.endMs);
                  const span=endMs-startMs;
                  if(!span||span<=0)return;
                  tl.querySelectorAll('.shift-closed').forEach(function(z){z.remove();});
                  const row=tl.closest('.machine-row');
                  const gid=tl.dataset.group||(row?row.dataset.group:null);
                  const group=byId[gid];
                  if(!group||!group.workWindows||!group.workWindows.length)return;
                  const windowsByDay={};
                  group.workWindows.forEach(function(w){
                    (windowsByDay[w.dayCode]=windowsByDay[w.dayCode]||[]).push(w);
                  });
                  const insertAt=tl.querySelector('.bar,.now')||null;
                  for(let t=startMs;t<endMs;t+=86400000){
                    const d=new Date(t);
                    const dayKey=dayCodes[(d.getDay()+6)%7];
                    const dayStart=new Date(d.getFullYear(),d.getMonth(),d.getDate()).getTime();
                    const wins=windowsByDay[dayKey]||[];
                    function addClosed(fromMs,toMs){
                      if(toMs<=startMs||fromMs>=endMs)return;
                      const left=Math.max(0,((Math.max(fromMs,startMs)-startMs)*100)/span);
                      const right=Math.min(100,((Math.min(toMs,endMs)-startMs)*100)/span);
                      const width=right-left;
                      if(width<=0.05)return;
                      const z=document.createElement('div');
                      z.className='shift-closed';
                      z.style.left=left.toFixed(2)+'%';
                      z.style.width=width.toFixed(2)+'%';
                      if(insertAt)tl.insertBefore(z,insertAt);
                      else tl.appendChild(z);
                    }
                    if(!wins.length){
                      addClosed(dayStart,dayStart+86400000);
                      continue;
                    }
                    wins.sort(function(a,b){return a.start.localeCompare(b.start);});
                    let cursor=dayStart;
                    wins.forEach(function(w){
                      const ws=dayStart+parseHm(w.start);
                      const we=dayStart+parseHm(w.end);
                      if(ws>cursor)addClosed(cursor,ws);
                      cursor=Math.max(cursor,we);
                    });
                    if(cursor<dayStart+86400000)addClosed(cursor,dayStart+86400000);
                  }
                });
              }
              function parseHm(hm){
                const p=hm.split(':');
                return (Number(p[0])*60+Number(p[1]||0))*60000;
              }

              function refreshOrderTimelines(){
                document.querySelectorAll('.timeline:not(.machine-timeline)').forEach(function(tl){
                  layoutTimeline(tl);
                  rebuildTimeAxis(tl);
                });
                drawShiftBackgrounds();
              }

              rebuildPartFilters();
              applyFilters();
              const initialPreset=panel.dataset.zoomPreset||'day';
              applyZoom(initialPreset);
              refreshOrderTimelines();
            })();
            </script>
            """;

    private static String buildPartTitlesForScript(ScheduleView view) {
        Map<String, String> titles = new LinkedHashMap<>();
        for (OrderScheduleView order : view.orders()) {
            for (PartScheduleView part : order.parts()) {
                titles.putIfAbsent(part.partId(), DomainLabels.partTitle(part.partId()));
            }
        }
        for (PartCatalogView part : view.partCatalog()) {
            titles.putIfAbsent(part.partId(), DomainLabels.partTitle(part.partId()));
        }
        return GSON.toJson(titles);
    }

    private static final String CSS =
            """
            *{box-sizing:border-box}body{font-family:system-ui,sans-serif;margin:0;padding:24px;\
            background:#f4f6f8;color:#1a1a1a}header{margin-bottom:32px}h1{margin:0 0 8px}\
            .meta{color:#555;margin:0}.cards{display:flex;flex-wrap:wrap;gap:12px;margin-top:16px}\
            .card{background:#fff;border-radius:8px;padding:12px 16px;min-width:160px;\
            box-shadow:0 1px 3px rgba(0,0,0,.08)}.card span{display:block;font-size:12px;color:#666}\
            .tag{background:#e3f2fd;color:#1565c0;font-size:11px;padding:2px 6px;border-radius:4px}\
            section{background:#fff;border-radius:8px;padding:20px;margin-bottom:20px;\
            box-shadow:0 1px 3px rgba(0,0,0,.08)}table{width:100%;border-collapse:collapse;font-size:14px}\
            th,td{border-bottom:1px solid #eee;padding:8px;text-align:left}th{background:#fafafa}\
            tr.warn td{background:#fff3e0}tr.busy td{background:#e8f5e9}\
            .order{border-top:1px solid #eee;padding-top:16px;margin-top:16px}\
            .order:first-child{border-top:none;padding-top:0;margin-top:0}\
            .part{margin:12px 0 20px}.order-parts{font-size:13px;color:#444;margin:4px 0 12px}\
            .qty{color:#1565c0;font-weight:600;font-size:13px}\
            .prio{color:#555;font-weight:normal;font-size:13px}\
            .tasks .dur{white-space:nowrap;color:#333;font-variant-numeric:tabular-nums}\
            .slack{color:#2e7d32;font-size:12px}.id{color:#666;font-size:12px}\
            .timeline-block{margin:6px 0 14px;overflow:hidden;border-radius:6px}\
            .timeline{position:relative;height:40px;background:#eef1f4;\
            border-radius:6px 6px 0 0;overflow:hidden;min-width:0}\
            .time-axis{position:relative;height:26px;margin:0 0 4px;padding:0 6px;\
            border:1px solid #e0e4e8;border-top:none;border-radius:0 0 6px 6px;background:#fafbfc;\
            overflow:hidden}\
            .time-axis .tick{position:absolute;top:3px;font-size:10px;color:#666;white-space:nowrap}\
            .time-axis .tick-mid{transform:translateX(-50%);text-align:center}\
            .time-axis .tick-start{transform:none;text-align:left;max-width:46%;overflow:hidden;text-overflow:ellipsis}\
            .time-axis .tick-end{left:auto!important;right:4px;transform:none;text-align:right;\
            max-width:46%;overflow:hidden;text-overflow:ellipsis}\
            .time-axis .tick::before{content:'';position:absolute;top:-7px;width:1px;height:5px;background:#bbb}\
            .time-axis .tick-mid::before{left:50%}\
            .time-axis .tick-start::before{left:0}\
            .time-axis .tick-end::before{right:0;left:auto}\
            .zoom-bar,.zoom-custom{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin:12px 0 8px;\
            padding:10px 12px;background:#f0f4f8;border-radius:8px;border:1px solid #e2e6ea}\
            .zoom-custom{margin-top:0;margin-bottom:12px}\
            .zoom-label{font-size:13px;color:#444;font-weight:600}\
            .btn-zoom,.btn-zoom-nav{padding:6px 12px;border:1px solid #bbb;border-radius:6px;background:#fff;\
            font-size:13px;cursor:pointer}.btn-zoom:hover,.btn-zoom-nav:hover:not(:disabled){background:#e8eef5}\
            .btn-zoom.active,#zoom-custom-apply.active{background:#1565c0;color:#fff;border-color:#1565c0}\
            .btn-zoom-nav{min-width:36px;padding:6px 10px;font-size:14px}\
            .btn-zoom-nav:disabled{opacity:0.4;cursor:not-allowed}\
            .zoom-nav{display:inline-flex;gap:4px;margin-left:4px}\
            .zoom-range{font-size:12px;color:#666;margin-left:8px;flex:1 1 200px}\
            .zoom-dt{font-size:13px;color:#444;display:inline-flex;align-items:center;gap:6px}\
            .zoom-dt input{padding:5px 8px;border:1px solid #ccc;border-radius:6px;font-size:13px}\
            .bar{position:absolute;top:6px;height:24px;background:#1565c0;color:#fff;\
            font-size:10px;line-height:24px;padding:0 4px;border-radius:4px;white-space:nowrap;\
            overflow:hidden;text-overflow:ellipsis;cursor:help;z-index:1}\
            .machine-bar{background:#1565c0}\
            .setup-bar{background:#ef6c00!important}\
            .shift-closed{position:absolute;top:0;bottom:0;background:repeating-linear-gradient(\
            -45deg,#d0d4d8,#d0d4d8 6px,#e8eaed 6px,#e8eaed 12px);opacity:0.55;z-index:0;pointer-events:none}\
            .bar:hover,.machine-bar:hover{filter:brightness(0.88);z-index:3}\
            .group-tag{font-size:11px;color:#666}\
            .shift-tag{font-size:11px;color:#2e7d32;background:#e8f5e9;padding:1px 6px;border-radius:4px}\
            .page-nav{display:flex;flex-wrap:wrap;gap:12px 20px;margin-top:16px;padding-top:12px;\
            border-top:1px solid #e0e4e8}\
            .page-nav a{font-size:14px;color:#1565c0;text-decoration:none;font-weight:500}\
            .page-nav a:hover{text-decoration:underline}\
            .factory-overview{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:12px;\
            margin-top:12px}\
            .factory-overview-group{background:#fff;border:1px solid #e2e6ea;border-radius:8px;padding:12px 14px;\
            font-size:13px}\
            .factory-overview-shifts{margin:6px 0 8px;color:#555}\
            .factory-overview-shifts .shift-today{font-weight:600;color:#1565c0}\
            .factory-overview-machines{margin:0;padding-left:18px;color:#444}\
            .factory-overview-machines li{margin:4px 0}\
            .shift-summary{background:#f0f4f8;border:1px solid #e2e6ea;border-radius:8px;padding:10px 14px;\
            margin:0 0 12px;font-size:13px;line-height:1.5}\
            .shift-summary-label{font-weight:600;color:#333}\
            .shift-summary-item{display:inline-block;margin-right:12px;white-space:nowrap}\
            .legend-setup{color:#ef6c00;font-weight:600}\
            .now{position:absolute;top:0;bottom:0;width:2px;background:#d32f2f;z-index:4;pointer-events:none}\
            .machine-row{display:grid;grid-template-columns:minmax(180px,220px) 1fr;gap:12px;\
            align-items:center;margin-bottom:14px;padding-bottom:14px;border-bottom:1px solid #eee}\
            .machine-row.filtered-out{display:none}\
            .machine-row:last-child{border-bottom:none;margin-bottom:0;padding-bottom:0}\
            .machine-label{font-size:13px;line-height:1.35}\
            .machine-calendar-spans{margin-top:8px;font-size:11px;color:#555;line-height:1.4}\
            .machine-span-all{color:#444}\
            .machine-span-by-part{margin:4px 0 0;padding-left:16px;list-style:disc}\
            .machine-span-part.hidden{display:none}\
            .machine-span-part .part-name{color:#333}\
            .machine-span-part .part-range{white-space:nowrap}\
            .tasks{font-size:13px}.hint{color:#666;font-size:13px}\
            .empty{color:#888}.hidden{display:none}\
            .filters{display:grid;grid-template-columns:1fr 1fr auto;gap:16px;align-items:start;\
            margin:16px 0 12px;padding:14px;background:#f8f9fb;border-radius:8px;border:1px solid #e8eaed}\
            @media(max-width:900px){.filters{grid-template-columns:1fr}}\
            .filter-field{border:none;margin:0;padding:0;min-width:0}\
            .filter-field legend{font-size:13px;font-weight:600;color:#333;padding:0 4px}\
            .filter-hint{font-weight:normal;color:#888;font-size:12px}\
            .filter-checkboxes{display:flex;flex-direction:column;gap:6px;max-height:140px;\
            overflow-y:auto;margin-top:8px;padding:4px}\
            .filter-check{display:flex;align-items:center;gap:8px;font-size:14px;cursor:pointer}\
            .filter-check input{margin:0}\
            .filter-actions{display:flex;align-items:flex-end;padding-bottom:4px}\
            .btn-reset{padding:8px 14px;border:1px solid #ccc;border-radius:6px;background:#fff;\
            font-size:14px;cursor:pointer}.btn-reset:hover{background:#f5f5f5}\
            .color-legend{grid-column:1/-1;font-size:12px;color:#555;margin-top:4px;padding-top:10px;\
            border-top:1px solid #e8eaed}\
            .controls-panel h2{margin-top:0}\
            .controls-grid{display:grid;grid-template-columns:1fr 1fr;gap:20px;margin-top:12px}\
            @media(max-width:900px){.controls-grid{grid-template-columns:1fr}}\
            .control-card{border:1px solid #e8eaed;border-radius:8px;padding:16px;background:#fafbfc}\
            .control-card h3{margin:0 0 12px;font-size:15px}\
            .control-field{display:flex;flex-direction:column;gap:6px;font-size:13px;margin-bottom:12px}\
            .control-field input,.order-part-select,.order-qty-input{padding:8px 10px;border:1px solid #ccc;\
            border-radius:6px;font-size:14px;max-width:100%}\
            .order-lines-field{border:none;margin:0 0 12px;padding:0}\
            .order-lines-field legend{font-size:13px;font-weight:600;padding:0 4px}\
            .order-lines{display:flex;flex-direction:column;gap:8px;margin:8px 0}\
            .order-line{display:flex;flex-wrap:wrap;gap:8px;align-items:flex-end}\
            .order-line-part{flex:1 1 200px}\
            .order-line-qty{flex:0 0 100px}\
            .btn-primary,.btn-secondary{padding:8px 14px;border-radius:6px;font-size:14px;cursor:pointer;\
            border:1px solid #ccc}\
            .btn-primary{background:#1565c0;color:#fff;border-color:#1565c0}\
            .btn-primary:hover{background:#0d47a1}\
            .btn-primary:disabled{opacity:0.5;cursor:not-allowed}\
            .shift-stale-banner{background:#fff3e0;border:1px solid #ffb74d;color:#e65100;\
            padding:12px 14px;border-radius:8px;margin-bottom:12px;font-size:14px}\
            .shift-close-title{margin:8px 0 12px;font-size:16px}\
            .shift-idle-details{margin:16px 0}\
            .shift-facts td.num{text-align:center}\
            .shift-done-count{width:4em}\
            body.schedule-stale header{border-bottom:3px solid #e65100}\
            .btn-secondary{background:#fff}\
            .btn-secondary:hover{background:#f0f4f8}\
            .btn-icon{width:36px;height:36px;padding:0;border:1px solid #ccc;border-radius:6px;\
            background:#fff;font-size:20px;line-height:1;cursor:pointer;color:#666}\
            .btn-icon:hover{background:#ffebee;color:#c62828;border-color:#ef9a9a}\
            .time-advance-btns{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:12px}\
            .sim-now{font-size:13px;margin:0 0 12px}\
            .controls-message{padding:10px 12px;border-radius:6px;font-size:13px;margin-bottom:12px}\
            .controls-message.ok{background:#e8f5e9;color:#2e7d32;border:1px solid #c8e6c9}\
            .controls-message.error{background:#ffebee;color:#c62828;border:1px solid #ffcdd2}
            """;
}
