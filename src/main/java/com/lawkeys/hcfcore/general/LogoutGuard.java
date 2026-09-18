package com.lawkeys.hcfcore.general;

import org.bukkit.entity.Player;

/**
 * Whether a player may log out through {@code /logout} - asked before the countdown
 * starts, and again when it ends.
 *
 * <p>A seam (ARCHITECTURE.md section 14): {@code pvp/} answers it with the combat tag.
 * The countdown is cancelled by damage taken and by moving, not by hitting somebody,
 * and hitting somebody tags the attacker: a player who struck a blow during their
 * countdown was kicked while tagged, and the anti-logout rule killed them - the safe
 * logout was the one that killed (found in the command review, 15/09/2026).
 */
@FunctionalInterface
public interface LogoutGuard {

    /** @return {@code true} to refuse, having told the player why */
    boolean refuse(Player player);

    /** Nobody is refused. */
    LogoutGuard ALLOW = player -> false;
}
