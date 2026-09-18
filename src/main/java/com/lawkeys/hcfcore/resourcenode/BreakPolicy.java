package com.lawkeys.hcfcore.resourcenode;

/**
 * What players are allowed to mine inside a node's region.
 *
 * <p>FEATURES.md section 6 says a mountain is "non constructible" and that players
 * "can only mine the regenerated blocks". Building is always refusable on its own
 * ({@code prevent-build}); this enum is about the other half, and it exists
 * because the honest reading of that sentence has a real gameplay consequence
 * either way.
 */
public enum BreakPolicy {

    /**
     * Only the blocks the node itself places may be mined.
     *
     * <p>The structure around the resource - the netherrack a glowstone vein sits
     * in, say - survives. Without this, a week of raids leaves the mountain a hole
     * in the ground and the refill has nothing to fill.
     */
    PALETTE_ONLY,

    /** Anything inside the region may be mined. The region is then only non-buildable. */
    ANY;

    public boolean allows(BlockPalette palette, String material) {
        return this == ANY || palette.contains(material);
    }
}
