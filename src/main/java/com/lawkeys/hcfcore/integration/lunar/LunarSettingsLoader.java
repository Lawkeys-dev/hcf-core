package com.lawkeys.hcfcore.integration.lunar;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Reads {@code apollo.yml}; a bad value is a warning and the default, never a crash. */
public final class LunarSettingsLoader {

    private LunarSettingsLoader() {
    }

    public static LunarSettings load(ConfigurationSection section, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        LunarSettings defaults = LunarSettings.defaults();
        if (section == null) {
            warn.accept("apollo.yml is missing or empty - using built-in defaults.");
            return defaults;
        }
        return new LunarSettings(
                section.getBoolean("enabled", defaults.enabled()),
                section.getInt("update-ticks", defaults.updateTicks()),
                section.getLong("resend-seconds", defaults.resendSeconds()),
                waypoints(section.getConfigurationSection("waypoints"), defaults.waypoints(), warn),
                teamView(section.getConfigurationSection("team-view"), defaults.teamView(), warn),
                cooldowns(section.getConfigurationSection("cooldowns"), defaults.cooldowns()),
                nametags(section.getConfigurationSection("nametags"), defaults.nametags()));
    }

    private static LunarSettings.Waypoints waypoints(ConfigurationSection section, LunarSettings.Waypoints d,
                                                     Consumer<String> warn) {
        if (section == null) {
            return d;
        }
        ConfigurationSection colors = section.getConfigurationSection("colors");
        return new LunarSettings.Waypoints(
                section.getBoolean("enabled", d.enabled()),
                section.getBoolean("hq", d.hq()),
                section.getBoolean("base", d.base()),
                section.getBoolean("rally", d.rally()),
                section.getBoolean("focus", d.focus()),
                section.getBoolean("events", d.events()),
                section.getInt("event-height", d.eventHeight()),
                section.getBoolean("show-beam", d.showBeam()),
                section.getBoolean("highlight-block", d.highlightBlock()),
                color(colors, "hq", d.hqColor(), warn),
                color(colors, "base", d.baseColor(), warn),
                color(colors, "rally", d.rallyColor(), warn),
                color(colors, "focus", d.focusColor(), warn),
                color(colors, "event", d.eventColor(), warn));
    }

    private static LunarSettings.TeamView teamView(ConfigurationSection section, LunarSettings.TeamView d,
                                                   Consumer<String> warn) {
        if (section == null) {
            return d;
        }
        return new LunarSettings.TeamView(
                section.getBoolean("enabled", d.enabled()),
                color(section, "marker-color", d.markerColor(), warn),
                Math.max(0.0, section.getDouble("tracking-range", d.trackingRange())));
    }

    private static LunarSettings.Cooldowns cooldowns(ConfigurationSection section, LunarSettings.Cooldowns d) {
        if (section == null) {
            return d;
        }
        return new LunarSettings.Cooldowns(
                section.getBoolean("enabled", d.enabled()),
                section.getBoolean("combat-tag", d.combatTag()),
                section.getString("combat-tag-icon", d.combatTagIcon()).trim().toUpperCase(Locale.ROOT),
                section.getBoolean("warmups", d.warmups()),
                section.getString("warmup-icon", d.warmupIcon()).trim().toUpperCase(Locale.ROOT),
                section.getBoolean("abilities", d.abilities()),
                section.getString("ability-global-icon", d.abilityGlobalIcon()).trim().toUpperCase(Locale.ROOT),
                section.getBoolean("ender-pearl", d.enderPearl()),
                section.getString("ender-pearl-icon", d.enderPearlIcon()).trim().toUpperCase(Locale.ROOT),
                section.getBoolean("item-cooldowns", d.itemCooldowns()),
                section.getBoolean("classes", d.classes()),
                section.getBoolean("crowbar", d.crowbar()));
    }

    private static LunarSettings.Nametags nametags(ConfigurationSection section, LunarSettings.Nametags d) {
        if (section == null) {
            return d;
        }
        Map<NametagStyle.Relation, String> colors = new EnumMap<>(NametagStyle.Relation.class);
        colors.putAll(d.style().colors());
        ConfigurationSection configured = section.getConfigurationSection("colors");
        if (configured != null) {
            for (NametagStyle.Relation relation : NametagStyle.Relation.values()) {
                String key = relation.name().toLowerCase(Locale.ROOT);
                if (configured.isString(key)) {
                    colors.put(relation, configured.getString(key));
                }
            }
        }
        return new LunarSettings.Nametags(section.getBoolean("enabled", d.enabled()),
                new NametagStyle(section.getString("team-line", d.style().teamLine()),
                        section.getString("name-line", d.style().nameLine()), colors));
    }

    private static int color(ConfigurationSection section, String key, int fallback, Consumer<String> warn) {
        if (section == null || !section.isSet(key)) {
            return fallback;
        }
        String text = section.getString(key);
        var parsed = LunarSettings.parseRgb(text);
        if (parsed.isEmpty()) {
            warn.accept("'" + key + "' must be a colour like \"#FFAA00\", got '" + text + "'; using the default.");
            return fallback;
        }
        return parsed.getAsInt();
    }
}
