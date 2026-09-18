package com.lawkeys.hcfcore.limiter;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Turns {@code limiters.yml} into an immutable {@link LimiterSettings}.
 *
 * <p>Invalid values are reported and replaced by the built-in default rather than
 * taking the server down (ARCHITECTURE.md section 6).
 */
public final class LimiterSettingsLoader {

    private LimiterSettingsLoader() {
    }

    public static LimiterSettings load(ConfigurationSection section, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        LimiterSettings defaults = LimiterSettings.defaults();
        if (section == null) {
            warn.accept("limiters.yml is missing or empty - no limits are applied.");
            return defaults;
        }
        ConfigurationSection enchantments = section.getConfigurationSection("enchantments");
        ConfigurationSection potions = section.getConfigurationSection("potions");
        return new LimiterSettings(
                section.getBoolean("enabled", defaults.enabled()),
                LevelCaps.of(readCaps(enchantments, "enchantments", warn),
                        message -> warn.accept("enchantments.caps: " + message)),
                enchantments == null
                        ? defaults.fixExistingItems()
                        : enchantments.getBoolean("fix-existing-items", defaults.fixExistingItems()),
                LevelCaps.of(readCaps(potions, "potions", warn),
                        message -> warn.accept("potions.caps: " + message)));
    }

    private static Map<String, Integer> readCaps(ConfigurationSection parent, String path,
                                                 Consumer<String> warn) {
        Map<String, Integer> caps = new LinkedHashMap<>();
        ConfigurationSection section = parent == null ? null : parent.getConfigurationSection("caps");
        if (section == null) {
            return caps;
        }
        for (String name : section.getKeys(false)) {
            if (!section.isInt(name)) {
                warn.accept(path + ".caps." + name + " is not a whole number; ignored.");
                continue;
            }
            caps.put(name, section.getInt(name));
        }
        return caps;
    }
}
