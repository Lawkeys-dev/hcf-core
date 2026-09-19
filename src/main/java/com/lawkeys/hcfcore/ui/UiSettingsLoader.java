package com.lawkeys.hcfcore.ui;

import com.lawkeys.hcfcore.ui.tab.TabGrid;
import com.lawkeys.hcfcore.ui.tab.TabSort;
import com.lawkeys.hcfcore.ui.tab.TabStyle;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
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
                loadTablist(section.getConfigurationSection("tablist"), defaults.tablist(), warn));
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
                                                      UiSettings.TablistRules defaults,
                                                      Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        String styleName = section.getString("style");
        TabStyle style = TabStyle.parse(styleName).orElse(defaults.style());
        if (styleName != null && TabStyle.parse(styleName).isEmpty()) {
            warn.accept("tablist.style '" + styleName + "' is not hcf, classic or auto; using "
                    + defaults.style().name().toLowerCase(java.util.Locale.ROOT) + ".");
        }
        return new UiSettings.TablistRules(
                section.getBoolean("enabled", defaults.enabled()),
                style,
                Math.max(1L, section.getLong("update-ticks", defaults.updateTicks())),
                loadGrid(section.getConfigurationSection("hcf"), defaults.hcf(), warn),
                loadClassic(section.getConfigurationSection("classic"), defaults.classic(), warn));
    }

    private static UiSettings.GridRules loadGrid(ConfigurationSection section, UiSettings.GridRules defaults,
                                                 Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        List<List<String>> columns = new ArrayList<>();
        for (int i = 1; i <= TabGrid.COLUMNS; i++) {
            String key = "column-" + i;
            List<String> rows = section.isSet(key) ? section.getStringList(key)
                    : i <= defaults.columns().size() ? defaults.columns().get(i - 1) : List.of();
            if (rows.size() > TabGrid.ROWS) {
                warn.accept("tablist.hcf." + key + " has " + rows.size() + " rows, but a column shows "
                        + TabGrid.ROWS + "; the last of them are never drawn.");
            }
            columns.add(rows);
        }
        ConfigurationSection skin = section.getConfigurationSection("skin");
        return new UiSettings.GridRules(
                section.isSet("header") ? section.getStringList("header") : defaults.header(),
                section.isSet("footer") ? section.getStringList("footer") : defaults.footer(),
                columns,
                section.getInt("latency", defaults.latency()),
                skin == null ? defaults.texture() : Objects.requireNonNullElse(skin.getString("texture"), ""),
                skin == null ? defaults.signature() : Objects.requireNonNullElse(skin.getString("signature"), ""));
    }

    private static UiSettings.ClassicRules loadClassic(ConfigurationSection section,
                                                       UiSettings.ClassicRules defaults, Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        String sortName = section.getString("sort");
        TabSort sort = TabSort.parse(sortName).orElse(defaults.sort());
        if (sortName != null && TabSort.parse(sortName).isEmpty()) {
            warn.accept("tablist.classic.sort '" + sortName + "' is not rank, kills or name; using "
                    + defaults.sort().name().toLowerCase(java.util.Locale.ROOT) + ".");
        }
        return new UiSettings.ClassicRules(
                section.isSet("header") ? section.getStringList("header") : defaults.header(),
                section.isSet("footer") ? section.getStringList("footer") : defaults.footer(),
                Objects.requireNonNullElse(section.getString("name"), defaults.name()),
                sort);
    }
}
