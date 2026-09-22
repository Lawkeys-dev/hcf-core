package com.lawkeys.hcfcore.events.planning;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * When events start between two instants: the weekly schedule and each event's
 * daily {@code schedule}, merged - what {@code /schedule} lists and what the
 * "starts in 5 minutes" announcements are drawn from.
 *
 * <p>Pure Java: unit-tested without a server.
 */
public final class WeekAgenda {

    /**
     * @param weekly {@code true} for a start from the weekly schedule - the one this
     *               plugin launches itself; {@code false} for an event's own daily
     *               time, which its engine launches
     */
    public record Entry(ZonedDateTime at, String eventId, boolean weekly) {

        public Entry {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(eventId, "eventId");
        }
    }

    private WeekAgenda() {
    }

    /**
     * @param daily each event's id to its daily times
     * @return every start in {@code (from, to]}, in time order; the same event at the
     *         same instant from both sources is listed once, as weekly
     */
    public static List<Entry> between(WeeklySchedule weekly, Map<String, List<LocalTime>> daily,
                                      ZoneId zone, long from, long to) {
        List<Entry> entries = new ArrayList<>();
        if (to <= from) {
            return entries;
        }
        LocalDate first = Instant.ofEpochMilli(from).atZone(zone).toLocalDate();
        LocalDate last = Instant.ofEpochMilli(to).atZone(zone).toLocalDate();
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            for (WeeklySchedule.PlannedStart start : weekly.on(date.getDayOfWeek())) {
                add(entries, date.atTime(start.time()).atZone(zone), start.eventId(), true, from, to);
            }
            for (Map.Entry<String, List<LocalTime>> event : daily.entrySet()) {
                for (LocalTime time : event.getValue()) {
                    add(entries, date.atTime(time).atZone(zone), event.getKey().toLowerCase(java.util.Locale.ROOT),
                            false, from, to);
                }
            }
        }
        entries.sort(Comparator.comparing(Entry::at).thenComparing(Entry::eventId));
        return entries;
    }

    private static void add(List<Entry> entries, ZonedDateTime at, String eventId, boolean weekly, long from, long to) {
        long millis = at.toInstant().toEpochMilli();
        if (millis <= from || millis > to) {
            return;
        }
        for (Entry existing : entries) {
            if (existing.eventId().equals(eventId) && existing.at().toInstant().equals(at.toInstant())) {
                return; // Weekly entries are added first: the duplicate is the daily one.
            }
        }
        entries.add(new Entry(at, eventId, weekly));
    }
}
