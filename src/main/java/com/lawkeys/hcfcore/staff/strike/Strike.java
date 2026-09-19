package com.lawkeys.hcfcore.staff.strike;

import java.util.Objects;
import java.util.UUID;

/**
 * One strike against a team - a faction punished for what one of its members did,
 * a cheater banned typically (the project owner's definition, 12/09/2026).
 *
 * @param teamName  the team's name when it was struck; kept so a team since renamed
 *                  or disbanded can still be looked up
 * @param subject   the member it was for, or blank
 * @param offence   the offence's id ({@code staff.yml}, {@code strikes.offences}), or blank
 *                  for a strike given before offences existed
 * @param reason    the details staff added, or blank - staff see them, players do not
 * @param expiresAt when this strike stops counting, or {@link #NEVER}
 */
public record Strike(long id, UUID teamId, String teamName, String subject, String offence, String reason,
                     String issuedBy, long issuedAt, long expiresAt) {

    /** A strike that never stops counting. */
    public static final long NEVER = Long.MAX_VALUE;

    /**
     * The width of the {@code reason} column. Cut here rather than refused by the
     * database, for the reason {@code Ticket.MAX_MESSAGE} gives.
     */
    public static final int MAX_REASON = 512;
    /** The width of the name columns. */
    public static final int MAX_NAME = 32;

    public Strike {
        Objects.requireNonNull(teamId, "teamId");
        teamName = cut(teamName == null ? "" : teamName, MAX_NAME);
        subject = cut(subject == null ? "" : subject, MAX_NAME);
        offence = cut(offence == null ? "" : offence, MAX_NAME);
        reason = cut(reason == null ? "" : reason, MAX_REASON);
        issuedBy = cut(issuedBy == null ? "" : issuedBy, MAX_NAME);
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    public boolean isActiveAt(long now) {
        return now < expiresAt;
    }
}
