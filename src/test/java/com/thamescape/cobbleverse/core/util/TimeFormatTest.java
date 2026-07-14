package com.thamescape.cobbleverse.core.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeFormatTest {

    @Test
    void secondsUnderAMinute() {
        assertEquals("45s", TimeFormat.playtime(45));
    }

    @Test
    void minutesOnly() {
        assertEquals("5m", TimeFormat.playtime(5 * 60));
    }

    @Test
    void hoursAndMinutes() {
        assertEquals("2h 30m", TimeFormat.playtime(2 * 3600 + 30 * 60));
    }

    @Test
    void daysHoursMinutes() {
        assertEquals("1d 2h 3m", TimeFormat.playtime(86_400 + 2 * 3600 + 3 * 60));
    }

    @Test
    void timestampFormatsInZone() {
        ZoneId utc = ZoneId.of("UTC");
        long epoch = LocalDateTime.of(2026, 7, 14, 20, 15).atZone(utc).toInstant().toEpochMilli();
        assertEquals("2026-07-14 20:15", TimeFormat.timestamp(epoch, utc));
    }
}
