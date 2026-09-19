package com.lawkeys.hcfcore.schedule;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot of {@code schedule.yml}.
 *
 * @param zone      the time zone the schedule times are read in
 * @param schedules things to say or do at fixed times every day
 * @param presets   what a custom timer of a given name looks like and does when it ends
 */
public record ScheduleSettings(boolean enabled, ZoneId zone, TipRules tips,
                               List<ScheduledAction> schedules, Map<String, TimerPreset> presets,
                               KeyAllRules keyAll) {

    public ScheduleSettings {
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(tips, "tips");
        schedules = List.copyOf(schedules);
        presets = Map.copyOf(presets);
        Objects.requireNonNull(keyAll, "keyAll");
    }

    /** @param random at random rather than in order - never the same one twice running */
    public record TipRules(boolean enabled, long intervalSeconds, boolean random, String prefix,
                           List<String> messages) {

        public TipRules {
            prefix = prefix == null ? "" : prefix;
            messages = List.copyOf(messages);
        }
    }

    /** @param commands run from the console */
    public record ScheduledAction(String name, List<LocalTime> times, String broadcast, List<String> commands) {

        public ScheduledAction {
            times = List.copyOf(times);
            broadcast = broadcast == null ? "" : broadcast;
            commands = List.copyOf(commands);
        }
    }

    /** @param endCommands run from the console when the timer ends; {@code %timer%} is its name */
    public record TimerPreset(String label, String endBroadcast, List<String> endCommands) {

        public TimerPreset {
            Objects.requireNonNull(label, "label");
            endBroadcast = endBroadcast == null ? "" : endBroadcast;
            endCommands = List.copyOf(endCommands);
        }
    }

    /**
     * @param label    the label of the timer {@code /keyall <countdown>} starts
     * @param commands run from the console once for every player online; {@code %player%}
     */
    public record KeyAllRules(String label, String broadcast, List<String> commands) {

        public KeyAllRules {
            label = label == null ? "Key-All" : label;
            broadcast = broadcast == null ? "" : broadcast;
            commands = List.copyOf(commands);
        }
    }

    /**
     * Built-in fallback, the values {@code resources/schedule.yml} ships: tips off with
     * their example lines, no schedule, no preset, a key-all with no command. A setting
     * missing from the file takes the value here.
     */
    public static ScheduleSettings defaults() {
        return new ScheduleSettings(true, ZoneId.systemDefault(),
                new TipRules(false, 300L, false, "{dark}[{warning}Tip{dark}] {muted}", List.of(
                        "Type {secondary}/team help {muted}to see every team command.",
                        "{secondary}/events {muted}lists what is running and what is coming.",
                        "{secondary}/stats {muted}and {secondary}/leaderboard {muted}show how you are doing.")),
                List.of(), Map.of(),
                new KeyAllRules("{primary}Key-All", "{primary}&lKEY-ALL! {muted}%count% players received a key.", List.of()));
    }
}
