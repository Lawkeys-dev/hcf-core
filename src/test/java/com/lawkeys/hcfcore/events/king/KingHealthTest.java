package com.lawkeys.hcfcore.events.king;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KingHealthTest {

    @Test
    void healthIsAPercentageOfTheMaximum() {
        assertEquals(100, KingHealth.percent(20, 20));
        assertEquals(50, KingHealth.percent(10, 20));
        assertEquals(38, KingHealth.percent(15, 40), "a King with more maximum health reads against it");
        assertEquals(3, KingHealth.percent(0.5, 20), "rounded, not cut down");
    }

    @Test
    void neverOutsideZeroToAHundred() {
        assertEquals(100, KingHealth.percent(30, 20));
        assertEquals(0, KingHealth.percent(0, 20));
        assertEquals(0, KingHealth.percent(10, 0), "an unknown maximum reads as nothing");
    }
}
