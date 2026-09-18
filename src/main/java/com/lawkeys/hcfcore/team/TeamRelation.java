package com.lawkeys.hcfcore.team;

/**
 * Relation between a viewer and another team, as consumed by the UI layer
 * (scoreboard, tab list, name tags, claim borders) and by the PvP module.
 */
public enum TeamRelation {

    /** The viewer's own team. */
    SELF,
    /** A team the viewer's team has an accepted alliance with. */
    ALLY,
    /** A server-owned team (spawn, warzone...). */
    SYSTEM,
    /** Any other player team. */
    ENEMY,
    /** The viewer, or the target, has no team. */
    NEUTRAL
}
