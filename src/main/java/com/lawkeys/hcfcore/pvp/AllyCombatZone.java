package com.lawkeys.hcfcore.pvp;

import java.util.UUID;

/**
 * Where allies may hurt each other (ARCHITECTURE.md section 14).
 *
 * <p>The owner's rule (13/09/2026) lets allies fight only in an event area, and
 * what counts as one belongs to {@code events/}, written after this module - so the
 * question is declared here and answered there, like {@link CombatProtection}. The
 * default answers "nowhere": without the events module, allies never hurt each other.
 */
@FunctionalInterface
public interface AllyCombatZone {

    /** Allies fight nowhere. */
    AllyCombatZone NOWHERE = (playerId, world, x, y, z) -> false;

    /**
     * @return whether this player, standing at this point, is in an event area - the
     *         zone of a running capture event, or the King of a running Kill the King
     *         wherever they stand. A hit between allies lands if either of the two is.
     */
    boolean covers(UUID playerId, String world, double x, double y, double z);
}
