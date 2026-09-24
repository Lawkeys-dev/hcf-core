package com.lawkeys.hcfcore.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgivenDeathsTest {

    private final UUID king = UUID.randomUUID();

    @AfterEach
    void reset() {
        ForgivenDeaths.reset();
    }

    @Test
    void nothingIsForgivenUnlessSaid() {
        assertFalse(ForgivenDeaths.spares(king, ForgivenDeaths.Cost.DTR));
        assertFalse(ForgivenDeaths.spares(null, ForgivenDeaths.Cost.DEATHBAN));
    }

    @Test
    void onlyTheCostsNamedAreSparedAndOnlyUntilCleared() {
        ForgivenDeaths.forgive(king, EnumSet.of(ForgivenDeaths.Cost.DTR));
        assertTrue(ForgivenDeaths.spares(king, ForgivenDeaths.Cost.DTR));
        assertFalse(ForgivenDeaths.spares(king, ForgivenDeaths.Cost.DEATHBAN));
        assertFalse(ForgivenDeaths.spares(UUID.randomUUID(), ForgivenDeaths.Cost.DTR));

        ForgivenDeaths.clear(king);
        assertFalse(ForgivenDeaths.spares(king, ForgivenDeaths.Cost.DTR));
    }
}
