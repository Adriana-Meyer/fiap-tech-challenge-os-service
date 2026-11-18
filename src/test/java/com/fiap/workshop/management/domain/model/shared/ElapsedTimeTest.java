package com.fiap.workshop.management.domain.model.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("ElapsedTime")
class ElapsedTimeTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    @Test
    @DisplayName("should count whole minutes between two moments")
    void shouldCountWholeMinutesBetweenTwoMoments() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 15, 10, 0, 0);

        assertEquals(90, ElapsedTime.minutesBetween(start, start.plusMinutes(90), UTC));
    }

    @Test
    @DisplayName("should drop the seconds that do not fill a minute")
    void shouldDropTheSecondsThatDoNotFillAMinute() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 15, 10, 0, 0);

        assertEquals(1, ElapsedTime.minutesBetween(start, start.plusSeconds(119), UTC));
    }

    @Test
    @DisplayName("should be zero when both moments are the same")
    void shouldBeZeroWhenBothMomentsAreTheSame() {
        LocalDateTime moment = LocalDateTime.of(2026, 11, 15, 10, 0, 0);

        assertEquals(0, ElapsedTime.minutesBetween(moment, moment, UTC));
    }

    @Test
    @DisplayName("should be negative when the end comes before the start")
    void shouldBeNegativeWhenTheEndComesBeforeTheStart() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 15, 10, 0, 0);

        assertEquals(-30, ElapsedTime.minutesBetween(start, start.minusMinutes(30), UTC));
    }

    @Test
    @DisplayName("should account for the clock going forward at the start of daylight saving time")
    void shouldAccountForTheClockGoingForwardAtTheStartOfDaylightSavingTime() {
        // 2026-03-08, New York: at 02:00 the clocks jump to 03:00, so 01:30 -> 03:30 is one hour, not two.
        LocalDateTime start = LocalDateTime.of(2026, 3, 8, 1, 30);
        LocalDateTime end = LocalDateTime.of(2026, 3, 8, 3, 30);

        assertEquals(60, ElapsedTime.minutesBetween(start, end, NEW_YORK));
        assertEquals(120, ElapsedTime.minutesBetween(start, end, UTC));
    }

    @Test
    @DisplayName("should account for the clock going back at the end of daylight saving time")
    void shouldAccountForTheClockGoingBackAtTheEndOfDaylightSavingTime() {
        // 2026-11-01, New York: at 02:00 the clocks go back to 01:00, so 00:30 -> 02:30 is three hours, not two.
        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 0, 30);
        LocalDateTime end = LocalDateTime.of(2026, 11, 1, 2, 30);

        assertEquals(180, ElapsedTime.minutesBetween(start, end, NEW_YORK));
    }

    @Test
    @DisplayName("should use the system zone when none is given")
    void shouldUseTheSystemZoneWhenNoneIsGiven() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 15, 10, 0, 0);

        assertEquals(ElapsedTime.minutesBetween(start, start.plusMinutes(45), ZoneId.systemDefault()),
                ElapsedTime.minutesBetween(start, start.plusMinutes(45)));
    }
}
