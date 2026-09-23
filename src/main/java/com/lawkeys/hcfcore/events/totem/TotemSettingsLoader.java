package com.lawkeys.hcfcore.events.totem;

import com.lawkeys.hcfcore.events.EventSettings;
import com.lawkeys.hcfcore.events.EventSettingsLoader;
import com.lawkeys.hcfcore.util.Cuboid;
import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reads the {@code totem:} section of {@code events.yml}: every Totem and Mini
 * Totem, a Mini Totem being only a shorter column.
 *
 * <p>Ids are shared with every other kind of event; this loader is handed the ones
 * already taken, since it loads last.
 */
public final class TotemSettingsLoader {

    /** Every sword the game has, the tools a totem is broken with unless told otherwise. */
    public static final List<String> SWORDS = List.of("WOODEN_SWORD", "STONE_SWORD", "COPPER_SWORD", "IRON_SWORD",
            "GOLDEN_SWORD", "DIAMOND_SWORD", "NETHERITE_SWORD");

    private TotemSettingsLoader() {
    }

    /** @param taken the ids of every event already loaded, lower-cased; the Totems' own are added */
    public static TotemSettings load(ConfigurationSection root, EventSettings events, Set<String> taken,
                                     Consumer<String> warn) {
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(taken, "taken");
        Objects.requireNonNull(warn, "warn");
        List<TotemDefinition> definitions = new ArrayList<>();
        ConfigurationSection section = root == null ? null : root.getConfigurationSection("totem");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                if (!taken.add(id.toLowerCase(Locale.ROOT))) {
                    warn.accept("totem '" + id + "' has the id of another event (ids are case-insensitive and "
                            + "shared by every kind of event); skipped.");
                    continue;
                }
                ConfigurationSection entry = section.getConfigurationSection(id);
                if (entry == null) {
                    warn.accept("totem '" + id + "' is not a section; skipped.");
                    continue;
                }
                TotemDefinition definition = loadDefinition(id, entry, warn);
                if (definition != null) {
                    definitions.add(definition);
                }
            }
        }
        return new TotemSettings(events.enabled(), events.timeZone(), definitions);
    }

    private static TotemDefinition loadDefinition(String id, ConfigurationSection entry, Consumer<String> warn) {
        String world = entry.getString("world");
        if (world == null || world.isBlank()) {
            warn.accept("totem '" + id + "' has no world; skipped.");
            return null;
        }
        ConfigurationSection first = entry.getConfigurationSection("corner-1");
        ConfigurationSection second = entry.getConfigurationSection("corner-2");
        ConfigurationSection base = entry.getConfigurationSection("base");
        if (first == null || second == null || base == null) {
            warn.accept("totem '" + id + "' needs corner-1, corner-2 and base; skipped.");
            return null;
        }
        Cuboid zone = Cuboid.between(world,
                first.getInt("x"), first.getInt("y"), first.getInt("z"),
                second.getInt("x"), second.getInt("y"), second.getInt("z"));
        int height = entry.getInt("height", 5);
        if (height <= 0 || height > 64) {
            warn.accept("totem '" + id + "': height must be between 1 and 64; skipped.");
            return null;
        }
        String active = block(entry.getString("material", "QUARTZ_BLOCK"));
        String broken = block(entry.getString("broken-material", "BEDROCK"));
        String idle = block(entry.getString("idle-material", "BEDROCK"));
        if (active == null || broken == null || idle == null) {
            warn.accept("totem '" + id + "': material, broken-material and idle-material must be solid blocks "
                    + "(not air, not affected by gravity); skipped.");
            return null;
        }
        List<String> tools = new ArrayList<>();
        List<String> listed = entry.isList("tools") ? entry.getStringList("tools") : SWORDS;
        for (String tool : listed) {
            Material material = Material.matchMaterial(tool);
            if (material == null || !material.isItem()) {
                warn.accept("totem '" + id + "': '" + tool + "' in tools is not an item; ignored.");
            } else {
                tools.add(material.name());
            }
        }
        RivalBreak rival = RivalBreak.RESET;
        String rawRival = entry.getString("rival-break");
        if (rawRival != null && !rawRival.isBlank()) {
            try {
                rival = RivalBreak.valueOf(rawRival.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException e) {
                warn.accept("totem '" + id + "': rival-break '" + rawRival + "' is not RESET or RESET_AND_START; "
                        + "using RESET.");
            }
        }
        long maxDuration = Durations.capSeconds(entry.getLong("max-duration-seconds", 0L), "max-duration-seconds", warn);
        if (maxDuration < 0) {
            warn.accept("totem '" + id + "': max-duration-seconds cannot be negative; using 0.");
            maxDuration = 0L;
        }
        String displayName = entry.getString("display-name", id);
        if (displayName == null || displayName.isBlank()) {
            displayName = id;
        }
        try {
            return new TotemDefinition(id, displayName, zone, base.getInt("x"), base.getInt("y"), base.getInt("z"),
                    height, active, broken, idle, tools, entry.getBoolean("instant-break", false), rival,
                    entry.getBoolean("announce-breaks", true),
                    EventSettingsLoader.loadSchedule(entry.getStringList("schedule"), id, warn),
                    maxDuration,
                    EventSettingsLoader.nonEmpty(entry.getStringList("reward-commands"), id, "reward-commands", warn));
        } catch (IllegalArgumentException e) {
            warn.accept("totem '" + id + "': " + e.getMessage() + "; skipped.");
            return null;
        }
    }

    /** @return the block's name, or {@code null} when it is not a usable solid block */
    private static String block(String name) {
        Material material = name == null ? null : Material.matchMaterial(name);
        return material == null || !material.isBlock() || material.isAir() || material.hasGravity()
                ? null : material.name();
    }
}
