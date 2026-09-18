package com.lawkeys.hcfcore.settings;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Turns {@code settings.yml} into an immutable {@link SettingsConfig}. */
public final class SettingsConfigLoader {

    private SettingsConfigLoader() {
    }

    public static SettingsConfig load(ConfigurationSection section, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        SettingsConfig defaults = SettingsConfig.defaults();
        if (section == null) {
            warn.accept("settings.yml is missing or empty - every setting is offered.");
            return defaults;
        }
        // Missing means every setting; an empty list means none, which an operator
        // can want - but a file written before a setting existed must not lose it.
        Set<PlayerSetting> offered = section.contains("offered")
                ? EnumSet.noneOf(PlayerSetting.class)
                : EnumSet.allOf(PlayerSetting.class);
        for (String key : section.getStringList("offered")) {
            PlayerSetting.byKey(key).ifPresentOrElse(offered::add,
                    () -> warn.accept("offered lists '" + key + "', which is not a setting; ignored."));
        }
        Set<String> cobblestone = defaults.cobblestone();
        if (section.contains("cobblestone.materials")) {
            cobblestone = new LinkedHashSet<>();
            for (String name : section.getStringList("cobblestone.materials")) {
                cobblestone.add(name.trim());
            }
        }
        return new SettingsConfig(section.getBoolean("enabled", defaults.enabled()), offered, cobblestone);
    }
}
