package com.lawkeys.hcfcore.staff;

import java.util.Objects;
import java.util.UUID;

/**
 * A moderation ban with no expiry, lifted only by staff.
 *
 * <p>Recorded when somebody disconnects to escape a freeze - the standard answer,
 * and the only one that makes a freeze mean anything.
 *
 * <p><strong>Why this is not a {@code Deathban}.</strong> It would have fitted the
 * same table, but {@code DeathbanManager} refuses to record or report a ban when
 * {@code pvp.yml} has deathbans switched off. A moderation ban must not be
 * disabled by a gameplay setting: an operator turning deathbans off for a kitmap
 * would otherwise silently turn off the enforcement of every freeze as well. A
 * death penalty and a moderation hold are different things and answer to different
 * switches.
 *
 * @param bannedBy the staff member who held them, for the record and the message
 */
public record StaffBan(UUID playerId, String reason, String bannedBy, long bannedAt) {

    public StaffBan {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(bannedBy, "bannedBy");
    }
}
