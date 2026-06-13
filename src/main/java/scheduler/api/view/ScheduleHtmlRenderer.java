package scheduler.api.view;

import com.google.gson.Gson;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import scheduler.engine.policy.FactoryZone;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import scheduler.model.machine.Capability;
import scheduler.model.schedule.SetupIntervals;

public final class ScheduleHtmlRenderer {
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(FactoryZone.ZONE);
    private static final Gson GSON = new Gson();

    private ScheduleHtmlRenderer() {}

    public static String render(ScheduleView view) {
        return SchedulePageRenderer.renderStandalone(view);
    }

    static SchedulePageModel assemblePage(ScheduleView view) {
        TimelineBounds bounds = computeTimelineBounds(view);
        Instant now = view.clock().currentTime();

        StringBuilder pageNav = new StringBuilder();
        appendPageNav(pageNav);

        StringBuilder factoryOverview = new StringBuilder();
        appendFactoryOverviewStrip(factoryOverview, view, now);

        StringBuilder controlsPanel = new StringBuilder();
        appendControlsPanel(controlsPanel, view, now);

        StringBuilder ordersSection = new StringBuilder();
        appendOrdersSection(ordersSection, view, now);

        StringBuilder machinesSection = new StringBuilder();
        appendMachinesParkSection(machinesSection, view);

        StringBuilder machineGroupsSection = new StringBuilder();
        appendMachineGroupsSection(machineGroupsSection, view);

        StringBuilder machineScheduleSection = new StringBuilder();
        appendMachineScheduleSection(machineScheduleSection, view, bounds, now);

        StringBuilder catalogSection = new StringBuilder();
        appendCatalogSection(catalogSection, view);

        return new SchedulePageModel(
                formatInstant(view.generatedAt()),
                formatInstant(view.factoryStartedAt()),
                formatInstant(now),
                view.clock().simulationEnabled(),
                view.orders().size(),
                view.machines().size(),
                pageNav.toString(),
                factoryOverview.toString(),
                controlsPanel.toString(),
                ordersSection.toString(),
                machinesSection.toString(),
                machineGroupsSection.toString(),
                machineScheduleSection.toString(),
                catalogSection.toString(),
                safeJsonForScript(GSON.toJson(partsByOrder(view))),
                safeJsonForScript(buildPartTitlesForScript(view)),
                safeJsonForScript(buildScheduleMeta(view, bounds, now)),
                safeJsonForScript(buildPartCatalogForScript(view)));
    }

    static String formatInstant(Instant instant) {
        return FMT.format(instant);
    }

    static Map<String, List<String>> partsByOrder(ScheduleView view) {
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
        html.append("<p class=\"hint\">Номер заказа присваивается автоматически (З-год-номер). ")
                .append("Планирование от текущего времени (")
                .append(esc(FMT.format(now)))
                .append(").</p>");
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

    private static void appendOrdersSection(StringBuilder html, ScheduleView view, Instant now) {
        html.append("<section id=\"orders\"><div class=\"section-head\">");
        html.append("<h2>Заказы</h2>");
        html.append("<a href=\"#controls\" class=\"btn-primary btn-add-order-jump\">+ Добавить заказ</a>");
        html.append("</div>");
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
            html.append("<p class=\"empty\">Заказов пока нет. ")
                    .append("<a href=\"#controls\" class=\"btn-add-order-jump\">Добавить заказ</a></p>");
        }
        html.append("</section>");
    }

    private static void appendPageNav(StringBuilder html) {
        html.append("<nav class=\"page-nav\" aria-label=\"Разделы плана\">");
        html.append("<a href=\"#machines\">Парк станков</a>");
        html.append("<a href=\"#machine-groups\">Группы станков</a>");
        html.append("<a href=\"#machine-schedule\">Расписание по станкам</a>");
        html.append("<a href=\"#orders\">Заказы</a>");
        html.append("</nav>");
    }

    private static void appendFactoryOverviewStrip(StringBuilder html, ScheduleView view, Instant now) {
        html.append("<div class=\"factory-overview\">");
        for (MachineGroupView group : view.machineGroups()) {
            html.append("<div class=\"factory-overview-group\">");
            html.append("<strong>").append(esc(group.name())).append("</strong> ");
            html.append("<code class=\"id\">").append(esc(group.groupId())).append("</code>");
            html.append("<div class=\"factory-overview-meta\">переналадка ")
                    .append(esc(formatDuration(group.setupDuration())))
                    .append(" · круглосуточно</div><ul class=\"factory-overview-machines\">");
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
        html.append("<section id=\"machine-groups\"><h2>Группы станков</h2>");
        html.append("<p class=\"hint\">Переналадка добавляется автоматически при смене типа операции на станке.</p>");
        for (MachineGroupView group : view.machineGroups()) {
            html.append("<h3>")
                    .append(esc(group.name()))
                    .append(" <code class=\"id\">")
                    .append(esc(group.groupId()))
                    .append("</code></h3>");
            html.append("<p>Переналадка: <strong>")
                    .append(esc(formatDuration(group.setupDuration())))
                    .append("</strong> · круглосуточно</p>");
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
        html.append("<p class=\"hint\">Каждая строка — станок; ")
                .append("<span class=\"legend-setup\">оранжевый</span> — переналадка. ")
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
        presets.put("shift", boundsToMs(presetDay(now)));
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
            int qty = quantityByOrderPart.getOrDefault(orderPartKey(a.orderId(), a.partId()), 1);
            PartCalendarSpan partSpan = partSpansOnMachine.get(orderPartKey(a.orderId(), a.partId()));
            String tooltip = assignmentTooltip(a, qty, partSpan);
            boolean setup = SetupIntervals.isSetup(a.taskId());
            for (CalendarSegment segment : segmentsForAssignment(a)) {
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

    private static Map<String, String> machineToGroupId(ScheduleView view) {
        Map<String, String> map = new LinkedHashMap<>();
        for (MachineView m : view.machines()) {
            map.put(m.machineId(), m.groupId());
        }
        return map;
    }

    private static List<CalendarSegment> segmentsForAssignment(AssignmentView a) {
        Instant start = a.actualStart() != null ? a.actualStart() : a.plannedStart();
        Instant end = a.actualEnd() != null ? a.actualEnd() : a.plannedEnd();
        if (!end.isBefore(start)) {
            return List.of(new CalendarSegment(start, end));
        }
        return List.of(new CalendarSegment(start, start));
    }

    private record CalendarSegment(Instant start, Instant end) {}

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
        Instant start = assignments.stream()
                .flatMap(a -> segmentsForAssignment(a).stream())
                .map(CalendarSegment::start)
                .min(Comparator.naturalOrder())
                .orElse(now);
        Instant end = assignments.stream()
                .flatMap(a -> segmentsForAssignment(a).stream())
                .map(CalendarSegment::end)
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
        return presetDay(now);
    }

    private static String defaultZoomPresetId(TimelineBounds full) {
        if (full.spanSeconds() <= 86400) {
            return "all";
        }
        return "day";
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
    static String safeJsonForScript(String json) {
        if (json == null) {
            return "";
        }
        return json.replace("</", "<\\/");
    }

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
}
