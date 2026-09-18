package com.lawkeys.hcfcore.schedule;

import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Turns {@code schedule.yml} into an immutable {@link ScheduleSettings}.
 *
 * <p>Invalid values are reported and replaced by the built-in default rather than
 * taking the server down (ARCHITECTURE.md section 6); a bad entry loses that entry,
 * not the file.
 */
public final class ScheduleSettingsLoader {

    private ScheduleSettingsLoader() {
    }

    public static ScheduleSettings load(ConfigurationSection section, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        ScheduleSettings defaults = ScheduleSettings.defaults();
        if (section == null) {
            warn.accept("schedule.yml is missing or empty - nothing is scheduled.");
            return defaults;
        }
        return new ScheduleSettings(
                section.getBoolean("enabled", defaults.enabled()),
                loadZone(section.getString("time-zone", "system"), warn),
                loadTips(section.getConfigurationSection("tips"), defaults.tips(), warn),
                loadSchedules(section.getConfigurationSection("schedules"), warn),
                loadPresets(section.getConfigurationSection("timers"), warn),
                loadKeyAll(section.getConfigurationSection("key-all"), defaults.keyAll()));
    }

    /** The same rule as {@code events.yml}: "system" follows the host clock. */
    private static ZoneId loadZone(String raw, Consumer<String> warn) {
        if (raw == null || raw.isBlank() || raw.trim().equalsIgnoreCase("system")) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(raw.trim());
        } catch (RuntimeException e) {
            warn.accept("time-zone '" + raw + "' is not a known zone id; using the system zone ("
                    + ZoneId.systemDefault() + ").");
            return ZoneId.systemDefault();
        }
    }

    private static ScheduleSettings.TipRules loadTips(ConfigurationSection section,
                                                     ScheduleSettings.TipRules defaults,
                                                     Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        long interval = Durations.capSeconds(section.getLong("interval-seconds", defaults.intervalSeconds()), "tips.interval-seconds", warn);
        if (interval < 10) {
            warn.accept("tips.interval-seconds must be at least 10, got " + interval
                    + "; using " + defaults.intervalSeconds() + ".");
            interval = defaults.intervalSeconds();
        }
        String order = section.getString("order", "in-order");
        boolean random = "random".equalsIgnoreCase(order);
        if (!random && !"in-order".equalsIgnoreCase(order)) {
            warn.accept("tips.order '" + order + "' is neither in-order nor random; using in-order.");
        }
        // Missing takes the shipped lines; an empty list is a choice, and says nothing.
        List<String> messages = new ArrayList<>();
        if (!section.contains("messages")) {
            messages.addAll(defaults.messages());
        }
        for (String message : section.getStringList("messages")) {
            if (message != null && !message.isBlank()) {
                messages.add(message);
            }
        }
        boolean enabled = section.getBoolean("enabled", defaults.enabled());
        if (enabled && messages.isEmpty()) {
            warn.accept("tips.enabled is true but tips.messages is empty, so no tip is ever sent.");
        }
        return new ScheduleSettings.TipRules(enabled,
                interval, random, section.getString("prefix", defaults.prefix()), messages);
    }

    private static List<ScheduleSettings.ScheduledAction> loadSchedules(ConfigurationSection section,
                                                                      Consumer<String> warn) {
        List<ScheduleSettings.ScheduledAction> actions = new ArrayList<>();
        if (section == null) {
            return actions;
        }
        for (String name : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(name);
            if (entry == null) {
                warn.accept("schedules." + name + " is not a section; ignored.");
                continue;
            }
            List<LocalTime> times = new ArrayList<>();
            for (String raw : entry.getStringList("times")) {
                try {
                    times.add(LocalTime.parse(raw.trim()));
                } catch (DateTimeParseException | NullPointerException e) {
                    warn.accept("schedules." + name + ": '" + raw + "' is not a time like 18:00; ignored.");
                }
            }
            String broadcast = entry.getString("broadcast", "");
            List<String> commands = entry.getStringList("commands");
            if (times.isEmpty() || (broadcast.isBlank() && commands.isEmpty())) {
                // A schedule with no time, or with nothing to do, would look set up and
                // never do anything - the ghost behaviour ARCHITECTURE.md section 2 forbids.
                warn.accept("schedules." + name + " needs at least one time and a broadcast or a command; ignored.");
                continue;
            }
            actions.add(new ScheduleSettings.ScheduledAction(name, times, broadcast, commands));
        }
        return actions;
    }

    private static Map<String, ScheduleSettings.TimerPreset> loadPresets(ConfigurationSection section,
                                                                       Consumer<String> warn) {
        Map<String, ScheduleSettings.TimerPreset> presets = new LinkedHashMap<>();
        if (section == null) {
            return presets;
        }
        for (String name : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(name);
            if (entry == null) {
                warn.accept("timers." + name + " is not a section; ignored.");
                continue;
            }
            String key = CustomTimers.normalize(name);
            if (key.equals(ScheduleModule.KEY_ALL_TIMER)) {
                warn.accept("timers." + name + ": that name belongs to /keyall, whose timer is set up "
                        + "under key-all; ignored.");
                continue;
            }
            presets.put(key, new ScheduleSettings.TimerPreset(
                    entry.getString("label", name),
                    entry.getString("end-broadcast", ""),
                    entry.getStringList("end-commands")));
        }
        return presets;
    }

    private static ScheduleSettings.KeyAllRules loadKeyAll(ConfigurationSection section,
                                                         ScheduleSettings.KeyAllRules defaults) {
        if (section == null) {
            return defaults;
        }
        return new ScheduleSettings.KeyAllRules(
                section.getString("label", defaults.label()),
                section.getString("broadcast", defaults.broadcast()),
                section.getStringList("commands"));
    }
}
