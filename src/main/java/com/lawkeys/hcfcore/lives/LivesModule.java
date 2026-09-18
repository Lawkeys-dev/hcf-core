package com.lawkeys.hcfcore.lives;

import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.database.dao.JdbcLivesStore;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.lives.command.LivesCommand;
import com.lawkeys.hcfcore.pvp.Deathban;
import com.lawkeys.hcfcore.pvp.PvpModule;
import com.lawkeys.hcfcore.startup.StartupBarrier;
import com.lawkeys.hcfcore.startup.StartupGate;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Lives (FEATURES.md section 16): the way back from a deathban.
 *
 * <p><strong>Not started at all in kitmap mode</strong>, as the project owner decided
 * on 28/08/2026 - kitmap has no deathban for a life to lift.
 *
 * <p>A life is spent in one of two ways. A friend spends one of theirs with
 * {@code /lives revive <player>}; or, since a deathbanned player cannot join to
 * type anything on a single server, their own is spent when they try to join, if
 * {@code use-on-login} is on. That second way reaches the login check through the
 * {@link com.lawkeys.hcfcore.pvp.DeathbanWaiver} seam of {@code pvp/}. A ban until
 * the end of the map - EOTW's - is never bought back: EOTW is final.
 */
public final class LivesModule implements Listener {

    public static final String ADMIN_PERMISSION = "hcfcore.lives.admin";

    private final Plugin plugin;
    private final LangManager lang;
    private final StartupGate startup;
    private final PvpModule pvp;

    private volatile boolean enabled = true;
    private volatile int startingLives;
    private volatile boolean useOnLogin = true;
    private volatile boolean allowSend = true;
    private Lives lives;
    private BukkitTask saveTask;
    /** Players let in by a life at login, to be told once they are in. */
    private final Set<UUID> spentAtLogin = ConcurrentHashMap.newKeySet();

    /** @param pvp may be {@code null}; without deathbans there is nothing to revive from */
    public LivesModule(Plugin plugin, LangManager lang, StartupGate startup, PvpModule pvp) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lang = Objects.requireNonNull(lang, "lang");
        this.startup = Objects.requireNonNull(startup, "startup");
        this.pvp = pvp;
    }

    public LangManager getLang() {
        return lang;
    }

    public StartupGate getStartup() {
        return startup;
    }

    public Lives getLives() {
        return lives;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isSendAllowed() {
        return allowSend;
    }

    public void enable(DataSource dataSource, long saveIntervalSeconds) {
        reloadSettings();
        LivesStore store = dataSource == null
                ? LivesStore.NO_OP
                : new JdbcLivesStore(dataSource, message -> plugin.getLogger().info(message));
        this.lives = new Lives(store, () -> startingLives);

        StartupBarrier.Load load = startup.expect("lives");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                lives.loadAll();
                load.succeeded();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load lives.", e);
                load.failed();
            }
        });
        if (saveIntervalSeconds > 0) {
            long ticks = saveIntervalSeconds * 20L;
            this.saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushQuietly, ticks, ticks);
        }
        if (pvp != null) {
            pvp.setDeathbanWaiver(this::spendAtLogin);
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        LivesCommand command = new LivesCommand(this);
        for (String name : new String[] {"lives", "revive"}) {
            PluginCommand registered = plugin.getServer().getPluginCommand(name);
            if (registered == null) {
                plugin.getLogger().severe("The '" + name + "' command is missing from plugin.yml.");
                continue;
            }
            registered.setExecutor(command);
            registered.setTabCompleter(command);
        }
    }

    public void reloadSettings() {
        ConfigurationSection section = ConfigManager.loadFile(plugin, "lives.yml");
        if (section == null) {
            return;
        }
        this.enabled = section.getBoolean("enabled", true);
        this.startingLives = Math.max(0, section.getInt("starting-lives", 0));
        this.useOnLogin = section.getBoolean("use-on-login", true);
        this.allowSend = section.getBoolean("allow-send", true);
    }

    /** The {@code DeathbanWaiver}: a deathbanned player with a life spends it and comes in. */
    private boolean spendAtLogin(UUID playerId, Deathban ban) {
        if (!LoginWaiver.spend(enabled, useOnLogin, ban, lives, playerId)) {
            return false;
        }
        pvp.getDeathbans().lift(playerId);
        spentAtLogin.add(playerId);
        flushSoon();
        return true;
    }

    /** Why a revive did or did not happen. */
    public enum Revive {
        DONE,
        NOT_BANNED,
        MAP_END,
        NO_LIFE
    }

    /** Spends one of {@code reviver}'s lives to lift {@code target}'s deathban. */
    public Revive revive(UUID reviver, UUID target) {
        Optional<Deathban> ban = pvp == null ? Optional.empty() : pvp.getDeathbans().getActiveBan(target);
        if (ban.isEmpty()) {
            return Revive.NOT_BANNED;
        }
        if (ban.get().isUntilMapEnd()) {
            return Revive.MAP_END;
        }
        if (!lives.spendOne(reviver)) {
            return Revive.NO_LIFE;
        }
        pvp.getDeathbans().lift(target);
        flushSoon();
        return Revive.DONE;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (spentAtLogin.remove(event.getPlayer().getUniqueId())) {
            lang.send(event.getPlayer(), LivesMessages.USED_AT_LOGIN,
                    "lives", String.valueOf(lives.get(event.getPlayer().getUniqueId())));
        }
    }

    /**
     * Writes lives - and the lifted ban - now: a crash between the two saves must not
     * leave a life spent and the ban back.
     */
    public void flushSoon() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::flushQuietly);
        if (pvp != null) {
            pvp.flushSoon();
        }
    }

    private void flushQuietly() {
        try {
            lives.flush();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Saving lives failed; the changes stay queued.", e);
        }
    }

    public void disable() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (lives == null) {
            return;
        }
        try {
            int written = lives.flush();
            if (written > 0) {
                plugin.getLogger().info("Saved " + written + " lives on shutdown.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save lives on shutdown", e);
        }
    }
}
