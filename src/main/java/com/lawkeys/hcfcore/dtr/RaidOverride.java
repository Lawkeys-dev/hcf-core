package com.lawkeys.hcfcore.dtr;

/**
 * Whether every team is raidable right now, whatever its DTR - EOTW, the Purge.
 *
 * <p>Those phases belong to a module written after this one, which opens every
 * claim by wrapping the claim module's raid policy. This module still reports
 * DTR, and without asking it announced "no longer raidable" to the whole server
 * during an EOTW that had not ended (found in game, 13/09/2026). Asked through this
 * seam (ARCHITECTURE.md section 14); until it is installed, {@link #NONE} leaves
 * DTR the only answer.
 */
@FunctionalInterface
public interface RaidOverride {

    boolean everyTeamRaidable();

    RaidOverride NONE = () -> false;
}
