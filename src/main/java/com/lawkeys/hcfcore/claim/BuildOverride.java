package com.lawkeys.hcfcore.claim;

import java.util.UUID;

/**
 * Whether a particular player may build through territory protection right now.
 *
 * <p>The permission {@code hcfcore.claim.bypass} answers "may this rank ignore
 * protection at all"; this answers "is this staff member currently choosing to".
 * The difference is the point of the staff-build toggle: a staff member with the
 * permission still wants to be stopped from breaking somebody's wall by accident
 * while they are only looking around.
 *
 * <p>Declared here and answered by a module written later ({@code staff/} owns the
 * toggle), in the manner of {@link RaidabilityPolicy} and {@link ClaimingPolicy};
 * until then {@link #NONE} changes nothing.
 */
@FunctionalInterface
public interface BuildOverride {

    /** @return whether this player may currently build anywhere */
    boolean allows(UUID playerId);

    BuildOverride NONE = playerId -> false;
}
