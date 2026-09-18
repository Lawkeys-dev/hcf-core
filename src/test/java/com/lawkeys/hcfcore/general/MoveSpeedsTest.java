package com.lawkeys.hcfcore.general;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The 0-10 speeds of /flyspeed and /walkspeed. */
class MoveSpeedsTest {

    private static final float EPSILON = 1e-6f;

    @Test
    void oneIsTheGamesOwnSpeed() {
        assertEquals(MoveSpeeds.DEFAULT_WALK, MoveSpeeds.toGame(1, MoveSpeeds.DEFAULT_WALK), EPSILON);
        assertEquals(MoveSpeeds.DEFAULT_FLY, MoveSpeeds.toGame(1, MoveSpeeds.DEFAULT_FLY), EPSILON);
    }

    @Test
    void tenIsTheFastestThereIs() {
        assertEquals(1.0f, MoveSpeeds.toGame(10, MoveSpeeds.DEFAULT_WALK), EPSILON);
        assertEquals(1.0f, MoveSpeeds.toGame(10, MoveSpeeds.DEFAULT_FLY), EPSILON);
    }

    @Test
    void belowOneSlowsDownAndZeroStops() {
        assertEquals(0.1f, MoveSpeeds.toGame(0.5f, MoveSpeeds.DEFAULT_WALK), EPSILON);
        assertEquals(0f, MoveSpeeds.toGame(0, MoveSpeeds.DEFAULT_WALK), EPSILON);
    }

    @Test
    void theSpeedRisesEvenlyAndIsKeptInRange() {
        assertEquals(0.2f + 4f / 9f * 0.8f, MoveSpeeds.toGame(5, MoveSpeeds.DEFAULT_WALK), EPSILON);
        assertEquals(1.0f, MoveSpeeds.toGame(42, MoveSpeeds.DEFAULT_WALK), EPSILON);
        assertEquals(0f, MoveSpeeds.toGame(-3, MoveSpeeds.DEFAULT_WALK), EPSILON);
    }
}
