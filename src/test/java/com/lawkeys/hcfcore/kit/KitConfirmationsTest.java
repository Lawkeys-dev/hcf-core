package com.lawkeys.hcfcore.kit;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitConfirmationsTest {

    private final KitConfirmations confirmations = new KitConfirmations();
    private final UUID player = UUID.randomUUID();

    @Test
    void theSecondClickOnTheSameSignInTimeConfirms() {
        assertFalse(confirmations.confirm(player, "sign-a", 1_000, 5), "the first click asks");
        assertTrue(confirmations.confirm(player, "sign-a", 4_000, 5));
        assertFalse(confirmations.confirm(player, "sign-a", 4_500, 5), "a confirmed click is not a standing yes");
    }

    @Test
    void anotherSignOrALateClickAsksAgain() {
        confirmations.confirm(player, "sign-a", 1_000, 5);
        assertFalse(confirmations.confirm(player, "sign-b", 2_000, 5), "another sign asks for itself");
        assertFalse(confirmations.confirm(player, "sign-b", 9_000, 5), "too late: asked again");
        assertTrue(confirmations.confirm(player, "sign-b", 10_000, 5));
    }

    @Test
    void zeroSecondsAsksNothing() {
        assertTrue(confirmations.confirm(player, "sign-a", 1_000, 0));
    }
}
