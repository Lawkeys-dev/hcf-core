package com.lawkeys.hcfcore.general;

import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;
import java.util.function.Consumer;

/** Turns {@code general.yml} into an immutable {@link GeneralSettings}. */
public final class GeneralSettingsLoader {

    private GeneralSettingsLoader() {
    }

    public static GeneralSettings load(ConfigurationSection section, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        GeneralSettings defaults = GeneralSettings.defaults();
        if (section == null) {
            warn.accept("general.yml is missing or empty - using built-in defaults.");
            return defaults;
        }
        return new GeneralSettings(
                section.getBoolean("enabled", defaults.enabled()),
                loadSpawn(section.getConfigurationSection("spawn"), defaults.spawn(), warn),
                Math.max(0L, Durations.capSeconds(section.getLong("logout-seconds", defaults.logoutSeconds()), "logout-seconds", warn)),
                Math.max(1, section.getInt("rename-max-length", defaults.renameMaxLength())),
                section.getBoolean("private-messages", defaults.privateMessagesEnabled()));
    }

    private static GeneralSettings.SpawnRules loadSpawn(ConfigurationSection section,
                                                       GeneralSettings.SpawnRules defaults, Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        return new GeneralSettings.SpawnRules(
                section.getBoolean("enabled", defaults.enabled()),
                Objects.requireNonNullElse(section.getString("world"), defaults.world()),
                Math.max(0L, Durations.capSeconds(section.getLong("warmup-seconds", defaults.warmupSeconds()), "warmup-seconds", warn)));
    }
}
