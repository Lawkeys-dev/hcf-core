package com.lawkeys.hcfcore.events.setup;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static com.lawkeys.hcfcore.events.setup.TerritoryAbilityMode.ALWAYS;
import static com.lawkeys.hcfcore.events.setup.TerritoryAbilityMode.DURING_EVENT;
import static com.lawkeys.hcfcore.events.setup.TerritoryAbilityMode.NEVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventTerritoriesTest {

    private static final Predicate<String> ALL_RUNNING = id -> true;
    private static final Predicate<String> NONE_RUNNING = id -> false;

    private static EventTerritories koth(TerritoryAbilityMode own) {
        return EventTerritories.of(List.of(new EventTerritories.Declared("koth", "Koth", own)));
    }

    @Test
    void landThatIsNoEventsIsNeverRefusedHere() {
        assertFalse(koth(null).abilitiesRefused("Spawn", ALWAYS, ALL_RUNNING));
        assertFalse(koth(null).abilitiesRefused(null, ALWAYS, ALL_RUNNING));
    }

    @Test
    void duringEventRefusesOnlyWhileItRuns() {
        assertTrue(koth(null).abilitiesRefused("koth", DURING_EVENT, ALL_RUNNING));
        assertFalse(koth(null).abilitiesRefused("koth", DURING_EVENT, NONE_RUNNING));
    }

    @Test
    void alwaysRefusesRunningOrNot() {
        assertTrue(koth(null).abilitiesRefused("KOTH", ALWAYS, NONE_RUNNING));
        assertTrue(koth(null).abilitiesRefused("KOTH", ALWAYS, ALL_RUNNING));
    }

    @Test
    void neverRefuses() {
        assertFalse(koth(null).abilitiesRefused("koth", NEVER, ALL_RUNNING));
    }

    @Test
    void anEventsOwnSayWinsOverTheDefault() {
        assertTrue(koth(ALWAYS).abilitiesRefused("koth", NEVER, NONE_RUNNING));
        assertFalse(koth(NEVER).abilitiesRefused("koth", ALWAYS, ALL_RUNNING));
        assertFalse(koth(DURING_EVENT).abilitiesRefused("koth", ALWAYS, NONE_RUNNING));
    }

    @Test
    void onSharedLandARefusalByAnyEventWins() {
        EventTerritories territories = EventTerritories.of(List.of(
                new EventTerritories.Declared("totem", "Totem", NEVER),
                new EventTerritories.Declared("mini-totem", "Totem", DURING_EVENT)));
        assertFalse(territories.abilitiesRefused("Totem", ALWAYS, Set.of("totem")::contains));
        assertTrue(territories.abilitiesRefused("Totem", NEVER, Set.of("mini-totem")::contains));
    }

    @Test
    void modesAreReadByIdOrAsTheOldBooleans() {
        assertEquals(Optional.of(ALWAYS), TerritoryAbilityMode.parse("always"));
        assertEquals(Optional.of(DURING_EVENT), TerritoryAbilityMode.parse("DURING_EVENT"));
        assertEquals(Optional.of(DURING_EVENT), TerritoryAbilityMode.parse(true));
        assertEquals(Optional.of(NEVER), TerritoryAbilityMode.parse(false));
        assertEquals(Optional.empty(), TerritoryAbilityMode.parse("sometimes"));
        assertEquals(Optional.empty(), TerritoryAbilityMode.parse(null));
        assertEquals("during-event", DURING_EVENT.id());
    }
}
