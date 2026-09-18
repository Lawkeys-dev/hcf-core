package com.lawkeys.hcfcore.stats;

import com.lawkeys.hcfcore.database.dao.JdbcStatsStore;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.startup.StartupBarrier;
import com.lawkeys.hcfcore.startup.StartupGate;
import com.lawkeys.hcfcore.stats.command.StatsCommand;
import com.lawkeys.hcfcore.stats.command.TopCommand;
import com.lawkeys.hcfcore.stats.listener.StatsListener;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.logging.Level;

/**
 * Wires player statistics into the server.
 *
 * <p>Comes early: the chat format, the scoreboard, the leaderboards and the
 * killstreak rewards all read from here, and none of them can start before it.
 *
 * <p>It depends on nothing itself, which is why it can.
 */
public final class StatsModule {

    private final Plugin plugin;
    private final LangManager lang;
    private final StartupGate startup;

    private StatsManager manager;
    private BukkitTask saveTask;

    /**
     * Told about every kill and the streak it produced, so {@code killstreak/} can
     * hang rewards off it without this module knowing what a reward is.
     */
    private volatile KillstreakObserver killstreakObserver = KillstreakObserver.NONE;

    /**
     * What happens when somebody reaches a streak.
     *
     * <p>Declared here and answered by {@code killstreak/}, which starts later, in
     * the manner of every other seam in this project (ARCHITECTURE.md section 14).
     * Until then {@link #NONE} changes nothing.
     */
    @FunctionalInterface
    public interface KillstreakObserver {

        /**
         * @param player the killer
         * @param streak their streak after this kill
         */
        void reached(Player player, int streak);

        KillstreakObserver NONE = (player, streak) -> {
        };
    }

    public StatsModule(Plugin plugin, LangManager lang, StartupGate startup) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lang = Objects.requireNonNull(lang, "lang");
        this.startup = Objects.requireNonNull(startup, "startup");
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public LangManager getLang() {
        return lang;
    }

    public StartupGate getStartup() {
        return startup;
    }

    public StatsManager getManager() {
        return manager;
    }

    /** Installs the killstreak observer. Called by {@code killstreak/} at startup. */
    public void setKillstreakObserver(KillstreakObserver observer) {
        this.killstreakObserver = Objects.requireNonNull(observer, "observer");
    }

    /** Records a kill and tells whoever is listening about the streak it produced. */
    public void recordKill(Player killer) {
        int streak = manager.recordKill(killer.getUniqueId(), killer.getName());
        killstreakObserver.reached(killer, streak);
    }

    public void enable(DataSource dataSource, long saveIntervalSeconds) {
        StatsStore store = dataSource == null
                ? StatsStore.NO_OP
                : new JdbcStatsStore(dataSource, message -> plugin.getLogger().info(message));
        this.manager = new StatsManager(store);

        StartupBarrier.Load load = startup.expect("player statistics");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                manager.loadAll();
                plugin.getLogger().info("Loaded statistics for " + manager.size() + " players.");
                load.succeeded();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load player statistics.", e);
                load.failed();
            }
        });

        if (saveIntervalSeconds > 0) {
            long ticks = saveIntervalSeconds * 20L;
            this.saveTask = Bukkit.getScheduler()
                    .runTaskTimerAsynchronously(plugin, this::flushQuietly, ticks, ticks);
        }

        plugin.getServer().getPluginManager().registerEvents(new StatsListener(this), plugin);
        register("stats", new StatsCommand(this));
        register("leaderboard", new TopCommand(this));
    }

    private void register(String name, TabExecutor executor) {
        PluginCommand command = plugin.getServer().getPluginCommand(name);
        if (command == null) {
            plugin.getLogger().severe("The '" + name + "' command is missing from plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    public void disable() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (manager == null) {
            return;
        }
        // Before the final flush, and this is the whole reason it exists: playtime is
        // only added to the stored figure when a session ends, so without this the
        // time everybody online had played would never be written.
        manager.endAllSessions();
        try {
            int written = manager.flush();
            if (written > 0) {
                plugin.getLogger().info("Saved " + written + " player statistics on shutdown.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save player statistics on shutdown", e);
        }
    }

    private void flushQuietly() {
        try {
            manager.flush();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Periodic statistics save failed; the affected rows stay queued.", e);
        }
    }

    /** Runs {@code action} with the number of players whose stats are held. */
    public void withPlayerCount(IntConsumer action) {
        action.accept(manager == null ? 0 : manager.size());
    }
}
