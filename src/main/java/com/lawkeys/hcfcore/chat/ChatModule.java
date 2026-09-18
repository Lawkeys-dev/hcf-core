package com.lawkeys.hcfcore.chat;

import com.lawkeys.hcfcore.chat.listener.ChatListener;
import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.integration.luckperms.LuckPermsIntegration;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.stats.StatsModule;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.util.ColorCodes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Wires the chat format into the server (FEATURES.md section 9).
 *
 * <p>Comes after {@code team/} and {@code stats/}, both of which it reads: the team
 * channel decides whether a line is routed rather than broadcast, and the kill count
 * goes in the line. It runs without either - no team means no routing, no stats
 * means no kill count - so neither is a hard dependency.
 *
 * <p>LuckPerms is looked up here rather than at plugin startup because this is the
 * only feature that needs it; a server without it simply shows no prefix.
 */
public final class ChatModule {

    /**
     * Lets a player's own {@code &} codes through. Without it, what they type is
     * shown exactly as typed: colour and obfuscated text in public chat are the
     * tools of spam and of fake staff lines, so they are granted, not assumed.
     */
    public static final String COLOR_PERMISSION = "hcfcore.chat.color";

    /**
     * @return what the sender typed, ready to go into a template that is coloured
     *         afterwards: escaped unless they hold {@link #COLOR_PERMISSION}. The
     *         console holds every permission.
     */
    public static String typedText(Permissible sender, String typed) {
        return sender.hasPermission(COLOR_PERMISSION) ? typed : ColorCodes.escape(typed);
    }

    /** How often, in ticks, local chat's copy of player positions is refreshed. */
    private static final long POSITION_REFRESH_TICKS = 10L;

    private final Plugin plugin;
    private final TeamModule teams;
    private final StatsModule stats;
    private final LangManager lang;

    private volatile ChatSettings settings = ChatSettings.defaults();
    private volatile ChatDecorations decorations = ChatDecorations.NONE;
    private final Positions positions = new Positions();
    private BukkitTask positionTask;

    /**
     * @param teams may be {@code null} if the team module is not running
     * @param stats may be {@code null} if the stats module is not running
     */
    public ChatModule(Plugin plugin, TeamModule teams, StatsModule stats, LangManager lang) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.teams = teams;
        this.stats = stats;
        this.lang = Objects.requireNonNull(lang, "lang");
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public TeamModule getTeams() {
        return teams;
    }

    public StatsModule getStats() {
        return stats;
    }

    public LangManager getLang() {
        return lang;
    }

    public ChatSettings getSettings() {
        return settings;
    }

    public ChatDecorations getDecorations() {
        return decorations;
    }

    public Positions getPositions() {
        return positions;
    }

    public void enable() {
        reloadSettings();
        this.decorations = LuckPermsIntegration.decorations(plugin);
        plugin.getServer().getPluginManager().registerEvents(new ChatListener(this), plugin);
        // Always scheduled, and idle while local chat is off, so /hcf reload can turn
        // it on without a restart.
        this.positionTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshPositions,
                POSITION_REFRESH_TICKS, POSITION_REFRESH_TICKS);
    }

    public void disable() {
        if (positionTask != null) {
            positionTask.cancel();
            positionTask = null;
        }
        positions.clear();
    }

    /** Main thread: copies where everybody is, for the chat thread to read. */
    private void refreshPositions() {
        ChatSettings current = settings;
        if (!current.enabled() || current.rangeBlocks() <= 0) {
            positions.clear();
            return;
        }
        Map<UUID, Positions.Position> copy = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location at = player.getLocation();
            copy.put(player.getUniqueId(),
                    new Positions.Position(at.getWorld().getUID(), at.getX(), at.getY(), at.getZ()));
        }
        positions.replace(copy);
    }

    public void reloadSettings() {
        var section = ConfigManager.loadFile(plugin, "chat.yml");
        this.settings = ChatSettingsLoader.load(section,
                warning -> plugin.getLogger().warning("chat.yml: " + warning));
    }
}
