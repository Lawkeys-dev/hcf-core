package com.lawkeys.hcfcore.resourcenode;

import java.util.Objects;
import java.util.Set;

/**
 * Which blocks a refill is allowed to overwrite.
 *
 * <p>A refill is not a "set every block in the box" operation: by default it only
 * fills what players actually mined, which is why {@link #air()} alone is the
 * usual configuration. Naming extra materials lets an operator have the refill
 * repair a mountain players have replaced with cobblestone, without giving it
 * licence to overwrite the whole region.
 *
 * <p>Air is expressed as a flag rather than as a material name because the server
 * has several kinds of it (cave air, void air) and the exact set has changed
 * across versions; the server layer asks the block whether it is air instead of
 * matching names, so this stays correct without guessing at names
 * (CONTRIBUTING.md section 6).
 *
 * @param air       whether empty space is refilled
 * @param materials extra block names, normalised, that a refill may overwrite
 */
public record RefillTargets(boolean air, Set<String> materials) {

    public RefillTargets {
        materials = Set.copyOf(Objects.requireNonNull(materials, "materials"));
    }

    /** The default: a refill puts back what was mined, and touches nothing else. */
    public static RefillTargets airOnly() {
        return new RefillTargets(true, Set.of());
    }

    /**
     * @param material the block currently in place, normalised
     * @param isAir    what the server says about that block, since air has more
     *                 than one name
     * @return whether the refill may replace it
     */
    public boolean replaces(String material, boolean isAir) {
        if (isAir) {
            return air;
        }
        return materials.contains(BlockPalette.normalise(material));
    }
}
