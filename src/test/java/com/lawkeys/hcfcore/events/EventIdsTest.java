package com.lawkeys.hcfcore.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The charset {@code /events create} accepts for a new id - see the class
 * javadoc for why a dot must be refused before it ever reaches a
 * {@code ConfigurationSection}.
 */
class EventIdsTest {

    @Test
    void lowercaseLettersDigitsDashAndUnderscoreAreValid() {
        assertTrue(EventIds.isValid("dtc"));
        assertTrue(EventIds.isValid("last-break"));
        assertTrue(EventIds.isValid("arena_1"));
        assertTrue(EventIds.isValid("Arena1"), "case alone does not make it invalid - it is normalised instead");
    }

    @Test
    void aDotIsRefused() {
        assertFalse(EventIds.isValid("raid.zone1"), "a dot is a path separator to ConfigurationSection");
    }

    @Test
    void otherPathSeparatorsAndSymbolsAreRefused() {
        assertFalse(EventIds.isValid("raid/zone1"));
        assertFalse(EventIds.isValid("raid zone1"));
        assertFalse(EventIds.isValid("raid:zone1"));
        assertFalse(EventIds.isValid("raid#zone1"));
    }

    @Test
    void nullAndEmptyAreRefused() {
        assertFalse(EventIds.isValid(null));
        assertFalse(EventIds.isValid(""));
    }

    @Test
    void tooShortOrTooLongIsRefused() {
        assertFalse(EventIds.isValid("a"), "shorter than MIN_LENGTH");
        assertTrue(EventIds.isValid("a".repeat(EventIds.MAX_LENGTH)));
        assertFalse(EventIds.isValid("a".repeat(EventIds.MAX_LENGTH + 1)));
    }

    @Test
    void normalizeLowercasesTheId() {
        assertEquals("arena", EventIds.normalize("Arena"));
        assertEquals("last-break", EventIds.normalize("Last-Break"));
    }
}
