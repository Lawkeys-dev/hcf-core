package com.lawkeys.hcfcore.events.slide;

import com.lawkeys.hcfcore.events.CaptureEventDefinition;
import com.lawkeys.hcfcore.events.EventSettings;
import com.lawkeys.hcfcore.events.EventSettingsLoader;
import com.lawkeys.hcfcore.events.conquest.ConquestDefinition;
import com.lawkeys.hcfcore.events.conquest.ConquestSettings;
import com.lawkeys.hcfcore.events.core.CoreEventDefinition;
import com.lawkeys.hcfcore.events.core.CoreSettings;
import com.lawkeys.hcfcore.events.king.KingEventDefinition;
import com.lawkeys.hcfcore.events.king.KingSettings;
import com.lawkeys.hcfcore.util.Cuboid;
import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reads the {@code slide:} section of {@code events.yml}.
 *
 * <p>Ids are shared with every other family; this loader sees the capture
 * events, Kill the King, Conquest and the core events, since {@code EventModule}
 * loads it last.
 */
public final class SlideSettingsLoader {

    private SlideSettingsLoader() {
    }

    public static SlideSettings load(ConfigurationSection root, EventSettings events, KingSettings king,
                                     ConquestSettings conquest, CoreSettings core, Consumer<String> warn) {
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(king, "king");
        Objects.requireNonNull(conquest, "conquest");
        Objects.requireNonNull(core, "core");
        Objects.requireNonNull(warn, "warn");
        Set<String> taken = new HashSet<>();
        for (CaptureEventDefinition capture : events.definitions()) {
            taken.add(capture.id().toLowerCase(Locale.ROOT));
        }
        for (KingEventDefinition definition : king.definitions()) {
            taken.add(definition.id().toLowerCase(Locale.ROOT));
        }
        for (ConquestDefinition definition : conquest.definitions()) {
            taken.add(definition.id().toLowerCase(Locale.ROOT));
        }
        for (CoreEventDefinition definition : core.definitions()) {
            taken.add(definition.id().toLowerCase(Locale.ROOT));
        }

        List<SlideDefinition> definitions = new ArrayList<>();
        ConfigurationSection section = root == null ? null : root.getConfigurationSection("slide");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                if (!taken.add(id.toLowerCase(Locale.ROOT))) {
                    warn.accept("slide '" + id + "' has the id of another event (ids are case-insensitive and "
                            + "shared by every kind of event); skipped.");
                    continue;
                }
                ConfigurationSection entry = section.getConfigurationSection(id);
                if (entry == null) {
                    warn.accept("slide '" + id + "' is not a section; skipped.");
                    continue;
                }
                SlideDefinition definition = loadDefinition(id, entry, warn);
                if (definition != null) {
                    definitions.add(definition);
                }
            }
        }
        return new SlideSettings(events.enabled(), events.timeZone(), definitions);
    }

    /** @return the definition, or {@code null} after saying why the entry is unusable */
    private static SlideDefinition loadDefinition(String id, ConfigurationSection entry, Consumer<String> warn) {
        String world = entry.getString("world");
        if (world == null || world.isBlank()) {
            warn.accept("slide '" + id + "' has no world; skipped.");
            return null;
        }
        ConfigurationSection first = entry.getConfigurationSection("corner-1");
        ConfigurationSection second = entry.getConfigurationSection("corner-2");
        if (first == null || second == null) {
            warn.accept("slide '" + id + "' needs both corner-1 and corner-2; skipped.");
            return null;
        }
        Cuboid zone = Cuboid.between(world,
                first.getInt("x"), first.getInt("y"), first.getInt("z"),
                second.getInt("x"), second.getInt("y"), second.getInt("z"));

        int pointsPerPlayer = entry.getInt("points-per-player", 0);
        long interval = Durations.capSeconds(entry.getLong("interval-seconds", 0L), "interval-seconds", warn);
        int deathPenalty = entry.getInt("death-penalty", 0);
        int pointsToWin = entry.getInt("points-to-win", 0);
        if (pointsPerPlayer <= 0 || interval <= 0 || pointsToWin <= 0) {
            warn.accept("slide '" + id + "' needs a positive points-per-player, interval-seconds and "
                    + "points-to-win; skipped.");
            return null;
        }
        if (deathPenalty < 0) {
            warn.accept("slide '" + id + "': death-penalty cannot be negative; using 0.");
            deathPenalty = 0;
        }
        boolean announceDeaths = entry.getBoolean("announce-deaths", true);

        long maxDuration = Durations.capSeconds(entry.getLong("max-duration-seconds", 0L), "max-duration-seconds", warn);
        if (maxDuration < 0) {
            warn.accept("slide '" + id + "': max-duration-seconds cannot be negative; using 0.");
            maxDuration = 0L;
        }

        String displayName = entry.getString("display-name", id);
        if (displayName == null || displayName.isBlank()) {
            displayName = id;
        }

        List<Integer> announceAt = new ArrayList<>();
        for (int mark : entry.getIntegerList("announce-at")) {
            if (mark > 0) {
                announceAt.add(mark);
            } else {
                warn.accept("slide '" + id + "': ignoring a non-positive announce-at entry.");
            }
        }

        try {
            return new SlideDefinition(id, displayName, zone, pointsPerPlayer, interval, deathPenalty, announceDeaths,
                    pointsToWin, announceAt,
                    EventSettingsLoader.loadSchedule(entry.getStringList("schedule"), id, warn),
                    maxDuration,
                    EventSettingsLoader.nonEmpty(entry.getStringList("reward-commands"), id, "reward-commands", warn));
        } catch (IllegalArgumentException e) {
            warn.accept("slide '" + id + "': " + e.getMessage() + "; skipped.");
            return null;
        }
    }
}
