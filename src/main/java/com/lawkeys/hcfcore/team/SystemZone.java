package com.lawkeys.hcfcore.team;

import java.util.Locale;
import java.util.Optional;

/**
 * What a server-owned team's land is for, as far as fighting goes.
 *
 * <p>Both kinds are server land: nobody builds there without the bypass
 * permission, and no player team can claim it. They differ in exactly one thing,
 * whether players may fight on it. HCF needs both - spawn, where nobody can be
 * attacked, and the warzone, roads and event grounds around it, where everybody
 * can. With a single kind, every system team would be a safe zone, and the
 * warzone impossible to build.
 *
 * <p>Only meaningful for {@link TeamType#SYSTEM} teams; a player team has none.
 */
public enum SystemZone {

    /** Spawn and the like: PvP is off. What every system team was before this choice existed. */
    SAFE,
    /** The warzone, roads, event grounds: server land where players fight. */
    COMBAT;

    /** @return the id used in commands and storage: {@code safe} or {@code combat} */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** @return the zone named by {@code raw}, case-insensitively, or empty when it names none */
    public static Optional<SystemZone> fromId(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalised = raw.trim().toUpperCase(Locale.ROOT);
        for (SystemZone zone : values()) {
            if (zone.name().equals(normalised)) {
                return Optional.of(zone);
            }
        }
        return Optional.empty();
    }
}
