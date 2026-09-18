package com.lawkeys.hcfcore.resourcenode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * When a resource node refills.
 *
 * <p><strong>Refill times are absolute, never "last refill plus an interval".</strong>
 * This is the same choice the {@code dtr/} module made for regeneration, for the
 * same three reasons: nothing drifts when a tick is missed or the server is
 * restarted, the next refill can be shown in {@code /events} without storing any
 * state, and the whole thing is testable by moving a clock instead of waiting
 * four hours.
 *
 * <p>An interval is therefore anchored to <em>local midnight</em>, not to the
 * moment the server booted: {@code interval-seconds: 14400} means 00:00, 04:00,
 * 08:00 and so on, every day, whatever the server did in between. Players can
 * learn the hours. The cost of that choice is the last window of the day being
 * short when the interval does not divide 24 hours evenly - the loader warns
 * about it rather than hiding it.
 *
 * @param intervalSeconds seconds between refills, anchored at local midnight;
 *                        {@code 0} disables the interval
 * @param times           explicit local times of day, which may be combined with
 *                        an interval; both sources are merged
 */
public record RefillSchedule(long intervalSeconds, List<LocalTime> times) {

    private static final int SECONDS_PER_DAY = 86_400;

    public RefillSchedule {
        times = List.copyOf(Objects.requireNonNull(times, "times"));
        if (intervalSeconds < 0) {
            throw new IllegalArgumentException("intervalSeconds cannot be negative");
        }
    }

    public static RefillSchedule none() {
        return new RefillSchedule(0L, List.of());
    }

    public static RefillSchedule everySeconds(long intervalSeconds) {
        return new RefillSchedule(intervalSeconds, List.of());
    }

    public static RefillSchedule at(LocalTime... times) {
        return new RefillSchedule(0L, List.of(times));
    }

    /** @return whether nothing is scheduled, i.e. only staff can refill this node */
    public boolean isEmpty() {
        return intervalSeconds <= 0 && times.isEmpty();
    }

    /**
     * @return the first refill strictly after {@code from}, or empty when nothing
     *         is scheduled
     */
    public Optional<ZonedDateTime> next(ZonedDateTime from) {
        Objects.requireNonNull(from, "from");
        if (isEmpty()) {
            return Optional.empty();
        }
        ZoneId zone = from.getZone();
        ZonedDateTime best = null;

        // Explicit times: today and tomorrow are enough, since the list repeats daily.
        for (int dayOffset = 0; dayOffset <= 1; dayOffset++) {
            LocalDate date = from.toLocalDate().plusDays(dayOffset);
            for (LocalTime time : times) {
                best = earliestAfter(best, from, date.atTime(time).atZone(zone));
            }
        }

        if (intervalSeconds > 0) {
            long secondOfDay = from.toLocalTime().toSecondOfDay();
            long step = secondOfDay / intervalSeconds;
            // Two steps rather than one: on the night the clocks go back, the local
            // time of day repeats, so the obvious next slot can resolve to an instant
            // that is not actually in the future. Taking the earliest candidate that
            // really is after "from" costs one extra comparison and removes the edge.
            for (long k = step + 1; k <= step + 2; k++) {
                long target = k * intervalSeconds;
                if (target >= SECONDS_PER_DAY) {
                    break;
                }
                best = earliestAfter(best, from,
                        from.toLocalDate().atTime(LocalTime.ofSecondOfDay(target)).atZone(zone));
            }
            // Midnight restarts the cycle, so it is always a candidate.
            best = earliestAfter(best, from, from.toLocalDate().plusDays(1).atStartOfDay(zone));
        }

        return Optional.ofNullable(best);
    }

    /**
     * @return whether a refill falls in {@code (from, to]}, both epoch millis
     *
     * <p>Expressed through {@link #next}: occurrences are increasing, so the first
     * one after {@code from} is the only one that can land in the window. That
     * also means the two never disagree - the agenda shows exactly what fires.
     */
    public boolean occursWithin(ZoneId zone, long from, long to) {
        Objects.requireNonNull(zone, "zone");
        if (to <= from) {
            return false;
        }
        return next(Instant.ofEpochMilli(from).atZone(zone))
                .map(occurrence -> occurrence.toInstant().toEpochMilli() <= to)
                .orElse(false);
    }

    private static ZonedDateTime earliestAfter(ZonedDateTime best, ZonedDateTime from,
                                               ZonedDateTime candidate) {
        if (!candidate.isAfter(from)) {
            return best;
        }
        return best == null || candidate.isBefore(best) ? candidate : best;
    }
}
