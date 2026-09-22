package com.lawkeys.hcfcore.events.setup;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Reads and writes {@code events.yml} for the staff setup commands
 * ({@code /events create|claim|setzone|setblock|delete} - see {@link EventSetup}).
 *
 * <p>These are the only commands in the plugin that ever rewrite a configuration
 * file - see ARCHITECTURE.md section 9 and the note in
 * {@code docs/getting-started/upgrading.md}. Even so, each edit touches only the
 * one section of the one event it names; nothing else in the file is rewritten.
 *
 * <p>Comments and the documentation's {@code --8<--} markers survive: Paper's
 * {@code YamlConfigurationOptions#parseComments} defaults to {@code true}
 * (checked with {@code javap} against {@code paper-api-26.2}), and is set
 * explicitly here so a future default change cannot silently start stripping
 * them.
 *
 * <p>Runs on the main thread, like the command that calls it: the file is a few
 * kilobytes, and staff type these commands rarely enough that the write is not
 * worth an async round trip.
 */
public final class EventYamlStore {

    private static final String FILE = "events.yml";

    private EventYamlStore() {
    }

    @FunctionalInterface
    public interface Edit {
        /** @return {@code true} to save the change, {@code false} to discard it */
        boolean apply(ConfigurationSection root);
    }

    /**
     * @return whether the edit was applied and saved. A file that does not parse is
     *         left alone: {@code YamlConfiguration.loadConfiguration} would hand back
     *         an empty configuration, and saving it would have replaced the whole
     *         file with the one section being edited (found in the final audit,
     *         22/09/2026).
     */
    public static boolean edit(Plugin plugin, Edit edit) {
        File file = new File(plugin.getDataFolder(), FILE);
        Optional<YamlConfiguration> loaded = read(plugin);
        if (loaded.isEmpty()) {
            return false;
        }
        YamlConfiguration yaml = loaded.get();
        if (!edit.apply(yaml)) {
            return false;
        }
        try {
            yaml.save(file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not write events.yml", e);
            return false;
        }
    }

    /**
     * @return the server's {@code events.yml} as it is on disk, comments kept - empty,
     *         after a console warning, when it does not parse
     */
    public static Optional<YamlConfiguration> read(Plugin plugin) {
        File file = new File(plugin.getDataFolder(), FILE);
        if (!file.exists() && plugin.getResource(FILE) != null) {
            plugin.saveResource(FILE, false);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        // Set before loading: the comments are kept only if they are read.
        yaml.options().parseComments(true);
        try {
            yaml.load(file);
            return Optional.of(yaml);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.WARNING, "events.yml could not be read; the setup command changed nothing", e);
            return Optional.empty();
        }
    }

    /**
     * @return the shipped example a new event of this kind is copied from - read from
     *         the plugin jar, never the server's file, so an operator who deleted the
     *         examples can still create events
     */
    static Optional<Map<String, Object>> template(Plugin plugin, EventKind kind) {
        try (InputStream in = plugin.getResource(FILE)) {
            if (in == null) {
                return Optional.empty();
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                ConfigurationSection section = YamlConfiguration.loadConfiguration(reader)
                        .getConfigurationSection(kind.section() + "." + kind.template());
                return section == null ? Optional.empty() : Optional.of(toMap(section));
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read the shipped events.yml", e);
            return Optional.empty();
        }
    }

    /** @return the section as plain nested maps, the shape {@link EventTemplate} works on */
    static Map<String, Object> toMap(ConfigurationSection section) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            map.put(key, value instanceof ConfigurationSection child ? toMap(child) : value);
        }
        return map;
    }
}
