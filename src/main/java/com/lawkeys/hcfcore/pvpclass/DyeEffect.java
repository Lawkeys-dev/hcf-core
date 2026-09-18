package com.lawkeys.hcfcore.pvpclass;

import java.util.Objects;

/**
 * An effect an arrow may add, depending on the colour the shooter's leather set is
 * dyed: a set dyed green, for instance, poisons.
 *
 * @param effect  applied to the player the arrow hits, for {@code effect.seconds()}
 * @param chance  in percent, 0 to 100: how likely one hit is to apply it
 */
public record DyeEffect(ClassEffect effect, double chance) {

    public DyeEffect {
        Objects.requireNonNull(effect, "effect");
        chance = Math.max(0.0, Math.min(100.0, chance));
    }

    /** @param roll a number from 0 (included) to 1 (excluded), drawn at random */
    public boolean applies(double roll) {
        return roll * 100.0 < chance;
    }
}
