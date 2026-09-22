package com.lawkeys.hcfcore.events.planning;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The weekly schedule of {@code events.yml} ({@code weekly-schedule.days}): which
 * event starts at what time on which day of the week - the owner's request of
 * 22/09/2026, alongside each event's own daily {@code schedule}.
 *
 * <p>Each day holds entries written {@code "HH:mm <event id>"}. Pure Java: no
 * server API, so it is unit-tested without one.
 */
public record WeeklySchedule(List<PlannedStart> starts) {

    /** One start: this event, at this time, every such day. */
    public record PlannedStart(DayOfWeek day, LocalTime time, String eventId) {

        public PlannedStart {
            Objects.requireNonNull(day, "day");
            Objects.requireNonNull(time, "time");
            Objects.requireNonNull(eventId, "eventId");
        }

        /** @return the entry as {@code events.yml} holds it: {@code "18:00 koth"} */
        public String entry() {
            return time + " " + eventId;
        }
    }

    private static final Comparator<PlannedStart> ORDER = Comparator.comparing(PlannedStart::day)
            .thenComparing(PlannedStart::time).thenComparing(PlannedStart::eventId);

    public WeeklySchedule {
        List<PlannedStart> sorted = new ArrayList<>(Objects.requireNonNull(starts, "starts"));
        sorted.sort(ORDER);
        starts = List.copyOf(sorted);
    }

    public static WeeklySchedule empty() {
        return new WeeklySchedule(List.of());
    }

    /**
     * @param days each day's name - {@code monday}, or {@code mon} - to its entries
     * @param warn told of every day or entry that cannot be read, which is skipped
     */
    public static WeeklySchedule parse(Map<String, List<String>> days, Consumer<String> warn) {
        List<PlannedStart> starts = new ArrayList<>();
        for (Map.Entry<String, List<String>> day : days.entrySet()) {
            Optional<DayOfWeek> dayOfWeek = parseDay(day.getKey());
            if (dayOfWeek.isEmpty()) {
                warn.accept("weekly-schedule: '" + day.getKey() + "' is not a day of the week; ignored.");
                continue;
            }
            for (String raw : day.getValue() == null ? List.<String>of() : day.getValue()) {
                Optional<PlannedStart> start = parseEntry(dayOfWeek.get(), raw);
                if (start.isEmpty()) {
                    warn.accept("weekly-schedule: '" + raw + "' on " + day.getKey()
                            + " is not \"HH:mm <event id>\"; ignored.");
                    continue;
                }
                starts.add(start.get());
            }
        }
        return new WeeklySchedule(starts);
    }

    /** @return the day an English name ({@code monday}) or its first three letters ({@code mon}) names */
    public static Optional<DayOfWeek> parseDay(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String typed = raw.trim().toLowerCase(Locale.ROOT);
        for (DayOfWeek day : DayOfWeek.values()) {
            String name = day.name().toLowerCase(Locale.ROOT);
            if (name.equals(typed) || (typed.length() == 3 && name.startsWith(typed))) {
                return Optional.of(day);
            }
        }
        return Optional.empty();
    }

    /** @return {@code "18:00 koth"} read as a start on {@code day}, or empty when it is not that shape */
    public static Optional<PlannedStart> parseEntry(DayOfWeek day, String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String[] parts = raw.trim().split("\\s+");
        if (parts.length != 2 || parts[1].isBlank()) {
            return Optional.empty();
        }
        Optional<LocalTime> time = parseTime(parts[0]);
        return time.map(at -> new PlannedStart(day, at, parts[1].toLowerCase(Locale.ROOT)));
    }

    /** @return {@code HH:mm} read as a time of day - {@code 9:30} is accepted too */
    public static Optional<LocalTime> parseTime(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String typed = raw.trim();
        if (typed.matches("\\d:\\d\\d")) {
            typed = "0" + typed;
        }
        try {
            return Optional.of(LocalTime.parse(typed));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** @return the starts of one day, in time order */
    public List<PlannedStart> on(DayOfWeek day) {
        return starts.stream().filter(start -> start.day() == day).toList();
    }
}
