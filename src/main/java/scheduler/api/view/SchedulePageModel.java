package scheduler.api.view;

import java.time.Instant;

/** Данные для шаблона schedule.html. */
public record SchedulePageModel(
        String generatedAt,
        String factoryStartedAt,
        String currentTime,
        boolean simulationEnabled,
        int orderCount,
        int machineCount,
        String pageNav,
        String factoryOverview,
        String controlsPanel,
        String ordersSection,
        String machinesSection,
        String machineGroupsSection,
        String machineScheduleSection,
        String catalogSection,
        String orderPartsJson,
        String partTitlesJson,
        String scheduleMetaJson,
        String partCatalogJson) {}
