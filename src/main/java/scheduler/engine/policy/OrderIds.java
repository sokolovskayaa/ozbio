package scheduler.engine.policy;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Номера заказов вида {@code З-2026-0142}. */
public final class OrderIds {
    private static final Pattern FORMAT = Pattern.compile("З-(\\d{4})-(\\d+)");

    private OrderIds() {}

    public static String nextOrderId(Instant createdAt, ZoneId zone, Collection<String> existingOrderIds) {
        int year = createdAt.atZone(zone).getYear();
        int maxSeq = 0;
        for (String id : existingOrderIds) {
            if (id == null) {
                continue;
            }
            Matcher matcher = FORMAT.matcher(id.trim());
            if (!matcher.matches()) {
                continue;
            }
            if (Integer.parseInt(matcher.group(1)) != year) {
                continue;
            }
            maxSeq = Math.max(maxSeq, Integer.parseInt(matcher.group(2)));
        }
        return String.format(Locale.ROOT, "З-%d-%04d", year, maxSeq + 1);
    }
}
