package scheduler.store;

import java.time.Duration;
import java.util.List;
import scheduler.model.machine.Capability;
import scheduler.model.order.Task;
import scheduler.store.core.CatalogSeeder;
import scheduler.store.core.PartDefinition;

/** Seeds catalog and test part definitions for engine tests. */
public final class TestFactoryFixtures {
    private TestFactoryFixtures() {}

    public static void seedGreedySchedulerCatalog(InMemoryPlanningRepository repo) {
        repo.putPartDefinition(
                "P1",
                new PartDefinition(
                        10,
                        List.of(
                                new Task("T1", 0, Duration.ofMinutes(60), Capability.MILLING),
                                new Task("T2", 1, Duration.ofMinutes(30), Capability.TURNING))));
        repo.putPartDefinition(
                "P-high",
                new PartDefinition(10, List.of(new Task("T1", 0, Duration.ofMinutes(100), Capability.MILLING))));
        repo.putPartDefinition(
                "P-low",
                new PartDefinition(5, List.of(new Task("T2", 0, Duration.ofMinutes(40), Capability.TURNING))));
        repo.putPartDefinition(
                "P-slow",
                new PartDefinition(10, List.of(new Task("T1", 0, Duration.ofMinutes(80), Capability.MILLING))));
        repo.putPartDefinition(
                "P-fast",
                new PartDefinition(5, List.of(new Task("T2", 0, Duration.ofMinutes(20), Capability.TURNING))));
        repo.putPartDefinition(
                "P2",
                new PartDefinition(5, List.of(new Task("T2", 0, Duration.ofMinutes(30), Capability.MILLING))));
        repo.putPartDefinition(
                "P-overlap",
                new PartDefinition(
                        8,
                        List.of(
                                new Task("T-mill", 0, Duration.ofMinutes(20), Capability.MILLING),
                                new Task("T-turn", 1, Duration.ofMinutes(10), Capability.TURNING))));
    }

    public static void seedDemoCatalog(InMemoryPlanningRepository repo) {
        CatalogSeeder.seedPartDefinitions(repo::putPartDefinition);
    }
}
