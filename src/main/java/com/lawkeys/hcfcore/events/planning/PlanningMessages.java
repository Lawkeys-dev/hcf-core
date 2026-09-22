package com.lawkeys.hcfcore.events.planning;

import java.time.DayOfWeek;
import java.util.Locale;

/** Language keys of {@code /schedule} and the weekly schedule (see {@code lang/en.yml}, {@code events.planning}). */
public final class PlanningMessages {

    public static final String HEADER = "events.planning.header";
    public static final String DAY = "events.planning.day";
    public static final String TODAY = "events.planning.today";
    public static final String TOMORROW = "events.planning.tomorrow";
    public static final String DATE_FORMAT = "events.planning.date-format";
    public static final String ENTRY = "events.planning.entry";
    public static final String EMPTY = "events.planning.empty";
    public static final String DISABLED = "events.planning.disabled";
    public static final String SOON = "events.planning.soon";
    public static final String MENU_TITLE = "events.planning.menu-title";
    public static final String MENU_DAY = "events.planning.menu-day";
    public static final String MENU_ENTRY = "events.planning.menu-entry";
    public static final String MENU_DAY_EMPTY = "events.planning.menu-day-empty";
    public static final String USAGE = "events.planning.usage";
    public static final String INVALID_DAY = "events.planning.invalid-day";
    public static final String INVALID_TIME = "events.planning.invalid-time";
    public static final String UNKNOWN_EVENT = "events.planning.unknown-event";
    public static final String ADDED = "events.planning.added";
    public static final String ALREADY_PLANNED = "events.planning.already-planned";
    public static final String REMOVED = "events.planning.removed";
    public static final String NOT_PLANNED = "events.planning.not-planned";
    public static final String WRITE_FAILED = "events.planning.write-failed";

    public static final String DAY_MONDAY = "events.planning.days.monday";
    public static final String DAY_TUESDAY = "events.planning.days.tuesday";
    public static final String DAY_WEDNESDAY = "events.planning.days.wednesday";
    public static final String DAY_THURSDAY = "events.planning.days.thursday";
    public static final String DAY_FRIDAY = "events.planning.days.friday";
    public static final String DAY_SATURDAY = "events.planning.days.saturday";
    public static final String DAY_SUNDAY = "events.planning.days.sunday";

    private PlanningMessages() {
    }

    /** @return the key of a day's name, as players read it */
    public static String day(DayOfWeek day) {
        return "events.planning.days." + day.name().toLowerCase(Locale.ROOT);
    }
}
