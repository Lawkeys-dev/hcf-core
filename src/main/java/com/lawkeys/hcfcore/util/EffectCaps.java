package com.lawkeys.hcfcore.util;

import org.bukkit.potion.PotionEffectType;

/**
 * The highest level an effect may have on a player, whatever gives it
 * ({@code limiters.yml}, {@code effects.caps}). Asked by the modules that give
 * effects themselves - the classes, custom enchants, the King - so that what they
 * give is already within the cap, and they still recognise it as their own.
 *
 * <p>Declared here, filled by {@code limiter/}: {@link #NONE} until then.
 */
@FunctionalInterface
public interface EffectCaps {

    /** No cap: every effect as asked. */
    EffectCaps NONE = (type, amplifier) -> amplifier;

    /**
     * @param amplifier the amplifier asked for ({@code 0} is level I)
     * @return the amplifier allowed, never above the one asked; {@code -1} when the
     *         effect is forbidden
     */
    int allowed(PotionEffectType type, int amplifier);
}
