package com.lawkeys.hcfcore.pvp;

import java.util.Optional;
import java.util.UUID;

/**
 * Answers a question {@code pvp/} cannot answer itself: may this player hit that
 * one right now, whatever the ground they stand on?
 *
 * <p>Safe zones are a property of land and {@code pvp/} reads them from
 * {@code claim/} directly. A protection that belongs to <em>time</em> - SOTW, the
 * start of the map - lives in a module written after this one, so it is asked
 * through this seam (ARCHITECTURE.md section 14) and installed by that module at
 * startup. Until then {@link #NONE} is in place and nothing changes.
 */
@FunctionalInterface
public interface CombatProtection {

    /**
     * @return the language key telling the attacker why the hit is refused, or
     *         empty when it may land
     */
    Optional<String> refusal(UUID attacker, UUID victim);

    /** Nobody is protected by anything but the ground they stand on. */
    CombatProtection NONE = (attacker, victim) -> Optional.empty();
}
