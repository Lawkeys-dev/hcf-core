package com.lawkeys.hcfcore.redeem;

import com.lawkeys.hcfcore.util.Durations;
import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.database.dao.JdbcRedeemStore;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.redeem.command.RedeemAdminCommand;
import com.lawkeys.hcfcore.redeem.command.RedeemCommand;
import com.lawkeys.hcfcore.startup.StartupBarrier;
import com.lawkeys.hcfcore.startup.StartupGate;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Redeem codes (FEATURES.md section 15): {@code /redeem <code>} for players, and
 * {@code /redeemadmin} - with {@code /resetredeem} - for staff.
 *
 * <p>Independent of any web store: a code is a name, a cap, and console commands,
 * so it can hand out whatever the server's other plugins can.
 */
public final class RedeemModule implements Listener {

    public static final String ADMIN_PERMISSION = "hcfcore.redeem.admin";

    private final Plugin plugin;
    private final LangManager lang;
    private final StartupGate startup;

    private volatile boolean enabled = true;
    private volatile long failureCooldownMillis = 3_000L;
    private RedeemCodes codes;
    private BukkitTask saveTask;

    public RedeemModule(Plugin plugin, LangManager lang, StartupGate startup) {
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

    public RedeemCodes getCodes() {
        return codes;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getFailureCooldownMillis() {
        return failureCooldownMillis;
    }

    public void enable(DataSource dataSource, long saveIntervalSeconds) {
        reloadSettings();
        RedeemStore store = dataSource == null
                ? RedeemStore.NO_OP
                : new JdbcRedeemStore(dataSource, message -> plugin.getLogger().info(message));
        this.codes = new RedeemCodes(store, System::currentTimeMillis);

        StartupBarrier.Load load = startup.expect("redeem codes");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                codes.loadAll();
                load.succeeded();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load redeem codes.", e);
                load.failed();
            }
        });
        if (saveIntervalSeconds > 0) {
            long ticks = saveIntervalSeconds * 20L;
            this.saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushQuietly, ticks, ticks);
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        register("redeem", new RedeemCommand(this));
        RedeemAdminCommand admin = new RedeemAdminCommand(this);
        register("redeemadmin", admin);
        register("resetredeem", admin);
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

    public void reloadSettings() {
        ConfigurationSection section = ConfigManager.loadFile(plugin, "redeem.yml");
        if (section == null) {
            return;
        }
        this.enabled = section.getBoolean("enabled", true);
        this.failureCooldownMillis = Math.max(0L, Durations.capSeconds(section.getLong("failed-attempt-cooldown-seconds", 3L), "redeem.yml: failed-attempt-cooldown-seconds", plugin.getLogger()::warning)) * 1000L;
    }

    /**
     * Writes now rather than at the next periodic save: a redemption written late is
     * one a crash could let the same player make twice.
     */
    public void flushSoon() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::flushQuietly);
    }

    private void flushQuietly() {
        try {
            codes.flush();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Saving redeem codes failed; the changes stay queued.", e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (codes != null) {
            codes.forget(event.getPlayer().getUniqueId());
        }
    }

    public void disable() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (codes == null) {
            return;
        }
        try {
            int written = codes.flush();
            if (written > 0) {
                plugin.getLogger().info("Saved " + written + " redeem changes on shutdown.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save redeem codes on shutdown", e);
        }
    }
}
