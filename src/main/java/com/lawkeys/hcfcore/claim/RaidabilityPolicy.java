package com.lawkeys.hcfcore.claim;

import java.util.UUID;

/**
 * Answers the one question the claim module cannot answer itself: is this team's
 * territory currently open to raiding?
 *
 * <p>This is the seam between {@code claim/} and {@code dtr/}. FEATURES.md
 * section 3 specifies that a team's protection is a <em>dynamic state derived
 * from its current DTR</em>, never a change of ownership - so the claim module
 * asks this question on every protection check rather than storing a
 * "raidable" flag of its own.
 *
 * <p>The {@code dtr/} module (CONTRIBUTING.md section 4, priority 3) will supply the
 * real implementation, returning {@code true} while a team's DTR sits at zero.
 * Until it lands, {@link #NEVER} is installed: claims are simply always
 * protected. That is a deliberately conservative default - the alternative,
 * guessing at DTR behaviour here, would put raid rules in the wrong module.
 */
@FunctionalInterface
public interface RaidabilityPolicy {

    /**
     * @param teamId the team owning the territory
     * @return {@code true} if that team's claims are currently raidable
     */
    boolean isRaidable(UUID teamId);

    /** Nothing is ever raidable. Active until the {@code dtr/} module is implemented. */
    RaidabilityPolicy NEVER = teamId -> false;
}
