package com.lawkeys.hcfcore.general;

import org.bukkit.entity.Player;

/**
 * Whether a player may be sent to spawn - asked before a {@code /spawn} countdown
 * starts, and again when it ends.
 *
 * <p>A seam (ARCHITECTURE.md section 14): {@code events/} answers it for the King of
 * Kill the King, who may not enter spawn by any means. Without it the King's
 * countdown ran its five seconds, and only then did the teleport guard refuse them -
 * found in the first in-game test.
 */
@FunctionalInterface
public interface SpawnGuard {

    /** @return {@code true} to refuse, having told the player why */
    boolean refuse(Player player);

    /** Nobody is refused. */
    SpawnGuard ALLOW = player -> false;
}
