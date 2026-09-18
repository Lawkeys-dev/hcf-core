package com.lawkeys.hcfcore.pvpclass;

import java.util.Objects;

/**
 * An effect handed out once, on a right-click with an item - the stronger burst of a
 * Bard, a Rogue's jump, an Archer's speed.
 *
 * @param energyCost      energy spent; {@code 0} for a class without energy
 * @param cooldownSeconds wait before this item can be used again; {@code 0} for none
 * @param consume         whether one item is taken from the stack
 */
public record ClickEffect(ClassEffect effect, ClassTarget target, double radius,
                          int energyCost, long cooldownSeconds, boolean consume) {

    public ClickEffect {
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(target, "target");
        radius = Math.max(0.0, radius);
        energyCost = Math.max(0, energyCost);
        cooldownSeconds = Math.max(0L, cooldownSeconds);
    }
}
