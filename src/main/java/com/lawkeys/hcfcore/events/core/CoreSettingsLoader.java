package com.lawkeys.hcfcore.events.core;

import com.lawkeys.hcfcore.events.CaptureEventDefinition;
import com.lawkeys.hcfcore.events.EventSettings;
import com.lawkeys.hcfcore.events.EventSettingsLoader;
import com.lawkeys.hcfcore.events.conquest.ConquestDefinition;
import com.lawkeys.hcfcore.events.conquest.ConquestSettings;
import com.lawkeys.hcfcore.events.king.KingEventDefinition;
import com.lawkeys.hcfcore.events.king.KingSettings;
import com.lawkeys.hcfcore.util.Cuboid;
import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reads the {@code dtc:} and {@code last-break:} sections of {@code events.yml} -
 * one engine, two sections, since Last Break has no {@code counter} key.
 *
 * <p>Ids are shared with the capture events, Kill the King and Conquest, checked
 * in the order {@code EventModule} loads the families in: this loader sees
 * theirs, {@code SlideSettingsLoader} sees this one's besides.
 */
public final class CoreSettingsLoader {

    private CoreSettingsLoader() {
    }

    public static CoreSettings load(ConfigurationSection root, EventSettings events, KingSettings king,
                                    ConquestSettings conquest, Consumer<String> warn) {
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(king, "king");
        Objects.requireNonNull(conquest, "conquest");
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

        List<CoreEventDefinition> definitions = new ArrayList<>();
        loadSection(root, "dtc", CoreEventKind.DTC, taken, definitions, warn);
        loadSection(root, "last-break", CoreEventKind.LAST_BREAK, taken, definitions, warn);
        return new CoreSettings(events.enabled(), events.timeZone(), definitions);
    }

    private static void loadSection(ConfigurationSection root, String sectionName, CoreEventKind kind,
                                    Set<String> taken, List<CoreEventDefinition> definitions, Consumer<String> warn) {
        ConfigurationSection section = root == null ? null : root.getConfigurationSection(sectionName);
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            if (!taken.add(id.toLowerCase(Locale.ROOT))) {
                warn.accept(sectionName + " '" + id + "' has the id of another event (ids are case-insensitive "
                        + "and shared by every kind of event); skipped.");
                continue;
            }
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                warn.accept(sectionName + " '" + id + "' is not a section; skipped.");
                continue;
            }
            CoreEventDefinition definition = loadDefinition(id, kind, entry, warn);
            if (definition != null) {
                definitions.add(definition);
            }
        }
    }

    /** @return the definition, or {@code null} after saying why the entry is unusable */
    private static CoreEventDefinition loadDefinition(String id, CoreEventKind kind, ConfigurationSection entry,
                                                       Consumer<String> warn) {
        String world = entry.getString("world");
        if (world == null || world.isBlank()) {
            warn.accept("core event '" + id + "' has no world; skipped.");
            return null;
        }
        ConfigurationSection first = entry.getConfigurationSection("corner-1");
        ConfigurationSection second = entry.getConfigurationSection("corner-2");
        if (first == null || second == null) {
            warn.accept("core event '" + id + "' needs both corner-1 and corner-2; skipped.");
            return null;
        }
        Cuboid zone = Cuboid.between(world,
                first.getInt("x"), first.getInt("y"), first.getInt("z"),
                second.getInt("x"), second.getInt("y"), second.getInt("z"));

        ConfigurationSection core = entry.getConfigurationSection("core");
        if (core == null) {
            warn.accept("core event '" + id + "' has no core; skipped.");
            return null;
        }
        int coreX = core.getInt("x");
        int coreY = core.getInt("y");
        int coreZ = core.getInt("z");
        if (!zone.containsBlock(world, coreX, coreY, coreZ)) {
            warn.accept("core event '" + id + "': its core is outside its own zone; skipped.");
            return null;
        }
        String materialName = core.getString("material", "END_STONE");
        Material material = materialName == null ? null : Material.matchMaterial(materialName);
        if (material == null || !material.isBlock() || material.isAir() || material.hasGravity()) {
            warn.accept("core event '" + id + "': core.material '" + materialName
                    + "' is not a usable solid block (not air, not affected by gravity); skipped.");
            return null;
        }
        String idleName = core.getString("idle-material", "BEDROCK");
        Material idle = idleName == null ? null : Material.matchMaterial(idleName);
        if (idle == null || !idle.isBlock() || idle.isAir() || idle.hasGravity()) {
            warn.accept("core event '" + id + "': core.idle-material '" + idleName
                    + "' is not a usable solid block; using BEDROCK.");
            idle = Material.BEDROCK;
        }

        int breaks = entry.getInt("breaks", 0);
        if (breaks <= 0) {
            warn.accept("core event '" + id + "' needs a positive breaks; skipped.");
            return null;
        }
        long cooldown = Durations.capSeconds(entry.getLong("break-cooldown-seconds", 1L), "break-cooldown-seconds", warn);
        if (cooldown < 0) {
            warn.accept("core event '" + id + "': break-cooldown-seconds cannot be negative; using 1.");
            cooldown = 1L;
        }

        CounterMode counter = CounterMode.SHARED;
        if (kind == CoreEventKind.DTC) {
            String raw = entry.getString("counter");
            if (raw != null && !raw.isBlank()) {
                try {
                    counter = CounterMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    warn.accept("core event '" + id + "': counter '" + raw + "' is not SHARED or PER_TEAM; "
                            + "using SHARED.");
                }
            }
        }

        long maxDuration = Durations.capSeconds(entry.getLong("max-duration-seconds", 0L), "max-duration-seconds", warn);
        if (maxDuration < 0) {
            warn.accept("core event '" + id + "': max-duration-seconds cannot be negative; using 0.");
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
                warn.accept("core event '" + id + "': ignoring a non-positive announce-at entry.");
            }
        }

        try {
            return new CoreEventDefinition(id, kind, displayName, zone, coreX, coreY, coreZ, material.name(),
                    idle.name(),
                    counter, breaks, cooldown, announceAt,
                    EventSettingsLoader.loadSchedule(entry.getStringList("schedule"), id, warn),
                    maxDuration,
                    EventSettingsLoader.nonEmpty(entry.getStringList("reward-commands"), id, "reward-commands", warn));
        } catch (IllegalArgumentException e) {
            warn.accept("core event '" + id + "': " + e.getMessage() + "; skipped.");
            return null;
        }
    }
}
