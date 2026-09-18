package com.lawkeys.hcfcore.util;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading back the durations staff type, as in {@code /sotw start 2h30m}. */
class DurationsTest {

    @Test
    void unitsAndSequencesAreRead() {
        assertEquals(OptionalLong.of(45), Durations.parse("45s"));
        assertEquals(OptionalLong.of(1_800), Durations.parse("30m"));
        assertEquals(OptionalLong.of(7_200), Durations.parse("2h"));
        assertEquals(OptionalLong.of(86_400), Durations.parse("1d"));
        assertEquals(OptionalLong.of(5_400), Durations.parse("1h30m"));
        assertEquals(OptionalLong.of(90_061), Durations.parse("1d1h1m1s"));
    }

    /** A configured duration a few digits too long would overflow into the past once added to the clock. */
    @Test
    void aConfiguredDurationIsCappedWithAWarning() {
        java.util.List<String> warnings = new java.util.ArrayList<>();
        assertEquals(7_200, Durations.capSeconds(7_200, "sotw.duration-seconds", warnings::add));
        assertEquals(Durations.MAX_SECONDS, Durations.capSeconds(Durations.MAX_SECONDS, "k", warnings::add));
        assertTrue(warnings.isEmpty());

        long capped = Durations.capSeconds(720_000_000_000_000_000L, "sotw.duration-seconds", warnings::add);
        assertEquals(Durations.MAX_SECONDS, capped);
        assertTrue(System.currentTimeMillis() + capped * 1000L > System.currentTimeMillis(), "still in the future");
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).startsWith("sotw.duration-seconds"));
        assertEquals(-5, Durations.capSeconds(-5, "k", warnings::add), "the lower bound stays the loader's call");
    }

    /** A countdown shows 1s until it is over, never 0s while time remains. */
    @Test
    void millisecondsLeftRoundUpToWholeSeconds() {
        assertEquals(0, Durations.secondsLeft(-5_000));
        assertEquals(0, Durations.secondsLeft(0));
        assertEquals(1, Durations.secondsLeft(1));
        assertEquals(1, Durations.secondsLeft(1_000));
        assertEquals(2, Durations.secondsLeft(1_001));
        assertEquals(Long.MAX_VALUE / 1000 + 1, Durations.secondsLeft(Long.MAX_VALUE), "no overflow");
    }

    @Test
    void aBareNumberIsSeconds() {
        assertEquals(OptionalLong.of(90), Durations.parse("90"));
    }

    @Test
    void caseAndSurroundingSpaceDoNotMatter() {
        assertEquals(OptionalLong.of(7_200), Durations.parse(" 2H "));
    }

    @Test
    void anythingElseIsRefusedRatherThanGuessed() {
        assertTrue(Durations.parse("1h30").isEmpty(), "thirty what? refused");
        assertTrue(Durations.parse("h").isEmpty(), "a unit with no number");
        assertTrue(Durations.parse("2w").isEmpty(), "an unknown unit");
        assertTrue(Durations.parse("-5m").isEmpty());
        assertTrue(Durations.parse("0").isEmpty(), "a duration of nothing");
        assertTrue(Durations.parse("0m").isEmpty());
        assertTrue(Durations.parse("").isEmpty());
        assertTrue(Durations.parse(null).isEmpty());
        assertTrue(Durations.parse("1h 30m").isEmpty(), "a command splits on spaces");
    }

    @Test
    void anOverflowIsRefusedNotWrapped() {
        assertTrue(Durations.parse("99999999999999999999s").isEmpty());
        assertTrue(Durations.parse("999999999999999d").isEmpty());
    }

    @Test
    void nothingLongerThanACenturyIsAccepted() {
        // Every typed duration becomes now + seconds * 1000: this is where that stops
        // overflowing into the past (found in the command review, 15/09/2026).
        assertEquals(OptionalLong.of(Durations.MAX_SECONDS), Durations.parse(Durations.MAX_SECONDS + "s"));
        assertTrue(Durations.parse((Durations.MAX_SECONDS + 1) + "s").isEmpty());
        assertTrue(Durations.parse("9999999999999999s").isEmpty(), "parses as a long, overflows once in millis");
        assertTrue(Durations.parse("36501d").isEmpty());

        assertTrue(Durations.isAcceptable(1));
        assertTrue(Durations.isAcceptable(Durations.MAX_SECONDS));
        assertFalse(Durations.isAcceptable(0));
        assertFalse(Durations.isAcceptable(-5));
        assertFalse(Durations.isAcceptable(Durations.MAX_SECONDS + 1));
        assertTrue(System.currentTimeMillis() + Durations.MAX_SECONDS * 1000L > 0, "the sum stays a real time");
    }
}
