package com.lawkeys.hcfcore.ability;

import java.util.Objects;

/**
 * An effect an ability gives: {@code {effect: strength, level: 2, seconds: 8}} in
 * {@code abilities.yml}.
 *
 * @param effect  the effect's key, without namespace: {@code strength}
 * @param level   1 is level I
 * @param seconds how long it lasts
 */
public record AbilityEffect(String effect, int level, int seconds) {

    public static final int MAX_LEVEL = 255;

    public AbilityEffect {
        Objects.requireNonNull(effect, "effect");
        level = Math.max(1, Math.min(MAX_LEVEL, level));
        seconds = Math.max(1, seconds);
    }

    /** @return the amplifier the game uses: level I is 0 */
    public int amplifier() {
        return level - 1;
    }
}
