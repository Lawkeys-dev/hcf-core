package com.lawkeys.hcfcore.events.planning;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeeklyScheduleTest {

    @Test
    void daysAreReadByNameOrThreeLetters() {
        assertEquals(Optional.of(DayOfWeek.MONDAY), WeeklySchedule.parseDay("Monday"));
        assertEquals(Optional.of(DayOfWeek.SATURDAY), WeeklySchedule.parseDay("sat"));
        assertEquals(Optional.empty(), WeeklySchedule.parseDay("sa"));
        assertEquals(Optional.empty(), WeeklySchedule.parseDay("lundi"));
    }

    @Test
    void anEntryIsATimeAndAnEventId() {
        assertEquals(Optional.of(new WeeklySchedule.PlannedStart(DayOfWeek.FRIDAY, LocalTime.of(9, 30), "koth")),
                WeeklySchedule.parseEntry(DayOfWeek.FRIDAY, " 9:30   KOTH "));
        assertEquals(Optional.empty(), WeeklySchedule.parseEntry(DayOfWeek.FRIDAY, "25:00 koth"));
        assertEquals(Optional.empty(), WeeklySchedule.parseEntry(DayOfWeek.FRIDAY, "18:00"));
        assertEquals(Optional.empty(), WeeklySchedule.parseEntry(DayOfWeek.FRIDAY, "18:00 koth citadel"));
        assertEquals("18:00 koth", new WeeklySchedule.PlannedStart(DayOfWeek.MONDAY, LocalTime.of(18, 0), "koth").entry());
    }

    @Test
    void badDaysAndEntriesAreSkippedWithAWarning() {
        Map<String, List<String>> days = new LinkedHashMap<>();
        days.put("saturday", List.of("21:00 citadel", "18:00 conquest", "nonsense"));
        days.put("funday", List.of("10:00 koth"));
        days.put("mon", List.of("20:00 koth"));
        List<String> warnings = new ArrayList<>();
        WeeklySchedule schedule = WeeklySchedule.parse(days, warnings::add);
        assertEquals(2, warnings.size());
        assertEquals(List.of("20:00 koth", "18:00 conquest", "21:00 citadel"),
                schedule.starts().stream().map(WeeklySchedule.PlannedStart::entry).toList());
        assertEquals(2, schedule.on(DayOfWeek.SATURDAY).size());
        assertTrue(schedule.on(DayOfWeek.SUNDAY).isEmpty());
    }
}
