package com.lawkeys.hcfcore.kit;

import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Turns {@code kits.yml} into an immutable {@link KitSettings}. */
public final class KitSettingsLoader {

    private KitSettingsLoader() {
    }

    public static KitSettings load(ConfigurationSection section, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        KitSettings defaults = KitSettings.defaults();
        if (section == null) {
            warn.accept("kits.yml is missing or empty - using built-in defaults.");
            return defaults;
        }
        ConfigurationSection signs = section.getConfigurationSection("refill-signs");
        return new KitSettings(
                section.getBoolean("enabled", defaults.enabled()),
                signs == null ? defaults.signsEnabled() : signs.getBoolean("enabled", defaults.signsEnabled()),
                signs == null ? defaults.signLine()
                        : Objects.requireNonNullElse(signs.getString("line"), defaults.signLine()),
                signs == null ? defaults.signCooldownSeconds()
                        : Math.max(0L, Durations.capSeconds(signs.getLong("cooldown-seconds", defaults.signCooldownSeconds()), "cooldown-seconds", warn)),
                section.getBoolean("clear-before-giving", defaults.clearBeforeGiving()),
                section.getBoolean("layout-editor", defaults.layoutEditor()));
    }
}
