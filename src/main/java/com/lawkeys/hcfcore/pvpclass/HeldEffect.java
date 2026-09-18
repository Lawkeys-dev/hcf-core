package com.lawkeys.hcfcore.pvpclass;

import java.util.Objects;

/**
 * An effect handed out for as long as an item is held - the Bard's sugar, blaze
 * powder, iron. It is applied in pulses of {@code effect.seconds()}, renewed while
 * the item stays in hand, so letting go of it ends the effect within that time.
 *
 * @param radius blocks around the holder; unused for {@link ClassTarget#SELF}
 */
public record HeldEffect(ClassEffect effect, ClassTarget target, double radius) {

    public HeldEffect {
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(target, "target");
        radius = Math.max(0.0, radius);
    }
}
