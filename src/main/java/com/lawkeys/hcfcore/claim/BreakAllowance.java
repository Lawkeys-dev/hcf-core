package com.lawkeys.hcfcore.claim;

/**
 * Whether a block break is allowed through territory protection right now,
 * because it is the core of a DTC or Last Break run under way.
 *
 * <p>Declared here and answered by {@code events/}, in the manner of
 * {@link BuildOverride} and {@link RaidabilityPolicy}: until installed,
 * {@link #NONE} refuses nothing extra, so territory protection behaves exactly
 * as it always did. Only a break of the exact block is covered - placing,
 * bucket use and interaction on a Citadel-like claim around the core still
 * follow the ordinary rules.
 */
@FunctionalInterface
public interface BreakAllowance {

    /** @return whether breaking this exact block is allowed regardless of who owns the land */
    boolean allows(String world, int x, int y, int z);

    BreakAllowance NONE = (world, x, y, z) -> false;
}
