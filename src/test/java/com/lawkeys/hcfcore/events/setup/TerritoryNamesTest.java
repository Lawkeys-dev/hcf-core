package com.lawkeys.hcfcore.events.setup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerritoryNamesTest {

    @Test
    void wordsAreJoinedAndCapitalised() {
        assertEquals("Koth", TerritoryNames.forEvent("koth", 16));
        assertEquals("LastBreak", TerritoryNames.forEvent("last-break", 16));
        assertEquals("MiniTotem2", TerritoryNames.forEvent("mini-totem-2", 16));
        assertEquals("Koth_north", TerritoryNames.forEvent("koth_north", 16));
    }

    @Test
    void aLongIdIsCutToTheLongestTeamName() {
        assertEquals("AVeryLongEventNa", TerritoryNames.forEvent("a-very-long-event-name", 16));
    }
}
