package com.lawkeys.hcfcore.pvp;

import java.util.UUID;

/**
 * Asked before a deathbanned player is turned away at login: may they come in
 * after all?
 *
 * <p>The lives system answers it - a player with a life left spends it and comes
 * in - and belongs to a module written after this one, which also does not run at
 * all in kitmap mode. Same seam as {@link DeathbanPolicy}, same reason
 * (ARCHITECTURE.md section 14); until one is installed, {@link #NONE} turns every
 * banned player away, as before.
 *
 * <p>A waiver that says yes must have lifted the ban itself: the login check does
 * nothing more than let the connection through.
 */
@FunctionalInterface
public interface DeathbanWaiver {

    /** @return whether this player may join despite the ban, which the waiver has lifted */
    boolean waive(UUID playerId, Deathban ban);

    DeathbanWaiver NONE = (playerId, ban) -> false;
}
