package com.lawkeys.hcfcore.limiter;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The highest level each enchantment - or each potion effect - may reach.
 *
 * <p>Pure Java, keyed by the registry key as text ({@code minecraft:sharpness}), so
 * every rule is tested without a server. A cap of {@code 0} forbids the thing
 * outright; anything unlisted keeps vanilla's behaviour.
 *
 * <p><strong>A cap may be above vanilla's maximum.</strong> FEATURES.md section 12
 * asks for levels that can be capped <em>or exceeded</em>. Nothing in the game
 * creates a level above vanilla's maximum on its own, so exceeding only means
 * anything at the anvil: {@link #combine} is vanilla's combining rule with the cap in
 * place of the enchantment's own maximum, so two Sharpness V make Sharpness VI when
 * Sharpness is capped at 6.
 */
public final class LevelCaps {

    private static final LevelCaps NONE = new LevelCaps(Map.of());

    private final Map<String, Integer> caps;

    private LevelCaps(Map<String, Integer> caps) {
        this.caps = Map.copyOf(caps);
    }

    public static LevelCaps none() {
        return NONE;
    }

    /**
     * @param raw  names as an operator writes them - {@code sharpness},
     *             {@code minecraft:sharpness}, any case - to their caps
     * @param warn told about every entry that is dropped, and why
     */
    public static LevelCaps of(Map<String, Integer> raw, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        Map<String, Integer> checked = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : raw.entrySet()) {
            String key = normalize(entry.getKey());
            if (key == null) {
                warn.accept("an entry with no name was ignored.");
                continue;
            }
            Integer cap = entry.getValue();
            if (cap == null || cap < 0) {
                warn.accept("'" + entry.getKey() + "' has cap " + cap + "; a cap is 0 (forbidden) or more. Ignored.");
                continue;
            }
            if (checked.put(key, cap) != null) {
                warn.accept("'" + key + "' is listed twice; the last one wins.");
            }
        }
        return checked.isEmpty() ? NONE : new LevelCaps(checked);
    }

    /**
     * @return the key as the registry spells it - lower case, with the
     *         {@code minecraft} namespace when none is given - or {@code null} for a
     *         blank name
     */
    public static String normalize(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (key.startsWith(":")) {
            return "minecraft" + key;
        }
        return key.indexOf(':') >= 0 ? key : "minecraft:" + key;
    }

    public boolean isEmpty() {
        return caps.isEmpty();
    }

    /** @return every capped key, for checking against the server's registry */
    public Set<String> keys() {
        return caps.keySet();
    }

    public OptionalInt capOf(String key) {
        Integer cap = caps.get(key);
        return cap == null ? OptionalInt.empty() : OptionalInt.of(cap);
    }

    /**
     * Brings a level down to its cap. Never raises one.
     *
     * @return the level allowed; {@code 0} means the thing must go entirely
     */
    public int clamp(String key, int level) {
        Integer cap = caps.get(key);
        return cap == null ? level : Math.min(level, cap);
    }

    /**
     * The level an anvil should give an enchantment on its result.
     *
     * <p>Vanilla's rule - equal levels make one higher, unequal ones keep the higher
     * - with the cap standing in for the enchantment's own maximum, which is where
     * vanilla stops.
     *
     * @param left    the level on the item being worked on, {@code 0} if none
     * @param right   the level on the item or book sacrificed, {@code 0} if none
     * @param vanilla the level vanilla put on the result, kept when nothing is capped
     */
    public int combine(String key, int left, int right, int vanilla) {
        Integer cap = caps.get(key);
        if (cap == null) {
            return vanilla;
        }
        if (left <= 0 && right <= 0) {
            return Math.min(vanilla, cap);
        }
        int wanted = left == right ? left + 1 : Math.max(left, right);
        return Math.min(wanted, cap);
    }
}
