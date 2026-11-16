package com.fiap.workshop.management.domain.model.shared;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Time elapsed between two moments the service order recorded.
 *
 * <p>The order stores its moments as {@link LocalDateTime}, taken from the
 * system clock, so they mean "wall-clock time in the system zone". Subtracting
 * two of them directly ignores a change of the clock between them (daylight
 * saving time), so the difference is computed on zone-aware values instead.
 */
public final class ElapsedTime {

    private ElapsedTime() {
    }

    /** Whole minutes from {@code start} to {@code end}, in the system zone. */
    public static long minutesBetween(LocalDateTime start, LocalDateTime end) {
        return minutesBetween(start, end, ZoneId.systemDefault());
    }

    public static long minutesBetween(LocalDateTime start, LocalDateTime end, ZoneId zone) {
        return Duration.between(start.atZone(zone), end.atZone(zone)).toMinutes();
    }
}
