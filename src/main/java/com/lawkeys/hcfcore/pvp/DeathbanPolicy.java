package com.lawkeys.hcfcore.pvp;

/**
 * Decides how a death is banned right now: as usual, not at all, or until the map
 * ends.
 *
 * <p>The phase of the map - SOTW at its start, EOTW at its end - changes what a
 * death costs, and it belongs to a module written after this one. Same seam as
 * {@link CombatProtection}, same reason (ARCHITECTURE.md section 14); until it is
 * installed, {@link #USUAL} keeps every death banned by the tiers in
 * {@code pvp.yml}.
 */
@FunctionalInterface
public interface DeathbanPolicy {

    enum Rule {
        /** Banned for as long as the player's tier says. */
        USUAL,
        /** Not banned at all. */
        NONE,
        /** Banned until the map ends: only staff lift it, after the reset. */
        UNTIL_MAP_END
    }

    Rule rule();

    DeathbanPolicy USUAL = () -> Rule.USUAL;
}
