package com.lawkeys.hcfcore.integration.lunar;

import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.lang.LangManager;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Lunar Client (Apollo) - FEATURES.md section 13: waypoints, team view, cooldowns and
 * nametags for players on Lunar Client, as the project owner chose on 12/09/2026.
 *
 * <p><strong>Needs Lunar's Apollo plugin on the server.</strong> {@code apollo-api} is
 * compiled against, not shipped - Lunar documents it as {@code provided} ("Apollo
 * doesn't require shading", lunarclient.dev/maven-repository) - and its static
 * accessors throw until the Apollo plugin has started. The plugin is
 * {@code Apollo-Bukkit} (or {@code Apollo-Folia}): the names Lunar's own plugin.yml
 * declares and its API example soft-depends on, checked in the LunarClient/Apollo
 * repository. Without it, this does nothing and the rest of the plugin is untouched;
 * with it, a player not on Lunar Client is simply never sent anything.
 *
 * <p>Soft dependency, the way {@code VaultIntegration} is one: no Apollo type is named
 * here, and {@link ApolloBridge} - the only class that names them - is loaded only
 * once the plugin check has passed.
 */
public final class LunarIntegration {

    private static final List<String> APOLLO_PLUGINS = List.of("Apollo-Bukkit", "Apollo-Folia");

    private final Plugin plugin;
    private final LunarBridge bridge;

    private LunarIntegration(Plugin plugin, LunarBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    /** @return the integration, running if Apollo is installed and inert otherwise */
    public static LunarIntegration start(Plugin plugin, LangManager lang, LunarSources sources) {
        Objects.requireNonNull(plugin, "plugin");
        boolean installed = APOLLO_PLUGINS.stream()
                .anyMatch(name -> plugin.getServer().getPluginManager().isPluginEnabled(name));
        if (!installed) {
            plugin.getLogger().info("Apollo is not installed - no Lunar Client features (waypoints, team view, "
                    + "cooldowns, nametags). Everything else works normally.");
            return new LunarIntegration(plugin, null);
        }
        try {
            LunarBridge bridge = ApolloBridge.start(plugin, lang, sources, load(plugin));
            plugin.getLogger().info("Lunar Client features enabled through Apollo.");
            return new LunarIntegration(plugin, bridge);
        } catch (LinkageError | RuntimeException e) {
            // An Apollo whose API is not the one compiled against, or one that has not
            // started: an optional integration is not worth the server.
            plugin.getLogger().log(Level.WARNING, "Apollo is installed but could not be used; "
                    + "no Lunar Client features.", e);
            return new LunarIntegration(plugin, null);
        }
    }

    public boolean isRunning() {
        return bridge != null;
    }

    /** Re-reads {@code apollo.yml}. */
    public void reload() {
        if (bridge != null) {
            bridge.apply(load(plugin));
        }
    }

    public void disable() {
        if (bridge != null) {
            bridge.stop();
        }
    }

    private static LunarSettings load(Plugin plugin) {
        LunarSettings read = LunarSettingsLoader.load(ConfigManager.loadFile(plugin, "apollo.yml"),
                warning -> plugin.getLogger().warning("apollo.yml: " + warning));
        return withTheme(read, com.lawkeys.hcfcore.lang.LangManager.theme().overrides());
    }

    /** theme.yml may set the nametags' look in place of apollo.yml. */
    static LunarSettings withTheme(LunarSettings read, com.lawkeys.hcfcore.theme.Theme.Overrides theme) {
        NametagStyle style = read.nametags().style();
        java.util.Map<NametagStyle.Relation, String> colors = new java.util.EnumMap<>(NametagStyle.Relation.class);
        colors.putAll(style.colors());
        theme.nametagColors().forEach((relation, colour) -> {
            for (NametagStyle.Relation known : NametagStyle.Relation.values()) {
                if (known.name().equalsIgnoreCase(relation)) {
                    colors.put(known, colour);
                }
            }
        });
        NametagStyle themed = new NametagStyle(
                theme.nametagTeam() != null ? theme.nametagTeam() : style.teamLine(),
                theme.nametagName() != null ? theme.nametagName() : style.nameLine(), colors);
        return new LunarSettings(read.enabled(), read.updateTicks(), read.resendSeconds(), read.waypoints(), read.teamView(),
                read.cooldowns(), new LunarSettings.Nametags(read.nametags().enabled(), themed));
    }
}
