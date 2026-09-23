package com.lawkeys.hcfcore.staff.mining;

import com.lawkeys.hcfcore.staff.mining.VeinCounter.Position;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VeinCounterTest {

    @Test
    void aLoneOreIsAVeinOfOne() {
        assertEquals(1, VeinCounter.vein(new Position(0, 0, 0), at -> false, 32).size());
    }

    @Test
    void touchingOresAreOneVein() {
        Set<Position> ore = Set.of(new Position(0, 0, 0), new Position(1, 0, 0), new Position(1, 1, 0),
                new Position(2, 2, 0));
        Set<Position> vein = VeinCounter.vein(new Position(0, 0, 0), ore::contains, 32);
        // (2, 2, 0) touches (1, 1, 0) along an edge: part of it.
        assertEquals(4, vein.size());
        assertEquals(new Position(0, 0, 0), vein.iterator().next(), "the mined block comes first");
    }

    @Test
    void oresMeetingAtACornerAreTwoVeins() {
        Set<Position> ore = Set.of(new Position(0, 0, 0), new Position(1, 1, 1));
        assertEquals(1, VeinCounter.vein(new Position(0, 0, 0), ore::contains, 32).size());
    }

    @Test
    void theWalkStopsAtTheCap() {
        // An endless line of ore.
        Set<Position> vein = VeinCounter.vein(new Position(0, 0, 0), at -> at.y() == 0 && at.z() == 0, 10);
        assertEquals(10, vein.size());
        assertTrue(vein.contains(new Position(0, 0, 0)));
    }
}
