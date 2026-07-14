package com.thamescape.cobbleverse.core.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Small formatting helpers for playtime durations and timestamps. */
public final class TimeFormat {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private TimeFormat() {
    }

    /** Formats a duration in seconds as a compact {@code "1d 2h 3m"} (omitting zero leading units). */
    public static String playtime(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        long days = seconds / 86_400;
        long hours = (seconds % 86_400) / 3_600;
        long minutes = (seconds % 3_600) / 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (days > 0 || hours > 0) {
            sb.append(hours).append("h ");
        }
        sb.append(minutes).append("m");
        return sb.toString();
    }

    /** Formats an epoch-millis timestamp in the given zone, e.g. {@code 2026-07-14 20:15}. */
    public static String timestamp(long epochMillis, ZoneId zone) {
        return STAMP.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }
}
