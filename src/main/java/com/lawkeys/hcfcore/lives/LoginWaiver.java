package com.lawkeys.hcfcore.lives;

import com.lawkeys.hcfcore.pvp.Deathban;

import java.util.Objects;
import java.util.UUID;

/**
 * The rule behind {@code use-on-login}: a deathbanned player who has a life spends
 * it and comes in. Pure Java, so the order of the checks is tested.
 */
final class LoginWaiver {

    private LoginWaiver() {
    }

    /**
     * Spends one life if the ban may be waived. A ban until the map ends never may,
     * and it is checked before the life is taken, so nobody loses one for nothing.
     *
     * @return whether a life was spent and the player may come in
     */
    static boolean spend(boolean enabled, boolean useOnLogin, Deathban ban, Lives lives, UUID playerId) {
        Objects.requireNonNull(ban, "ban");
        if (!enabled || !useOnLogin || ban.isUntilMapEnd() || lives == null) {
            return false;
        }
        return lives.spendOne(playerId);
    }
}
