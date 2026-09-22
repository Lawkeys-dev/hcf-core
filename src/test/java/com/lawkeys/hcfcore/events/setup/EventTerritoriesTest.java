package com.lawkeys.hcfcore.events.setup;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventTerritoriesTest {

    private static final Predicate<String> ALL_RUNNING = id -> true;
    private static final Predicate<String> NONE_RUNNING = id -> false;

    @Test
    void landThatIsNoEventsIsNeverRefusedHere() {
        EventTerritories territories = EventTerritories.of(List.of(new EventTerritories.Declared("koth", "Koth", null)));
        assertFalse(territories.abilitiesRefused("Spawn", true, ALL_RUNNING));
        assertFalse(territories.abilitiesRefused(null, true, ALL_RUNNING));
    }

    @Test
    void anEventsLandRefusesAbilitiesOnlyWhileItRuns() {
        EventTerritories territories = EventTerritories.of(List.of(new EventTerritories.Declared("koth", "Koth", null)));
        assertTrue(territories.abilitiesRefused("koth", true, ALL_RUNNING));
        assertFalse(territories.abilitiesRefused("koth", true, NONE_RUNNING));
        assertFalse(territories.abilitiesRefused("KOTH", false, ALL_RUNNING));
    }

    @Test
    void anEventsOwnSayWinsOverTheDefault() {
        EventTerritories territories = EventTerritories.of(List.of(
                new EventTerritories.Declared("conquest", "Conquest", false),
                new EventTerritories.Declared("koth", "Koth", true)));
        assertFalse(territories.abilitiesRefused("Conquest", true, ALL_RUNNING));
        assertTrue(territories.abilitiesRefused("Koth", false, ALL_RUNNING));
        assertFalse(territories.abilitiesRefused("Koth", false, NONE_RUNNING));
    }

    @Test
    void onSharedLandOnlyTheRunningEventCounts() {
        EventTerritories territories = EventTerritories.of(List.of(
                new EventTerritories.Declared("totem", "Totem", false),
                new EventTerritories.Declared("mini-totem", "Totem", true)));
        assertFalse(territories.abilitiesRefused("Totem", true, Set.of("totem")::contains));
        assertTrue(territories.abilitiesRefused("Totem", false, Set.of("mini-totem")::contains));
        assertTrue(territories.abilitiesRefused("Totem", false, ALL_RUNNING));
    }
}
