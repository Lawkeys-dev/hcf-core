package com.lawkeys.hcfcore.events.conquest;

import com.lawkeys.hcfcore.util.Durations;
import com.lawkeys.hcfcore.events.CaptureEventDefinition;
import com.lawkeys.hcfcore.events.ContestPolicy;
import com.lawkeys.hcfcore.events.EventSettings;
import com.lawkeys.hcfcore.events.EventSettingsLoader;
import com.lawkeys.hcfcore.events.king.KingEventDefinition;
import com.lawkeys.hcfcore.events.king.KingSettings;
import com.lawkeys.hcfcore.util.Cuboid;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reads the {@code conquest:} section of {@code events.yml}.
 *
 * <p>Ids are shared with the capture events and Kill the King - {@code /events start
 * <id>} must mean exactly one thing - and a bad entry loses that entry, not the file.
 */
public final class ConquestSettingsLoader {

    private ConquestSettingsLoader() {
    }

    public static ConquestSettings load(ConfigurationSection root, EventSettings events, KingSettings king,
                                        Consumer<String> warn) {
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(king, "king");
        Objects.requireNonNull(warn, "warn");
        List<ConquestDefinition> definitions = new ArrayList<>();
        ConfigurationSection section = root == null ? null : root.getConfigurationSection("conquest");
        if (section != null) {
            Set<String> taken = new HashSet<>();
            for (CaptureEventDefinition capture : events.definitions()) {
                taken.add(capture.id().toLowerCase(Locale.ROOT));
            }
            for (KingEventDefinition definition : king.definitions()) {
                taken.add(definition.id().toLowerCase(Locale.ROOT));
            }
            for (String id : section.getKeys(false)) {
                if (!taken.add(id.toLowerCase(Locale.ROOT))) {
                    warn.accept("conquest '" + id + "' has the id of another event (ids are case-insensitive and "
                            + "shared with the capture events and Kill the King); skipped.");
                    continue;
                }
                ConfigurationSection entry = section.getConfigurationSection(id);
                if (entry == null) {
                    warn.accept("conquest '" + id + "' is not a section; skipped.");
                    continue;
                }
                ConquestDefinition definition = loadDefinition(id, entry, warn);
                if (definition != null) {
                    definitions.add(definition);
                }
            }
        }
        return new ConquestSettings(events.enabled(), events.timeZone(), events.teamlessPlayersContest(),
                definitions);
    }

    /** @return the definition, or {@code null} after saying why the entry is unusable */
    private static ConquestDefinition loadDefinition(String id, ConfigurationSection entry, Consumer<String> warn) {
        String world = entry.getString("world");
        if (world == null || world.isBlank()) {
            warn.accept("conquest '" + id + "' has no world; skipped.");
            return null;
        }
        List<ConquestZone> zones = new ArrayList<>();
        ConfigurationSection zoneSection = entry.getConfigurationSection("zones");
        if (zoneSection != null) {
            for (String zoneId : zoneSection.getKeys(false)) {
                ConfigurationSection zone = zoneSection.getConfigurationSection(zoneId);
                ConfigurationSection first = zone == null ? null : zone.getConfigurationSection("corner-1");
                ConfigurationSection second = zone == null ? null : zone.getConfigurationSection("corner-2");
                if (first == null || second == null) {
                    warn.accept("conquest '" + id + "': zone '" + zoneId + "' needs corner-1 and corner-2; skipped.");
                    continue;
                }
                zones.add(new ConquestZone(zoneId, zone.getString("display-name", zoneId), Cuboid.between(world,
                        first.getInt("x"), first.getInt("y"), first.getInt("z"),
                        second.getInt("x"), second.getInt("y"), second.getInt("z"))));
            }
        }
        if (zones.isEmpty()) {
            warn.accept("conquest '" + id + "' has no usable zone; skipped.");
            return null;
        }
        long capture = Durations.capSeconds(entry.getLong("capture-seconds", 0L), "capture-seconds", warn);
        int perCapture = entry.getInt("points-per-capture", 0);
        int toWin = entry.getInt("points-to-win", 0);
        int penalty = entry.getInt("death-penalty", 0);
        if (capture <= 0 || perCapture <= 0 || toWin <= 0 || penalty < 0) {
            warn.accept("conquest '" + id + "' needs a positive capture-seconds, points-per-capture and "
                    + "points-to-win, and a death-penalty of 0 or more; skipped.");
            return null;
        }
        long maxDuration = Durations.capSeconds(entry.getLong("max-duration-seconds", 0L), "max-duration-seconds", warn);
        if (maxDuration < 0 || (maxDuration > 0 && maxDuration < capture)) {
            warn.accept("conquest '" + id + "': max-duration-seconds must be 0 or at least capture-seconds; using 0.");
            maxDuration = 0L;
        }
        ContestPolicy policy = ContestPolicy.RESET;
        String rawPolicy = entry.getString("contest-policy");
        if (rawPolicy != null && !rawPolicy.isBlank()) {
            try {
                policy = ContestPolicy.valueOf(rawPolicy.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                warn.accept("conquest '" + id + "': contest-policy '" + rawPolicy + "' is not RESET or PAUSE; "
                        + "using RESET.");
            }
        }
        String displayName = entry.getString("display-name", id);
        return new ConquestDefinition(id, displayName == null || displayName.isBlank() ? id : displayName,
                zones, capture, perCapture, toWin, penalty, policy,
                EventSettingsLoader.loadSchedule(entry.getStringList("schedule"), id, warn),
                maxDuration,
                EventSettingsLoader.nonEmpty(entry.getStringList("reward-commands"), id, "reward-commands", warn));
    }
}
