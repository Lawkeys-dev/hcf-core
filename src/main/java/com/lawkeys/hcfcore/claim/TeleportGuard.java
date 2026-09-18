package com.lawkeys.hcfcore.claim;

import java.util.UUID;

/**
 * Asks whether a player may be teleported by the plugin right now.
 *
 * <p>The same seam as {@link RaidabilityPolicy}, for the same reason
 * (ARCHITECTURE.md section 14): {@code /team hq} must refuse to teleport a player
 * who is in combat, but combat is the {@code pvp/} module's business and
 * {@code claim/} is written first. So the claim module declares the question, with
 * a permissive default, and {@code pvp/} answers it at startup.
 *
 * <p>Implementations may tell the player why they were refused, which is why this
 * returns a decision rather than being a pure predicate.
 */
@FunctionalInterface
public interface TeleportGuard {

    /**
     * @return {@code true} if the teleport must not happen, in which case the
     *         implementation has already explained why to the player
     */
    boolean blockTeleport(UUID player);

    /** Nothing is ever blocked. Active until the {@code pvp/} module is running. */
    TeleportGuard ALLOW = player -> false;
}
