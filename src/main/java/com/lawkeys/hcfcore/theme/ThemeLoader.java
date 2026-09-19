package com.lawkeys.hcfcore.theme;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Turns {@code theme.yml} into a {@link Theme}; a bad value is reported and replaced by the shipped one. */
public final class ThemeLoader {

    private ThemeLoader() {
    }

    public static Theme load(ConfigurationSection section, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        Theme defaults = Theme.defaults();
        if (section == null) {
            return defaults;
        }
        Map<String, String> colors = new LinkedHashMap<>(defaults.colors());
        ConfigurationSection read = section.getConfigurationSection("colors");
        if (read != null) {
            for (String role : read.getKeys(false)) {
                String value = read.getString(role, "");
                if (!Theme.isColour(value)) {
                    warn.accept("colors." + role + ": '" + value + "' is not a hex colour (#rrggbb) nor a gradient"
                            + " (#rrggbb>#rrggbb); ignored.");
                    continue;
                }
                colors.put(role, value);
            }
        }
        ConfigurationSection menus = section.getConfigurationSection("menus");
        Theme.Menus d = defaults.menus();
        Theme.Menus readMenus = menus == null ? d : new Theme.Menus(
                menus.getString("frame", d.frame()),
                menus.getString("pane", d.pane()),
                menus.getString("corner-pane", d.cornerPane()),
                menus.getString("action", d.action()));
        if (!java.util.List.of("full", "bars", "none").contains(readMenus.frame())) {
            warn.accept("menus.frame: '" + readMenus.frame() + "' is not full, bars or none; full is used.");
            readMenus = new Theme.Menus("full", readMenus.pane(), readMenus.cornerPane(), readMenus.action());
        }
        return new Theme(colors, section.getString("prefix", defaults.prefix()),
                section.getString("bullet", defaults.bullet()),
                section.getBoolean("small-caps-titles", defaults.smallCaps()), readMenus, overrides(section, warn));
    }

    /** The optional sections that set, in place of the other files, texts, the chat's format, nametags. */
    private static Theme.Overrides overrides(ConfigurationSection section, Consumer<String> warn) {
        Map<String, String> messages = new LinkedHashMap<>();
        ConfigurationSection texts = section.getConfigurationSection("messages");
        if (texts != null) {
            // Nested as in lang/en.yml, or dotted on one line: both are the key's full path.
            for (String key : texts.getKeys(true)) {
                if (texts.isString(key)) {
                    messages.put(key, texts.getString(key));
                } else if (!texts.isConfigurationSection(key)) {
                    warn.accept("messages." + key + ": a text is expected; ignored.");
                }
            }
        }
        ConfigurationSection chat = section.getConfigurationSection("chat");
        ConfigurationSection nametags = section.getConfigurationSection("nametags");
        Map<String, String> relations = new LinkedHashMap<>();
        ConfigurationSection colours = nametags == null ? null : nametags.getConfigurationSection("colors");
        if (colours != null) {
            for (String relation : colours.getKeys(false)) {
                relations.put(relation.toLowerCase(java.util.Locale.ROOT), colours.getString(relation, ""));
            }
        }
        return new Theme.Overrides(messages,
                chat == null ? null : chat.getString("format"),
                chat == null ? null : chat.getString("kills-format"),
                nametags == null ? null : nametags.getString("team-line"),
                nametags == null ? null : nametags.getString("name-line"),
                relations);
    }
}
