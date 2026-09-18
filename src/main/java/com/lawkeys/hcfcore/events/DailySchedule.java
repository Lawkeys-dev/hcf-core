package com.lawkeys.hcfcore.events;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Times of day at which something starts by itself, every day.
 *
 * <p>Shared by the capture events and Kill the King: both are scheduled the same
 * way in {@code events.yml}, and the two subtleties below are too easy to get
 * wrong twice.
 */
public final class DailySchedule {

    private DailySchedule() {
    }

    /**
     * @return the first scheduled time strictly after {@code now}, or empty when
     *         there are no times
     */
    public static Optional<ZonedDateTime> next(List<LocalTime> times, ZoneId zone, long now) {
        if (times.isEmpty()) {
            return Optional.empty();
        }
        ZonedDateTime from = Instant.ofEpochMilli(now).atZone(zone);
        ZonedDateTime best = null;
        // Today and tomorrow are enough: the schedule repeats daily, so the next
        // occurrence is always within 24 hours of now.
        for (int dayOffset = 0; dayOffset <= 1; dayOffset++) {
            LocalDate date = from.toLocalDate().plusDays(dayOffset);
            for (LocalTime time : times) {
                ZonedDateTime candidate = date.atTime(time).atZone(zone);
                if (candidate.isAfter(from) && (best == null || candidate.isBefore(best))) {
                    best = candidate;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** @return whether a scheduled time falls in {@code (from, to]} */
    public static boolean occursWithin(List<LocalTime> times, ZoneId zone, long from, long to) {
        ZonedDateTime end = Instant.ofEpochMilli(to).atZone(zone);
        // Yesterday as well as today: a window that spans midnight must not miss a
        // time configured just before it.
        for (int dayOffset = -1; dayOffset <= 0; dayOffset++) {
            LocalDate date = end.toLocalDate().plusDays(dayOffset);
            for (LocalTime time : times) {
                long occurrence = date.atTime(time).atZone(zone).toInstant().toEpochMilli();
                if (occurrence > from && occurrence <= to) {
                    return true;
                }
            }
        }
        return false;
    }
}
