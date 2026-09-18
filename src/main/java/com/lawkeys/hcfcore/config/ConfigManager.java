package com.lawkeys.hcfcore.config;

import com.lawkeys.hcfcore.HCFCore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
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

/**
 * Loads and gives access to the plugin's configuration files.
 *
 * <p>Per CONTRIBUTING.md section 3 (maximum configurability), this is the single
 * point through which every module reads its tunable values - no gameplay
 * constant should ever be hardcoded in a manager class.
 *
 * <p>Per ARCHITECTURE.md section 6, configuration is split across one file per
 * module ({@code teams.yml}, {@code dtr.yml}, {@code economy.yml}...) rather
 * than one monolithic file. {@link #loadFile(Plugin, String)} is how a module
 * reads its own file; each module owns the parsing of its section, so this class
 * does not grow a getter per setting.
 */
public class ConfigManager {

    private final HCFCore plugin;
    private FileConfiguration config;

    public ConfigManager(HCFCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads (or reloads) the root configuration from disk, saving the bundled
     * default if the operator does not have one yet.
     *
     * <p>Safe to call again at runtime for {@code /hcf reload} (ARCHITECTURE.md
     * section 6). Per-module files are re-read by their own module on reload.
     */
    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    /**
     * Reads a per-module configuration file, writing the bundled default into the
     * data folder on first run so operators have a documented file to edit.
     *
     * @return the file's root section, or {@code null} if it is missing or empty -
     *         callers fall back to their built-in defaults rather than failing
     */
    public static ConfigurationSection loadFile(Plugin plugin, String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists() && plugin.getResource(fileName) != null) {
            plugin.saveResource(fileName, false);
        }
        if (!file.exists()) {
            return null;
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        reportWrongTypes(plugin, fileName, loaded);
        return loaded;
    }

    /** Warns about every setting of the wrong type - see {@link ConfigTypeCheck}. */
    private static void reportWrongTypes(Plugin plugin, String fileName, ConfigurationSection loaded) {
        InputStream bundled = plugin.getResource(fileName);
        if (bundled == null) {
            return;
        }
        try (Reader reader = new InputStreamReader(bundled, StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            for (String line : ConfigTypeCheck.mismatches(values(defaults), values(loaded))) {
                plugin.getLogger().warning(fileName + ": " + line);
            }
        } catch (IOException e) {
            // Closing a resource read from the plugin's own jar: nothing to report.
        }
    }

    /** @return every value under this section by full path, sections themselves left out */
    private static Map<String, Object> values(ConfigurationSection section) {
        Map<String, Object> values = new LinkedHashMap<>();
        section.getValues(true).forEach((path, value) -> {
            if (!(value instanceof ConfigurationSection)) {
                values.put(path, value);
            }
        });
        return values;
    }
}
