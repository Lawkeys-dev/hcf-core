package com.lawkeys.hcfcore.lang;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import com.lawkeys.hcfcore.theme.Theme;
import com.lawkeys.hcfcore.util.ColorCodes;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Resolves a message key (plus placeholders) into the final coloured text
 * (ARCHITECTURE.md section 10).
 *
 * <p>The language file is chosen by {@code language:} in config.yml. V1 ships
 * {@code en} only, but adding {@code lang/fr.yml} needs no code change: the file
 * is copied out of the jar on first run so operators can edit it, and a key
 * missing from a translation falls back to the copy bundled in the jar before
 * falling back to the raw key.
 *
 * <p>Reloadable at runtime through {@link #load()}, which is what {@code /hcf reload}
 * calls.
 */
public final class LangManager {

    private static final String DEFAULT_LANGUAGE = "en";

    private final Plugin plugin;

    private String language = DEFAULT_LANGUAGE;
    private FileConfiguration messages;
    /** The copy shipped in the jar, used when an operator's edited file is missing a key. */
    private FileConfiguration bundledDefaults;

    public LangManager(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * Loads (or reloads) the configured language file, writing the bundled copy to
     * the data folder if the operator does not have one yet.
     */
    public void load(String language) {
        this.language = (language == null || language.isBlank()) ? DEFAULT_LANGUAGE : language.trim();
        String resource = "lang/" + this.language + ".yml";

        File file = new File(plugin.getDataFolder(), resource);
        if (!file.exists()) {
            if (plugin.getResource(resource) != null) {
                plugin.saveResource(resource, false);
            } else {
                plugin.getLogger().warning("No language file for '" + this.language
                        + "'; falling back to " + DEFAULT_LANGUAGE + ".");
                this.language = DEFAULT_LANGUAGE;
                resource = "lang/" + DEFAULT_LANGUAGE + ".yml";
                file = new File(plugin.getDataFolder(), resource);
                if (!file.exists()) {
                    plugin.saveResource(resource, false);
                }
            }
        }

        this.messages = YamlConfiguration.loadConfiguration(file);
        this.bundledDefaults = loadBundled(resource);
    }

    /** Reloads the language currently in use. */
    public void load() {
        load(this.language);
    }

    private FileConfiguration loadBundled(String resource) {
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read bundled " + resource, e);
            return new YamlConfiguration();
        }
    }

    public String getLanguage() {
        return language;
    }

    /**
     * @param placeholders alternating name and value, e.g. {@code "team", "Wizards"}
     * @return the coloured message, or the key itself if no translation exists -
     *         visible in-game, which is what you want when a key is missing
     */
    public String get(String key, String... placeholders) {
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException("placeholders must be name/value pairs");
        }
        String raw = lookup(key);
        for (int i = 0; i < placeholders.length; i += 2) {
            raw = raw.replace('%' + placeholders[i] + '%', placeholders[i + 1]);
        }
        return colorize(raw);
    }

    /** Same as {@link #get(String, String...)} but taking the placeholder map a manager returns. */
    public String get(String key, Map<String, String> placeholders) {
        String raw = lookup(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace('%' + entry.getKey() + '%', entry.getValue());
        }
        return colorize(raw);
    }

    private String lookup(String key) {
        // A text theme.yml sets comes first: a theme carries its own wording of the look.
        String themed = theme.overrides().messages().get(key);
        if (themed != null) {
            return themed;
        }
        if (messages != null) {
            String value = messages.getString(key);
            if (value != null) {
                return value;
            }
        }
        if (bundledDefaults != null) {
            String value = bundledDefaults.getString(key);
            if (value != null) {
                return value;
            }
        }
        plugin.getLogger().warning("Missing language key: " + key);
        return key;
    }

    /** Sends a resolved message, skipping empty ones so a translation can silence a message. */
    public void send(CommandSender recipient, String key, String... placeholders) {
        String message = get(key, placeholders);
        if (!message.isEmpty()) {
            recipient.sendMessage(message);
        }
    }

    public void send(CommandSender recipient, String key, Map<String, String> placeholders) {
        String message = get(key, placeholders);
        if (!message.isEmpty()) {
            recipient.sendMessage(message);
        }
    }

    /**
     * Translates legacy {@code &}-prefixed colour codes for display.
     *
     * <p>Delegates to {@link ColorCodes}, which keeps the actual translation free
     * of the server API and therefore unit-tested.
     */
    public static String colorize(String input) {
        return ColorCodes.translate(theme.apply(input));
    }

    /** The look every text is coloured with: {@code theme.yml}'s tokens, {@code {primary}} and the like. */
    private static volatile Theme theme = Theme.defaults();

    public static Theme theme() {
        return theme;
    }

    /** Installs the theme. Called at startup and on {@code /hcf reload}, before anything is coloured. */
    public static void setTheme(Theme newTheme) {
        theme = java.util.Objects.requireNonNull(newTheme, "newTheme");
    }
}
