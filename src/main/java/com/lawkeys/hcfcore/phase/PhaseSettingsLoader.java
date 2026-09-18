package com.lawkeys.hcfcore.phase;

import com.lawkeys.hcfcore.events.EventSettingsLoader;
import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.configuration.ConfigurationSection;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Turns {@code phases.yml} into an immutable {@link PhaseSettings}.
 *
 * <p>Same contract as the other loaders: a bad value is reported and replaced by
 * its default, never fatal. A scheduled date that cannot be read is dropped rather
 * than guessed at - a phase that starts at the wrong hour is worse than one staff
 * start by hand.
 */
public final class PhaseSettingsLoader {

    /** How dates are written in phases.yml, in its time zone. */
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private PhaseSettingsLoader() {
    }

    public static PhaseSettings load(ConfigurationSection section, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        PhaseSettings defaults = PhaseSettings.defaults();
        if (section == null) {
            warn.accept("phases.yml is missing or empty - SOTW and EOTW use their defaults and start only by command.");
            return defaults;
        }
        ZoneId zone = loadZone(section.getString("time-zone", "system"), warn);

        long sotwDuration = Durations.capSeconds(section.getLong("sotw.duration-seconds", defaults.sotwDurationSeconds()), "sotw.duration-seconds", warn);
        if (sotwDuration <= 0) {
            warn.accept("sotw.duration-seconds must be positive; using " + defaults.sotwDurationSeconds() + ".");
            sotwDuration = defaults.sotwDurationSeconds();
        }
        long eotwWindow = Durations.capSeconds(section.getLong("eotw.start-window-seconds", defaults.eotwStartWindowSeconds()), "eotw.start-window-seconds", warn);
        if (eotwWindow <= 0) {
            warn.accept("eotw.start-window-seconds must be positive; using " + defaults.eotwStartWindowSeconds() + ".");
            eotwWindow = defaults.eotwStartWindowSeconds();
        }
        List<Long> marks = section.contains("sotw.announce-at-seconds")
                ? EventSettingsLoader.loadMarks(section.getLongList("sotw.announce-at-seconds"), "sotw", warn)
                : defaults.sotwAnnounceAtSeconds();

        return new PhaseSettings(
                sotwDuration,
                loadDate(section.getString("sotw.start-at"), zone, "sotw.start-at", warn),
                loadDate(section.getString("eotw.start-at"), zone, "eotw.start-at", warn),
                eotwWindow,
                marks,
                zone,
                loadPurge(section, warn));
    }

    private static PhaseSettings.PurgeRules loadPurge(ConfigurationSection section, Consumer<String> warn) {
        PhaseSettings.PurgeRules defaults = PhaseSettings.PurgeRules.NONE;
        long duration = Durations.capSeconds(section.getLong("purge.duration-seconds", defaults.durationSeconds()), "purge.duration-seconds", warn);
        if (duration <= 0) {
            warn.accept("purge.duration-seconds must be positive; using " + defaults.durationSeconds() + ".");
            duration = defaults.durationSeconds();
        }
        List<java.time.LocalTime> times = new java.util.ArrayList<>();
        for (String raw : section.getStringList("purge.schedule")) {
            try {
                times.add(java.time.LocalTime.parse(raw.trim()));
            } catch (DateTimeParseException | NullPointerException e) {
                warn.accept("purge.schedule: '" + raw + "' is not a time like 20:00; ignored.");
            }
        }
        return new PhaseSettings.PurgeRules(duration, times);
    }

    /** @return the date as epoch millis, or 0 when it is empty or unreadable */
    private static long loadDate(String raw, ZoneId zone, String key, Consumer<String> warn) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return LocalDateTime.parse(raw.trim(), DATE).atZone(zone).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            warn.accept(key + " '" + raw + "' is not a yyyy-MM-dd HH:mm date; ignored - that phase "
                    + "will only start by command.");
            return 0L;
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
                    + ZoneId.systemDefault() + ").");
            return ZoneId.systemDefault();
        }
    }
}
