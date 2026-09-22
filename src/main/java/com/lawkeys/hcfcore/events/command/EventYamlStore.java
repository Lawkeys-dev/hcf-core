package com.lawkeys.hcfcore.events.command;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Writes the staff setup commands' changes ({@code /events create|setzone|setcore|delete})
 * into {@code events.yml}.
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
final class EventYamlStore {

    private EventYamlStore() {
    }

    @FunctionalInterface
    interface Edit {
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
    static boolean edit(Plugin plugin, Edit edit) {
        File file = new File(plugin.getDataFolder(), "events.yml");
        if (!file.exists() && plugin.getResource("events.yml") != null) {
            plugin.saveResource("events.yml", false);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        // Set before loading: the comments are kept only if they are read.
        yaml.options().parseComments(true);
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.WARNING, "events.yml could not be read; the setup command changed nothing", e);
            return false;
        }
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
}
