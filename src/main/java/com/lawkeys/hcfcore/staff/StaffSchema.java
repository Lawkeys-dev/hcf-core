package com.lawkeys.hcfcore.staff;

import com.lawkeys.hcfcore.database.migration.Migration;

import java.util.List;

/**
 * The staff module's versioned schema.
 *
 * <p>{@code MEDIUMBLOB} wherever an inventory is stored, for the same reason as
 * {@code hcf_king_stashes}: MySQL's {@code BLOB} stops at 65,535 bytes, which a
 * full inventory of filled shulker boxes passes, while {@code MEDIUMBLOB} goes to
 * 16 MB (MySQL 8.4 reference manual, "String Type Storage Requirements"). SQLite
 * takes the same declaration, any type containing "BLOB" getting BLOB affinity
 * (sqlite.org, "Datatypes In SQLite", section 3.1, rule 3).
 */
public final class StaffSchema {

    public static final String MODULE = "staff";

    private StaffSchema() {
    }

    private static final Migration V1 = Migration.of(MODULE, 1,
            "survival inventories held during staff mode",
            List.of("""
                    CREATE TABLE IF NOT EXISTS hcf_staff_stashes (
                        player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                        contents MEDIUMBLOB NOT NULL
                    )"""));

    /**
     * Moderation bans, which have no expiry: a row exists exactly while somebody is
     * banned, and staff lifting it deletes the row.
     *
     * <p>Deliberately not in the deathban table - see {@link StaffBan} for why a
     * gameplay switch must not be able to disable a moderation hold.
     */
    private static final Migration V2 = Migration.of(MODULE, 2,
            "moderation bans with no expiry",
            List.of("""
                    CREATE TABLE IF NOT EXISTS hcf_staff_bans (
                        player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                        reason VARCHAR(255) NOT NULL,
                        banned_by VARCHAR(64) NOT NULL,
                        banned_at BIGINT NOT NULL
                    )"""));

    /**
     * Inventories captured at death, for {@code /lastinv}.
     *
     * <p>Keyed by player and instant, so a player has one row per death and the
     * newest few are kept. Two deaths in the same millisecond would collide on the
     * key; a player cannot die twice in one millisecond, and the alternative - a
     * generated id - would need a portable auto-increment, which MySQL and SQLite
     * spell differently.
     */
    private static final Migration V3 = Migration.of(MODULE, 3,
            "inventories captured at death",
            List.of("""
                    CREATE TABLE IF NOT EXISTS hcf_staff_last_inventories (
                        player_uuid VARCHAR(36) NOT NULL,
                        died_at BIGINT NOT NULL,
                        contents MEDIUMBLOB NOT NULL,
                        PRIMARY KEY (player_uuid, died_at)
                    )"""));

    /**
     * Reports and requests. They share one table because they are one thing with a
     * different word on the front - somebody wants staff attention, here is why -
     * and splitting them would double the storage and the review for no gain.
     *
     * <p>Closed tickets stay here and leave memory; the history is the point of
     * storing them at all.
     */
    private static final Migration V4 = Migration.of(MODULE, 4,
            "reports and requests",
            List.of("""
                    CREATE TABLE IF NOT EXISTS hcf_staff_tickets (
                        id BIGINT NOT NULL PRIMARY KEY,
                        type VARCHAR(16) NOT NULL,
                        opened_by VARCHAR(36) NOT NULL,
                        opened_name VARCHAR(32) NOT NULL,
                        target_uuid VARCHAR(36),
                        target_name VARCHAR(32),
                        message VARCHAR(512) NOT NULL,
                        opened_at BIGINT NOT NULL,
                        status VARCHAR(16) NOT NULL,
                        handled_by VARCHAR(32)
                    )"""));

    /**
     * Strikes.
     *
     * <p>An expired strike is kept rather than deleted: staff reviewing an appeal
     * need the whole history, not only the part still in force.
     */
    private static final Migration V5 = Migration.of(MODULE, 5,
            "strikes against players",
            List.of("""
                    CREATE TABLE IF NOT EXISTS hcf_staff_strikes (
                        id BIGINT NOT NULL PRIMARY KEY,
                        player_uuid VARCHAR(36) NOT NULL,
                        player_name VARCHAR(32) NOT NULL,
                        reason VARCHAR(512) NOT NULL,
                        issued_by VARCHAR(32) NOT NULL,
                        issued_at BIGINT NOT NULL,
                        expires_at BIGINT NOT NULL
                    )"""));

    /**
     * Strikes are against teams, not players: the project owner's definition
     * (12/09/2026) - a faction punished when a member is banned for cheating. The
     * player table of version 5 was never released, and its rows would name the
     * wrong thing, so it is dropped rather than converted.
     */
    private static final Migration V6 = Migration.of(MODULE, 6,
            "strikes against teams",
            List.of("DROP TABLE IF EXISTS hcf_staff_strikes",
                    """
                    CREATE TABLE IF NOT EXISTS hcf_team_strikes (
                        id BIGINT NOT NULL PRIMARY KEY,
                        team_uuid VARCHAR(36) NOT NULL,
                        team_name VARCHAR(32) NOT NULL,
                        subject VARCHAR(32) NOT NULL,
                        reason VARCHAR(512) NOT NULL,
                        issued_by VARCHAR(32) NOT NULL,
                        issued_at BIGINT NOT NULL,
                        expires_at BIGINT NOT NULL
                    )"""));

    public static List<Migration> migrations() {
        return List.of(V1, V2, V3, V4, V5, V6);
    }
}
