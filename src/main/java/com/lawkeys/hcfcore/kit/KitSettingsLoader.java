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
                section.getBoolean("layout-editor", defaults.layoutEditor()),
                loadAbilities(section.getConfigurationSection("abilities"), warn));
    }

    private static List<Ability> loadAbilities(ConfigurationSection section, Consumer<String> warn) {
        if (section == null) {
            return List.of();
        }
        List<Ability> abilities = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                warn.accept("abilities." + id + " is not a block of settings; ignored.");
                continue;
            }
            String material = entry.getString("material");
            if (material == null || material.isBlank()) {
                warn.accept("abilities." + id + " has no material; ignored.");
                continue;
            }
            Ability ability = new Ability(id, material, entry.getString("name"),
                    entry.getStringList("lore"),
                    Math.max(0L, Durations.capSeconds(entry.getLong("cooldown-seconds", 0L), "cooldown-seconds", warn)),
                    entry.getBoolean("consume", true),
                    entry.getStringList("commands"));
            if (!ability.hasEffect()) {
                // An item that looks like a tool and does nothing is exactly the ghost
                // behaviour ARCHITECTURE.md section 2 forbids.
                warn.accept("abilities." + id + " has no commands, so using it would do nothing; ignored.");
                continue;
            }
            abilities.add(ability);
        }
        return abilities;
    }
}
