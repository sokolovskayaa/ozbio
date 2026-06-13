package scheduler.engine;

import java.time.ZoneId;

public final class FactoryZone {
    public static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    private FactoryZone() {}
}
