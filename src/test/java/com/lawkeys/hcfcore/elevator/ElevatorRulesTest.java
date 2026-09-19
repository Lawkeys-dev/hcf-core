package com.lawkeys.hcfcore.elevator;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where an elevator sign takes a player. */
class ElevatorRulesTest {

    /** A column: floors of stone at 63, 70 and 80; everything else air. */
    private static final Set<Integer> STONE = Set.of(63, 70, 80);
    private static final IntPredicate FIRM = STONE::contains;
    private static final IntPredicate FREE = y -> !STONE.contains(y);

    @Test
    void upGoesToTheNextFloorNotTheOwn() {
        assertEquals(71, ElevatorRules.destination(64, -64, 320, ElevatorRules.Direction.UP, 0, FIRM, FREE).getAsInt());
        assertEquals(81, ElevatorRules.destination(71, -64, 320, ElevatorRules.Direction.UP, 0, FIRM, FREE).getAsInt());
    }

    @Test
    void downGoesToTheFloorBelow() {
        assertEquals(71, ElevatorRules.destination(81, -64, 320, ElevatorRules.Direction.DOWN, 0, FIRM, FREE).getAsInt());
        assertEquals(64, ElevatorRules.destination(71, -64, 320, ElevatorRules.Direction.DOWN, 0, FIRM, FREE).getAsInt());
    }

    @Test
    void aFloorNeedsTwoFreeBlocks() {
        // Stone at 70 and 72: standing on 70 leaves one block of room - not a floor.
        Set<Integer> low = Set.of(63, 70, 72);
        assertEquals(73, ElevatorRules.destination(64, -64, 320, ElevatorRules.Direction.UP, 0, low::contains,
                y -> !low.contains(y)).getAsInt(), "the next floor with room enough");
    }

    @Test
    void nothingThatWayIsNoFloor() {
        assertTrue(ElevatorRules.destination(81, -64, 320, ElevatorRules.Direction.UP, 0, FIRM, FREE).isEmpty());
        assertTrue(ElevatorRules.destination(64, -64, 320, ElevatorRules.Direction.DOWN, 0, FIRM, FREE).isEmpty());
    }

    @Test
    void aFloorBeyondTheDistanceIsNotReached() {
        assertTrue(ElevatorRules.destination(64, -64, 320, ElevatorRules.Direction.UP, 5, FIRM, FREE).isEmpty());
        assertEquals(71, ElevatorRules.destination(64, -64, 320, ElevatorRules.Direction.UP, 7, FIRM, FREE).getAsInt());
    }

    @Test
    void aLinkedSignIsTheNextOneThatWayWhateverLiesBetween() {
        IntPredicate signs = Set.of(65, 90, 120)::contains;
        assertEquals(90, ElevatorRules.linkedSign(65, -64, 320, ElevatorRules.Direction.UP, 0, signs).getAsInt());
        assertEquals(65, ElevatorRules.linkedSign(90, -64, 320, ElevatorRules.Direction.DOWN, 0, signs).getAsInt());
        assertTrue(ElevatorRules.linkedSign(120, -64, 320, ElevatorRules.Direction.UP, 0, signs).isEmpty(),
                "alone that way: the floor search takes over");
        assertTrue(ElevatorRules.linkedSign(65, -64, 320, ElevatorRules.Direction.UP, 10, signs).isEmpty(),
                "beyond the distance");
    }

    @Test
    void theRiderArrivesAsHighAboveTheSignAsTheyLeft() {
        assertEquals(89, ElevatorRules.feetAtLinked(65, 64, 90), "a sign at eye level stays at eye level");
        assertEquals(90, ElevatorRules.feetAtLinked(65, 65, 90), "a sign at the feet stays at the feet");
        assertEquals(89, ElevatorRules.feetAtLinked(70, 64, 90), "never more than a block below it");
    }

    @Test
    void theWorldsLimitsAreKept() {
        IntPredicate all = y -> true;
        assertTrue(ElevatorRules.destination(318, -64, 320, ElevatorRules.Direction.UP, 0, all, all).isEmpty(),
                "no room left under the build limit");
    }
}
