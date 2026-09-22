package com.lawkeys.hcfcore.events.setup;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventTerritoriesTest {

    @Test
    void landThatIsNoEventsIsNeverRefusedHere() {
        EventTerritories territories = EventTerritories.of(List.of(new EventTerritories.Declared("koth", "Koth", null)));
        assertFalse(territories.abilitiesRefused("Spawn", true));
        assertFalse(territories.abilitiesRefused(null, true));
    }

    @Test
    void anEventThatSaysNothingFollowsTheDefault() {
        EventTerritories territories = EventTerritories.of(List.of(new EventTerritories.Declared("koth", "Koth", null)));
        assertTrue(territories.abilitiesRefused("koth", true));
        assertFalse(territories.abilitiesRefused("KOTH", false));
    }

    @Test
    void anEventsOwnSayWinsOverTheDefault() {
        EventTerritories territories = EventTerritories.of(List.of(
                new EventTerritories.Declared("conquest", "Conquest", false),
                new EventTerritories.Declared("koth", "Koth", true)));
        assertFalse(territories.abilitiesRefused("Conquest", true));
        assertTrue(territories.abilitiesRefused("Koth", false));
    }

    @Test
    void onSharedLandARefusalWins() {
        EventTerritories territories = EventTerritories.of(List.of(
                new EventTerritories.Declared("totem", "Totem", false),
                new EventTerritories.Declared("mini-totem", "Totem", true)));
        assertTrue(territories.abilitiesRefused("Totem", false));
        EventTerritories silentThenAllowed = EventTerritories.of(List.of(
                new EventTerritories.Declared("totem", "Totem", null),
                new EventTerritories.Declared("mini-totem", "Totem", false)));
        assertFalse(silentThenAllowed.abilitiesRefused("Totem", true));
    }
}
