package com.lawkeys.hcfcore.resourcenode;

import com.lawkeys.hcfcore.util.Durations;
import com.lawkeys.hcfcore.util.Cuboid;
import org.bukkit.configuration.ConfigurationSection;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Turns {@code resourcenodes.yml} into an immutable {@link ResourceNodeSettings}.
 *
 * <p>Same contract as the other loaders in this plugin: a malformed entry is
 * reported and skipped, never fatal. One typo in one corner should cost an
 * operator that mountain, not their server (ARCHITECTURE.md section 6).
 */
public final class ResourceNodeSettingsLoader {

    /**
     * Region size past which the loader speaks up.
     *
     * <p>Not a refusal - some servers really do run a mountain that big, and the
     * work is spread over ticks either way. But a region this large is far more
     * often a corner typed with an extra digit, and finding that out from a
     * warning beats finding it out from a refill that runs for an hour.
     */
    private static final long LARGE_REGION_BLOCKS = 2_000_000L;

    private ResourceNodeSettingsLoader() {
    }

    public static ResourceNodeSettings load(ConfigurationSection section, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        ResourceNodeSettings defaults = ResourceNodeSettings.defaults();
        if (section == null) {
            warn.accept("resourcenodes.yml is missing or empty - no node will refill.");
            return defaults;
        }

        long tickSeconds = Durations.capSeconds(section.getLong("tick-seconds", defaults.tickSeconds()), "tick-seconds", warn);
        if (tickSeconds <= 0) {
            warn.accept("tick-seconds must be at least 1; using " + defaults.tickSeconds() + ".");
            tickSeconds = defaults.tickSeconds();
        }

        int blocksPerTick = section.getInt("blocks-per-tick", defaults.blocksPerTick());
        if (blocksPerTick <= 0) {
            warn.accept("blocks-per-tick must be at least 1; using " + defaults.blocksPerTick() + ".");
            blocksPerTick = defaults.blocksPerTick();
        }

        return new ResourceNodeSettings(
                section.getBoolean("enabled", defaults.enabled()),
                tickSeconds,
                loadZone(section.getString("time-zone", "system"), warn),
                blocksPerTick,
                section.getBoolean("apply-physics", defaults.applyPhysics()),
                section.getBoolean("skip-occupied-blocks", defaults.skipOccupiedBlocks()),
                loadNodes(section.getConfigurationSection("nodes"), warn));
    }

    private static ZoneId loadZone(String raw, Consumer<String> warn) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("system")) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(raw.trim());
        } catch (DateTimeException e) {
            warn.accept("time-zone '" + raw + "' is not a known zone id; using the system zone ("
                    + ZoneId.systemDefault() + "). Refill times will follow the host's clock.");
            return ZoneId.systemDefault();
        }
    }

    private static List<ResourceNodeDefinition> loadNodes(ConfigurationSection section,
                                                          Consumer<String> warn) {
        List<ResourceNodeDefinition> nodes = new ArrayList<>();
        if (section == null) {
            return nodes;
        }
        // Ids are matched case-insensitively everywhere downstream, so two entries
        // differing only by case would make lookups ambiguous and leave one of them
        // permanently inert. Refuse the second and say why.
        Set<String> seen = new HashSet<>();
        for (String id : section.getKeys(false)) {
            if (!seen.add(id.toLowerCase(Locale.ROOT))) {
                warn.accept("node '" + id + "' collides with an earlier node whose id differs "
                        + "only by case; skipped. Node ids are case-insensitive.");
                continue;
            }
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                warn.accept("node '" + id + "' is not a section; skipped.");
                continue;
            }
            ResourceNodeDefinition node = loadNode(id, entry, warn);
            if (node != null) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    /** @return the definition, or {@code null} when the entry is unusable */
    private static ResourceNodeDefinition loadNode(String id, ConfigurationSection entry,
                                                   Consumer<String> warn) {
        String world = entry.getString("world");
        if (world == null || world.isBlank()) {
            warn.accept("node '" + id + "' has no world; skipped.");
            return null;
        }
        ConfigurationSection first = entry.getConfigurationSection("corner-1");
        ConfigurationSection second = entry.getConfigurationSection("corner-2");
        if (first == null || second == null) {
            warn.accept("node '" + id + "' needs both corner-1 and corner-2; skipped.");
            return null;
        }

        BlockPalette palette = loadPalette(entry, id, warn);
        if (palette.isEmpty()) {
            // A node with nothing to place is not a node. Refusing it here keeps the
            // definition's own invariant honest instead of throwing out of a reload.
            warn.accept("node '" + id + "' lists no blocks to refill with; skipped.");
            return null;
        }

        Cuboid region = Cuboid.between(world,
                first.getInt("x"), first.getInt("y"), first.getInt("z"),
                second.getInt("x"), second.getInt("y"), second.getInt("z"));
        if (region.blockCount() > LARGE_REGION_BLOCKS) {
            warn.accept("node '" + id + "' covers " + region.blockCount() + " blocks ("
                    + region + "). That is allowed, but check the corners: a refill that "
                    + "large takes a while even spread over ticks.");
        }

        String displayName = entry.getString("display-name", id);
        if (displayName == null || displayName.isBlank()) {
            displayName = id;
        }

        RefillSchedule schedule = loadSchedule(entry.getConfigurationSection("refill"), id, warn);
        ConfigurationSection protection = entry.getConfigurationSection("protection");

        return new ResourceNodeDefinition(
                id,
                displayName,
                region,
                palette,
                loadTargets(entry.getConfigurationSection("replace")),
                schedule,
                loadMarks(entry.getLongList("announce-before-seconds"), id, schedule, warn),
                entry.getBoolean("announce-refill", true),
                entry.getBoolean("fill-on-start", false),
                protection == null || protection.getBoolean("prevent-build", true),
                loadBreakPolicy(protection == null ? null : protection.getString("break-policy"),
                        id, warn),
                protection == null || protection.getBoolean("prevent-claim", true),
                protection == null || protection.getBoolean("prevent-explosions", true));
    }

    /**
     * Reads the palette in either of its two shapes: a plain list of block names,
     * all equally likely, or a section of name-to-weight for a mix.
     */
    private static BlockPalette loadPalette(ConfigurationSection entry, String id,
                                            Consumer<String> warn) {
        List<BlockPalette.Entry> entries = new ArrayList<>();
        ConfigurationSection weighted = entry.getConfigurationSection("blocks");
        if (weighted != null) {
            for (String material : weighted.getKeys(false)) {
                // Checked here rather than left to the Entry constructor: that one
                // throws, and a throw out of a loader would take the whole reload
                // down over one blank YAML key - the opposite of this class's
                // contract.
                if (BlockPalette.normalise(material).isEmpty()) {
                    warn.accept("node '" + id + "': ignoring a blank block name.");
                    continue;
                }
                int weight = weighted.getInt(material, 1);
                if (weight <= 0) {
                    warn.accept("node '" + id + "': weight for " + material
                            + " must be positive; ignored.");
                    continue;
                }
                entries.add(new BlockPalette.Entry(material, weight));
            }
            return new BlockPalette(entries);
        }
        for (String material : entry.getStringList("blocks")) {
            if (material == null || material.isBlank()) {
                warn.accept("node '" + id + "': ignoring an empty blocks entry.");
                continue;
            }
            entries.add(new BlockPalette.Entry(material, 1));
        }
        return new BlockPalette(entries);
    }

    private static RefillTargets loadTargets(ConfigurationSection section) {
        if (section == null) {
            return RefillTargets.airOnly();
        }
        return new RefillTargets(
                section.getBoolean("air", true),
                BlockPalette.normaliseAll(section.getStringList("blocks")));
    }

    private static RefillSchedule loadSchedule(ConfigurationSection section, String id,
                                               Consumer<String> warn) {
        if (section == null) {
            return RefillSchedule.none();
        }
        long interval = Durations.capSeconds(section.getLong("interval-seconds", 0L), "interval-seconds", warn);
        if (interval < 0) {
            warn.accept("node '" + id + "': interval-seconds cannot be negative; ignored.");
            interval = 0L;
        }
        if (interval > 0 && interval < 60) {
            warn.accept("node '" + id + "': an interval of " + interval + "s is almost certainly "
                    + "a mistake - a refill that frequent is a block generator, not an event.");
        }
        if (interval >= 86_400) {
            warn.accept("node '" + id + "': interval-seconds is a day or more. Intervals are "
                    + "anchored to local midnight, so anything that long only ever fires at "
                    + "midnight; use refill.times for a schedule that sparse.");
        }

        List<LocalTime> times = new ArrayList<>();
        for (String value : section.getStringList("times")) {
            // A bare "-" in a YAML list is a null entry; trimming it would throw an
            // NPE the parse catch below does not cover, and take the reload with it.
            if (value == null || value.isBlank()) {
                warn.accept("node '" + id + "': ignoring an empty refill time.");
                continue;
            }
            try {
                times.add(LocalTime.parse(value.trim()));
            } catch (DateTimeParseException e) {
                warn.accept("node '" + id + "': refill time '" + value
                        + "' is not a HH:mm time; ignored.");
            }
        }
        return new RefillSchedule(interval, times);
    }

    private static List<Long> loadMarks(List<Long> raw, String id, RefillSchedule schedule,
                                        Consumer<String> warn) {
        List<Long> marks = new ArrayList<>();
        for (Long mark : raw) {
            if (mark == null || mark <= 0) {
                warn.accept("node '" + id + "': ignoring a non-positive announce-before-seconds "
                        + "entry.");
                continue;
            }
            if (schedule.intervalSeconds() > 0 && mark >= schedule.intervalSeconds()) {
                warn.accept("node '" + id + "': a warning " + mark + "s ahead is at least as long "
                        + "as the refill interval, so it would be announced constantly; ignored.");
                continue;
            }
            marks.add(mark);
        }
        return marks;
    }

    private static BreakPolicy loadBreakPolicy(String raw, String id, Consumer<String> warn) {
        if (raw == null || raw.isBlank()) {
            return BreakPolicy.PALETTE_ONLY;
        }
        try {
            return BreakPolicy.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warn.accept("node '" + id + "': break-policy '" + raw + "' is not one of PALETTE_ONLY "
                    + "or ANY; using PALETTE_ONLY.");
            return BreakPolicy.PALETTE_ONLY;
        }
    }
}
