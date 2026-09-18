package com.lawkeys.hcfcore.phase;

import com.lawkeys.hcfcore.claim.ClaimManager;
import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.database.dao.JdbcPhaseStore;
import com.lawkeys.hcfcore.dtr.DtrModule;
import com.lawkeys.hcfcore.events.AgendaEntry;
import com.lawkeys.hcfcore.events.DailySchedule;
import com.lawkeys.hcfcore.events.EventModule;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.phase.command.PhaseCommand;
import com.lawkeys.hcfcore.pvp.PvpModule;
import com.lawkeys.hcfcore.startup.StartupBarrier;
import com.lawkeys.hcfcore.startup.StartupGate;
import com.lawkeys.hcfcore.util.ColorCodes;
import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Wires SOTW and EOTW into the server.
 *
 * <p><strong>It changes other modules without their knowing it exists.</strong>
 * Each of them declared the question it needed answered - {@code pvp/} whether a
 * hit may land and how a death is banned, {@code dtr/} whether a death costs DTR,
 * {@code claim/} whether players may claim and whether a team is raidable - and
 * this module, enabled after them all, plugs {@link PhaseManager}'s answers in
 * (ARCHITECTURE.md section 14). Raidability is <em>wrapped</em>, not replaced: the
 * DTR still decides outside EOTW.
 *
 * <p>The phase is persisted - a SOTW survives a restart - and loaded at startup
 * behind {@code StartupGate}, like any other data.
 */
public final class PhaseModule {

    /** Start and stop SOTW and EOTW. Declared in plugin.yml. */
    public static final String ADMIN_PERMISSION = "hcfcore.phase.admin";

    private final Plugin plugin;
    private final LangManager lang;
    private final StartupGate startup;

    private volatile PhaseSettings settings = PhaseSettings.defaults();
    private PhaseManager manager;
    private BukkitTask tickTask;
    private final AtomicBoolean flushing = new AtomicBoolean();

    public PhaseModule(Plugin plugin, LangManager lang, StartupGate startup) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lang = Objects.requireNonNull(lang, "lang");
        this.startup = Objects.requireNonNull(startup, "startup");
    }

    public PhaseManager getManager() {
        return manager;
    }

    public PhaseSettings getSettings() {
        return settings;
    }

    public LangManager getLang() {
        return lang;
    }

    public StartupGate getStartup() {
        return startup;
    }

    /**
     * @param claims may be {@code null}: claiming and raids then follow no phase
     * @param dtr    may be {@code null}: deaths then follow no phase for DTR
     * @param pvp    may be {@code null}: combat and deathbans then follow no phase
     * @param events may be {@code null}: the phases are then missing from {@code /events}
     */
    public void enable(DataSource dataSource, ClaimModule claims, DtrModule dtr, PvpModule pvp, EventModule events) {
        reloadSettings();
        PhaseStore store = dataSource == null
                ? PhaseStore.NO_OP
                : new JdbcPhaseStore(dataSource, message -> plugin.getLogger().info(message));
        this.manager = new PhaseManager(() -> settings, store);

        StartupBarrier.Load load = startup.expect("the map phase");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                manager.loadAll();
                if (manager.isSotw()) {
                    plugin.getLogger().info("SOTW is running: " + Durations.format(manager.getSotwRemainingSeconds())
                            + " left.");
                }
                if (manager.isEotw()) {
                    plugin.getLogger().info("EOTW is under way.");
                }
                if (manager.isPurge()) {
                    plugin.getLogger().info("A Purge is running: " + Durations.format(manager.getPurgeRemainingSeconds())
                            + " left.");
                }
                load.succeeded();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load the map phase.", e);
                load.failed();
            }
        });

        if (pvp != null) {
            pvp.setProtection(manager::combatRefusal);
            pvp.setDeathbanPolicy(manager::deathbanRule);
        }
        if (dtr != null) {
            dtr.setDeathCostPolicy(manager::deathsCostDtr);
            // What EOTW and the Purge open, /team dtr and the DTR announcements say so.
            dtr.setRaidOverride(() -> manager.isEotw() || manager.isPurge());
        }
        if (claims != null && claims.getManager() != null) {
            ClaimManager claimManager = claims.getManager();
            claimManager.setClaimingPolicy(manager::claimingRefusal);
            // /team lockclaim works during SOTW only (decided 12/09/2026).
            claimManager.setLockWindow(manager::isSotw);
            // Wrapped around whatever is installed - the DTR's policy - so that
            // outside EOTW and the Purge nothing about raids changes.
            claimManager.setRaidabilityPolicy(manager.raidability(claimManager.getRaidabilityPolicy()));
        }
        if (events != null) {
            events.addAgendaContributor(this::agenda);
        }

        PhaseCommand command = new PhaseCommand(this);
        registerCommand("sotw", command);
        registerCommand("eotw", command);
        registerCommand("purge", command);

        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void registerCommand(String name, PhaseCommand executor) {
        PluginCommand command = plugin.getServer().getPluginCommand(name);
        if (command == null) {
            plugin.getLogger().severe("The '" + name + "' command is missing from plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    public void reloadSettings() {
        this.settings = PhaseSettingsLoader.load(
                ConfigManager.loadFile(plugin, "phases.yml"),
                warning -> plugin.getLogger().warning("phases.yml: " + warning));
    }

    /** Writes what is still pending, synchronously - the scheduler no longer runs tasks. */
    public void disable() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (manager == null) {
            return;
        }
        try {
            manager.flush();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save the map phase on shutdown", e);
        }
    }

    private void tick() {
        // Nothing starts or ends before the stored phase is loaded.
        if (!startup.isReady()) {
            return;
        }
        try {
            for (PhaseUpdate update : manager.tick()) {
                broadcast(update);
            }
            flushIfPending();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "A phase tick failed", e);
        }
    }

    /**
     * Writes a change at once, off the main thread. The phase changes rarely and a
     * lost SOTW or EOTW is a lost map, so it does not wait for a periodic save; a
     * write that fails is retried at the next tick, one at a time.
     */
    public void flushIfPending() {
        if (!manager.hasPendingWrites() || !plugin.isEnabled() || !flushing.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                manager.flush();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Writing the map phase failed; retrying.", e);
            } finally {
                flushing.set(false);
            }
        });
    }

    /** Sends an update to everybody online, and mirrors it to the console log. */
    public void broadcast(PhaseUpdate update) {
        String message = lang.get(update.messageKey(), update.placeholders());
        if (message.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
        plugin.getLogger().info(ColorCodes.strip(message));
    }

    /** @return a scheduled date as players read it, in the configured zone */
    public String formatDate(long epochMillis) {
        return PhaseSettingsLoader.DATE.format(Instant.ofEpochMilli(epochMillis).atZone(settings.timeZone()));
    }

    /** @return when the next scheduled Purge starts, formatted, or empty when none is scheduled */
    public Optional<String> nextPurge() {
        return DailySchedule.next(settings.purge().times(), settings.timeZone(), System.currentTimeMillis())
                .map(next -> formatDate(next.toInstant().toEpochMilli()));
    }

    /** The phases' lines in {@code /events}. */
    private List<AgendaEntry> agenda() {
        List<AgendaEntry> entries = new ArrayList<>();
        if (manager == null) {
            return entries;
        }
        long now = System.currentTimeMillis();
        if (manager.isSotw()) {
            entries.add(AgendaEntry.of(PhaseMessages.AGENDA_SOTW_ACTIVE,
                    "time", Durations.format(manager.getSotwRemainingSeconds())));
        } else if (settings.sotwScheduledAt() > now) {
            entries.add(AgendaEntry.of(PhaseMessages.AGENDA_SOTW_SCHEDULED,
                    "time", formatDate(settings.sotwScheduledAt())));
        }
        if (manager.isPurge()) {
            entries.add(AgendaEntry.of(PhaseMessages.AGENDA_PURGE_ACTIVE,
                    "time", Durations.format(manager.getPurgeRemainingSeconds())));
        } else if (!manager.isEotw()) {
            nextPurge().ifPresent(next -> entries.add(AgendaEntry.of(PhaseMessages.AGENDA_PURGE_SCHEDULED, "time", next)));
        }
        if (manager.isEotw()) {
            entries.add(new AgendaEntry(PhaseMessages.AGENDA_EOTW_ACTIVE, Map.of()));
        } else if (settings.eotwScheduledAt() > now) {
            entries.add(AgendaEntry.of(PhaseMessages.AGENDA_EOTW_SCHEDULED,
                    "time", formatDate(settings.eotwScheduledAt())));
        }
        return entries;
    }
}
