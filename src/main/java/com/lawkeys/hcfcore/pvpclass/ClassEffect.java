package com.lawkeys.hcfcore.pvpclass;

import java.util.Locale;
import java.util.Objects;

/**
 * A potion effect a class hands out: which one, at which level, for how long.
 *
 * @param effect  the effect's key, as the server names it ({@code speed}, {@code strength})
 * @param level   1 for level I; the amplifier is {@code level - 1}
 * @param seconds how long one application lasts; unused for a passive effect, which
 *                lasts as long as the class
 */
public record ClassEffect(String effect, int level, int seconds) {

    /** Minecraft stores an amplifier in a byte: level 256 would wrap around. */
    public static final int MAX_LEVEL = 255;
    /** The longest duration a potion effect can carry, in seconds: its ticks are an int. */
    public static final int MAX_SECONDS = Integer.MAX_VALUE / 20;

    public ClassEffect {
        Objects.requireNonNull(effect, "effect");
        effect = normaliseKey(effect);
        if (level < 1 || level > MAX_LEVEL) {
            throw new IllegalArgumentException("level must be 1 to " + MAX_LEVEL + ", was " + level);
        }
        // A potion effect's duration is an int of ticks: longer would wrap around.
        seconds = Math.max(0, Math.min(seconds, MAX_SECONDS));
    }

    public int amplifier() {
        return level - 1;
    }

    /** @return "Speed II" - the key read as words, the level in roman numerals up to 10 */
    public String displayName() {
        return displayName(effect) + " " + roman(level);
    }

    /** @return an effect key in the form the server's registry uses: lower case, no namespace */
    public static String normaliseKey(String key) {
        String trimmed = key.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("minecraft:") ? trimmed.substring("minecraft:".length()) : trimmed;
    }

    /** @return {@code fire_resistance} read as {@code Fire Resistance} */
    public static String displayName(String key) {
        StringBuilder out = new StringBuilder();
        for (String word : normaliseKey(key).split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    /** @return the level as a roman numeral up to 10, as a number beyond */
    public static String roman(int level) {
        return level >= 1 && level <= ROMAN.length ? ROMAN[level - 1] : String.valueOf(level);
    }
}
