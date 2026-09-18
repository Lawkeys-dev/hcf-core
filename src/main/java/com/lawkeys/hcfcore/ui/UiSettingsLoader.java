package com.lawkeys.hcfcore.ui;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Turns {@code ui.yml} into an immutable {@link UiSettings}. */
public final class UiSettingsLoader {

    private UiSettingsLoader() {
    }

    public static UiSettings load(ConfigurationSection section, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        UiSettings defaults = UiSettings.defaults();
        if (section == null) {
            warn.accept("ui.yml is missing or empty - using the built-in scoreboard.");
            return defaults;
        }
        return new UiSettings(
                loadScoreboard(section.getConfigurationSection("scoreboard"), defaults.scoreboard(), warn),
                loadTablist(section.getConfigurationSection("tablist"), defaults.tablist()));
    }

    private static UiSettings.ScoreboardRules loadScoreboard(ConfigurationSection section,
                                                            UiSettings.ScoreboardRules defaults,
                                                            Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        List<String> lines = section.isSet("lines") ? section.getStringList("lines") : defaults.lines();
        // More than fifteen lines is fine when some come and go: rows whose
        // placeholders are all empty are dropped before the cut (LineRenderer). Only
        // rows with no placeholder at all are always there - if they alone pass the
        // limit, the last of them can never be drawn, and the operator is owed that.
        long alwaysShown = lines.stream().filter(line -> line.indexOf('%') < 0 && !line.isBlank()).count();
        if (alwaysShown > LineRenderer.MAX_LINES) {
            warn.accept("scoreboard.lines has " + alwaysShown + " rows with no placeholder, but a scoreboard shows "
                    + "at most " + LineRenderer.MAX_LINES + "; the last of them are never drawn.");
        }
        return new UiSettings.ScoreboardRules(
                section.getBoolean("enabled", defaults.enabled()),
                Math.max(1L, section.getLong("update-ticks", defaults.updateTicks())),
                Objects.requireNonNullElse(section.getString("title"), defaults.title()),
                lines);
    }

    private static UiSettings.TablistRules loadTablist(ConfigurationSection section,
                                                      UiSettings.TablistRules defaults) {
        if (section == null) {
            return defaults;
        }
        return new UiSettings.TablistRules(
                section.getBoolean("enabled", defaults.enabled()),
                section.isSet("header") ? section.getStringList("header") : defaults.header(),
                section.isSet("footer") ? section.getStringList("footer") : defaults.footer());
    }
}
