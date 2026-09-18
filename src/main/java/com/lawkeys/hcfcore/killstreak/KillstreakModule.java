package com.lawkeys.hcfcore.killstreak;

import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.stats.StatsModule;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/**
 * Rewards for consecutive kills (FEATURES.md section 7).
 *
 * <p>Almost nothing of its own: the streak itself is counted by {@code stats/},
 * which already had to store it, and this module only answers the question "does
 * anything happen at that number". It installs itself through that module's
 * {@code KillstreakObserver} seam, so the two need not know about each other beyond
 * an integer.
 *
 * <p><strong>The reward table ships empty.</strong> FEATURES.md leaves it entirely
 * to the operator, and inventing a table would be deciding gameplay on their behalf
 * - the same reason {@code points-per-capture} defaults to zero.
 */
public final class KillstreakModule {

    private final Plugin plugin;
    private final LangManager lang;
    private final StatsModule stats;

    private volatile boolean enabled = true;
    private volatile KillstreakRewards rewards = KillstreakRewards.empty();

    /** @param stats may be {@code null}; without it nothing counts streaks and this does nothing */
    public KillstreakModule(Plugin plugin, LangManager lang, StatsModule stats) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lang = Objects.requireNonNull(lang, "lang");
        this.stats = stats;
    }

    public KillstreakRewards getRewards() {
        return rewards;
    }

    public void enable() {
        reloadSettings();
        if (stats == null) {
            plugin.getLogger().info("Killstreaks are idle: the statistics module is not running.");
            return;
        }
        stats.setKillstreakObserver(this::onStreak);
    }

    /**
     * Fires the reward for exactly this streak, if one is configured.
     *
     * <p>Exactly this one, not every reward up to it: the player was already given
     * the lower ones on the way up.
     */
    private void onStreak(Player player, int streak) {
        if (!enabled) {
            return;
        }
        rewards.at(streak).ifPresent(reward -> {
            if (reward.hasBroadcast()) {
                Bukkit.broadcast(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacySection().deserialize(LangManager.colorize(reward.broadcastFor(player.getName()))));
            }
            for (String command : reward.commandsFor(player.getName())) {
                // From the console, so a reward can do what the player could not.
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        });
    }

    public void reloadSettings() {
        var section = ConfigManager.loadFile(plugin, "killstreaks.yml");
        this.enabled = KillstreakSettingsLoader.isEnabled(section);
        this.rewards = KillstreakSettingsLoader.load(section,
                warning -> plugin.getLogger().warning("killstreaks.yml: " + warning));
    }
}
