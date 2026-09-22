package com.lawkeys.hcfcore.events.planning;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeekAgendaTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private static long at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(PARIS).toInstant().toEpochMilli();
    }

    private static WeeklySchedule schedule(WeeklySchedule.PlannedStart... starts) {
        return new WeeklySchedule(List.of(starts));
    }

    private static WeeklySchedule.PlannedStart start(DayOfWeek day, int hour, String id) {
        return new WeeklySchedule.PlannedStart(day, LocalTime.of(hour, 0), id);
    }

    @Test
    void theWeekAheadMergesWeeklyAndDailyStartsInOrder() {
        // Tuesday 22/09/2026, 12:00.
        long now = at(2026, 9, 22, 12, 0);
        WeeklySchedule weekly = schedule(start(DayOfWeek.FRIDAY, 20, "koth"), start(DayOfWeek.TUESDAY, 10, "citadel"));
        List<WeekAgenda.Entry> week = WeekAgenda.between(weekly, Map.of("dtc", List.of(LocalTime.of(18, 0))),
                PARIS, now, now + 7L * 24 * 3600 * 1000);
        // Seven daily DTCs, Friday's KOTH, and next Tuesday's Citadel - today's 10:00 has passed.
        assertEquals(9, week.size());
        assertEquals("dtc", week.get(0).eventId());
        assertFalse(week.get(0).weekly());
        assertEquals(LocalDateTime.of(2026, 9, 25, 20, 0), week.stream().filter(e -> e.eventId().equals("koth"))
                .findFirst().orElseThrow().at().toLocalDateTime());
        assertEquals("citadel", week.get(week.size() - 1).eventId());
        assertTrue(week.get(week.size() - 1).weekly());
    }

    @Test
    void aWindowHoldsItsEndButNotItsStart() {
        WeeklySchedule weekly = schedule(start(DayOfWeek.TUESDAY, 18, "koth"));
        long sixPm = at(2026, 9, 22, 18, 0);
        assertEquals(1, WeekAgenda.between(weekly, Map.of(), PARIS, sixPm - 1000, sixPm).size());
        assertTrue(WeekAgenda.between(weekly, Map.of(), PARIS, sixPm, sixPm + 1000).isEmpty());
    }

    @Test
    void aWindowAcrossMidnightFindsTheStartOnTheOtherSide() {
        WeeklySchedule weekly = schedule(new WeeklySchedule.PlannedStart(DayOfWeek.WEDNESDAY, LocalTime.MIDNIGHT, "koth"));
        long before = at(2026, 9, 22, 23, 59);
        assertEquals(1, WeekAgenda.between(weekly, Map.of(), PARIS, before, before + 2 * 60_000).size());
    }

    @Test
    void theSameEventAtTheSameTimeIsListedOnceAsWeekly() {
        WeeklySchedule weekly = schedule(start(DayOfWeek.TUESDAY, 18, "koth"));
        long noon = at(2026, 9, 22, 12, 0);
        List<WeekAgenda.Entry> day = WeekAgenda.between(weekly, Map.of("KOTH", List.of(LocalTime.of(18, 0))),
                PARIS, noon, at(2026, 9, 22, 23, 0));
        assertEquals(1, day.size());
        assertTrue(day.get(0).weekly());
    }
}
