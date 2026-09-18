package com.lawkeys.hcfcore.events;

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
 * Turns {@code events.yml} into an immutable {@link EventSettings}.
 *
 * <p>This is where ARCHITECTURE.md section 9's "events are configuration, not a
 * hardcoded list" actually happens: every definition is read from YAML, so a new
 * KOTH variant needs no recompile.
 *
 * <p>A malformed entry is reported and skipped, never fatal. An operator with one
 * typo in one zone should lose that event, not their server (ARCHITECTURE.md
 * section 6).
 */
public final class EventSettingsLoader {

    private EventSettingsLoader() {
    }

    public static EventSettings load(ConfigurationSection section, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        EventSettings defaults = EventSettings.defaults();
        if (section == null) {
            warn.accept("events.yml is missing or empty - no events will run.");
            return defaults;
        }

        long tickSeconds = Durations.capSeconds(section.getLong("tick-seconds", defaults.tickSeconds()), "tick-seconds", warn);
        if (tickSeconds <= 0) {
            warn.accept("tick-seconds must be at least 1; using " + defaults.tickSeconds() + ".");
            tickSeconds = defaults.tickSeconds();
        }

        Set<String> seen = new HashSet<>();
        List<CaptureEventDefinition> definitions =
                new ArrayList<>(loadDefinitions(section.getConfigurationSection("events"), seen, warn));
        List<CitadelDefinition> citadels = new ArrayList<>();
        loadCitadels(section.getConfigurationSection("citadels"), seen, definitions, citadels, warn);

        return new EventSettings(
                section.getBoolean("enabled", defaults.enabled()),
                tickSeconds,
                loadZone(section.getString("time-zone", "system"), warn),
                section.getBoolean("announce-contests", defaults.announceContests()),
                section.getBoolean("teamless-players-contest", defaults.teamlessPlayersContest()),
                definitions,
                citadels);
    }

    /**
     * The {@code citadels:} section: each entry is a capture event, read exactly like a
     * KOTH, plus the name of the server team whose land is the Citadel and what that
     * land refuses. The capture event joins the others, so the capture engine runs it
     * as it runs any KOTH.
     */
    private static void loadCitadels(ConfigurationSection section, Set<String> seen,
                                     List<CaptureEventDefinition> definitions, List<CitadelDefinition> citadels,
                                     Consumer<String> warn) {
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            if (!seen.add(id.toLowerCase(Locale.ROOT))) {
                warn.accept("citadel '" + id + "' has the id of another event (ids are case-insensitive and "
                        + "shared by every kind of event); skipped.");
                continue;
            }
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                warn.accept("citadel '" + id + "' is not a section; skipped.");
                continue;
            }
            String claim = entry.getString("claim", "");
            if (claim == null || claim.isBlank()) {
                warn.accept("citadel '" + id + "' names no claim - the server team whose land is the "
                        + "Citadel; skipped.");
                continue;
            }
            CaptureEventDefinition capture = loadDefinition(id, entry, warn);
            if (capture == null) {
                continue;
            }
            ConfigurationSection restrictions = entry.getConfigurationSection("restrictions");
            CitadelRules all = CitadelRules.ALL;
            CitadelRules rules = restrictions == null ? all : new CitadelRules(
                    restrictions.getBoolean("ender-pearls", all.enderPearls()),
                    restrictions.getBoolean("partner-items", all.partnerItems()),
                    restrictions.getBoolean("chorus-fruit", all.chorusFruit()),
                    restrictions.getBoolean("elytra", all.elytra()),
                    restrictions.getBoolean("riptide", all.riptide()));
            definitions.add(capture);
            citadels.add(new CitadelDefinition(id, claim.trim(), rules));
        }
    }

    private static ZoneId loadZone(String raw, Consumer<String> warn) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("system")) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(raw.trim());
        } catch (DateTimeException e) {
            warn.accept("time-zone '" + raw + "' is not a known zone id; using the system zone ("
                    + ZoneId.systemDefault() + "). Schedules will follow the host's clock.");
            return ZoneId.systemDefault();
        }
    }

    private static List<CaptureEventDefinition> loadDefinitions(ConfigurationSection section, Set<String> seen,
                                                               Consumer<String> warn) {
        List<CaptureEventDefinition> definitions = new ArrayList<>();
        if (section == null) {
            return definitions;
        }
        // Ids are matched case-insensitively downstream - EventManager keys its
        // running events by lowercase id and EventSettings#find uses
        // equalsIgnoreCase - so two entries differing only by case would make
        // lookups ambiguous and let only one of them ever run. Refuse the second
        // rather than let an operator wonder why half their config is inert.
        for (String id : section.getKeys(false)) {
            if (!seen.add(id.toLowerCase(Locale.ROOT))) {
                warn.accept("event '" + id + "' collides with an earlier event whose id differs "
                        + "only by case; skipped. Event ids are case-insensitive.");
                continue;
            }
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                warn.accept("event '" + id + "' is not a section; skipped.");
                continue;
            }
            CaptureEventDefinition definition = loadDefinition(id, entry, warn);
            if (definition != null) {
                definitions.add(definition);
            }
        }
        return definitions;
    }

    /** @return the definition, or {@code null} when the entry is unusable */
    private static CaptureEventDefinition loadDefinition(String id, ConfigurationSection entry,
                                                         Consumer<String> warn) {
        String world = entry.getString("world");
        if (world == null || world.isBlank()) {
            warn.accept("event '" + id + "' has no world; skipped.");
            return null;
        }
        ConfigurationSection first = entry.getConfigurationSection("corner-1");
        ConfigurationSection second = entry.getConfigurationSection("corner-2");
        if (first == null || second == null) {
            warn.accept("event '" + id + "' needs both corner-1 and corner-2; skipped.");
            return null;
        }

        long captureSeconds = Durations.capSeconds(entry.getLong("capture-seconds", 0L), "capture-seconds", warn);
        if (captureSeconds <= 0) {
            warn.accept("event '" + id + "' needs a positive capture-seconds; skipped.");
            return null;
        }

        Cuboid zone = Cuboid.between(world,
                first.getInt("x"), first.getInt("y"), first.getInt("z"),
                second.getInt("x"), second.getInt("y"), second.getInt("z"));

        long maxDuration = Durations.capSeconds(entry.getLong("max-duration-seconds", 0L), "max-duration-seconds", warn);
        if (maxDuration < 0) {
            warn.accept("event '" + id + "': max-duration-seconds cannot be negative; using 0 "
                    + "(runs until somebody wins).");
            maxDuration = 0L;
        }
        if (maxDuration > 0 && maxDuration < captureSeconds) {
            warn.accept("event '" + id + "': max-duration-seconds (" + maxDuration + ") is below "
                    + "capture-seconds (" + captureSeconds + "), so it could never be won; using 0.");
            maxDuration = 0L;
        }

        String displayName = entry.getString("display-name", id);
        if (displayName == null || displayName.isBlank()) {
            displayName = id;
        }

        return new CaptureEventDefinition(
                id,
                displayName,
                zone,
                captureSeconds,
                loadPolicy(entry.getString("contest-policy"), id, warn),
                maxDuration,
                loadMarks(entry.getLongList("announce-at-seconds"), id, warn),
                loadSchedule(entry.getStringList("schedule"), id, warn),
                nonEmpty(entry.getStringList("reward-commands"), id, "reward-commands", warn));
    }

    private static ContestPolicy loadPolicy(String raw, String id, Consumer<String> warn) {
        if (raw == null || raw.isBlank()) {
            return ContestPolicy.RESET;
        }
        try {
            return ContestPolicy.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warn.accept("event '" + id + "': contest-policy '" + raw + "' is not one of RESET or "
                    + "PAUSE; using RESET.");
            return ContestPolicy.RESET;
        }
    }

    public static List<Long> loadMarks(List<Long> raw, String id, Consumer<String> warn) {
        List<Long> marks = new ArrayList<>();
        for (Long mark : raw) {
            if (mark == null || mark <= 0) {
                warn.accept("event '" + id + "': ignoring a non-positive announce-at-seconds entry.");
                continue;
            }
            marks.add(mark);
        }
        return marks;
    }

    public static List<LocalTime> loadSchedule(List<String> raw, String id, Consumer<String> warn) {
        List<LocalTime> times = new ArrayList<>();
        for (String value : raw) {
            // A bare "-" in the YAML list is a null entry. Calling trim() on it would
            // throw an NPE that the DateTimeParseException catch below does not cover,
            // taking down the reload - the opposite of this loader's contract. The
            // sibling loadMarks already guarded its nulls; this one did not.
            if (value == null || value.isBlank()) {
                warn.accept("event '" + id + "': ignoring an empty schedule entry.");
                continue;
            }
            try {
                times.add(LocalTime.parse(value.trim()));
            } catch (DateTimeParseException e) {
                warn.accept("event '" + id + "': schedule entry '" + value
                        + "' is not a HH:mm time; ignored.");
            }
        }
        return times;
    }

    /**
     * @return the entries that are usable text
     *
     * <p>{@link List#copyOf} rejects null elements outright, so an empty YAML list
     * item reaching a definition's constructor would be a crash rather than a
     * skipped line.
     */
    public static List<String> nonEmpty(List<String> raw, String id, String field,
                                         Consumer<String> warn) {
        List<String> kept = new ArrayList<>();
        for (String value : raw) {
            if (value == null || value.isBlank()) {
                warn.accept("event '" + id + "': ignoring an empty " + field + " entry.");
                continue;
            }
            kept.add(value);
        }
        return kept;
    }
}
