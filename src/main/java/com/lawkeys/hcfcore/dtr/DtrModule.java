package com.lawkeys.hcfcore.dtr;

import com.lawkeys.hcfcore.api.event.TeamRaidableEvent;
import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.database.dao.JdbcDtrStore;
import com.lawkeys.hcfcore.dtr.command.DtrSubCommands;
import com.lawkeys.hcfcore.dtr.listener.PlayerDeathDtrListener;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.startup.StartupBarrier;
import com.lawkeys.hcfcore.startup.StartupGate;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.team.command.TeamSubCommand;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Wires the DTR module into the server, and closes the reclaim loop.
 *
 * <p>The single most important line in this class is in {@link #enable}: it hands
 * {@link DtrManager} to the claim module as its {@code RaidabilityPolicy}. From
 * that point territory protection follows DTR, with no change to {@code claim/} -
 * which is the whole point of the seam described in ARCHITECTURE.md section 14.
 */
public final class DtrModule {

    private final Plugin plugin;
    private final TeamModule teams;
    private final ClaimModule claims;
    private final LangManager lang;
    private final StartupGate startup;

    private volatile DtrSettings settings = DtrSettings.defaults();
    private volatile DeathCostPolicy deathCost = DeathCostPolicy.ALWAYS;
    private volatile RaidOverride raidOverride = RaidOverride.NONE;
    private DtrManager manager;
    private BukkitTask saveTask;
    private BukkitTask announceTask;

    /** @param claims may be {@code null} if the claim module is not running */
    public DtrModule(Plugin plugin, TeamModule teams, ClaimModule claims, LangManager lang,
                     StartupGate startup) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.claims = claims;
        this.lang = Objects.requireNonNull(lang, "lang");
        this.startup = Objects.requireNonNull(startup, "startup");
    }

    public DtrManager getManager() {
        return manager;
    }

    public TeamModule getTeams() {
        return teams;
    }

    public DtrSettings getSettings() {
        return settings;
    }

    /**
     * Installs the death-cost policy. Called by the {@code phase/} module at
     * startup (no DTR is lost during SOTW); until then every death costs DTR.
     */
    public void setDeathCostPolicy(DeathCostPolicy policy) {
        this.deathCost = Objects.requireNonNull(policy, "policy");
    }

    /**
     * Installs the map-wide raid. Called by the {@code phase/} module at startup
     * (EOTW, the Purge); until then DTR alone decides.
     */
    public void setRaidOverride(RaidOverride override) {
        this.raidOverride = Objects.requireNonNull(override, "override");
    }

    public RaidOverride getRaidOverride() {
        return raidOverride;
    }

    public void enable(DataSource dataSource, long saveIntervalSeconds) {
        reloadSettings();

        DtrStore store = dataSource == null
                ? DtrStore.NO_OP
                : new JdbcDtrStore(dataSource, message -> plugin.getLogger().info(message));

        this.manager = new DtrManager(() -> settings, teams.getManager(), store);

        StartupBarrier.Load load = startup.expect("DTR");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                manager.loadAll();
                plugin.getLogger().info("Loaded DTR state for " + manager.getStates().size() + " teams.");
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load DTR.", e);
                load.failed();
                return;
            }
            // Seeded after the load so a restart does not re-announce raids that
            // were already under way. The load only counts as done once the
            // baseline is in: announcements wait for the barrier (see
            // pollAnnouncements), so the first one to run has something to compare.
            Bukkit.getScheduler().runTask(plugin, () -> {
                manager.primeRaidabilityBaseline();
                load.succeeded();
            });
        });

        // This is what turns the claim module's RaidabilityPolicy.NEVER into the
        // real thing. Without it, claims stay permanently protected.
        if (claims != null && claims.getManager() != null) {
            claims.getManager().setRaidabilityPolicy(manager);
            plugin.getLogger().info("Territory protection is now driven by DTR.");
        } else {
            plugin.getLogger().warning("The claim module is unavailable; DTR will be tracked but "
                    + "territory protection will not follow it.");
        }

        if (saveIntervalSeconds > 0) {
            long ticks = saveIntervalSeconds * 20L;
            this.saveTask = Bukkit.getScheduler()
                    .runTaskTimerAsynchronously(plugin, this::flushQuietly, ticks, ticks);
        }

        long pollSeconds = settings.announcements().pollSeconds();
        if (settings.announcements().onRaidableChange() && pollSeconds > 0) {
            long ticks = pollSeconds * 20L;
            // Main thread: it fires Bukkit events and messages players.
            this.announceTask = Bukkit.getScheduler()
                    .runTaskTimer(plugin, this::pollAnnouncements, ticks, ticks);
        }

        plugin.getServer().getPluginManager()
                .registerEvents(new PlayerDeathDtrListener(this), plugin);

        for (TeamSubCommand subCommand : DtrSubCommands.all(this)) {
            teams.registerSubCommand(subCommand);
        }
    }

    /** Re-reads {@code dtr.yml}; the running manager picks the new values up at once. */
    public void reloadSettings() {
        this.settings = DtrSettingsLoader.load(
                ConfigManager.loadFile(plugin, "dtr.yml"),
                warning -> plugin.getLogger().warning("dtr.yml: " + warning));
    }

    public void disable() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (announceTask != null) {
            announceTask.cancel();
            announceTask = null;
        }
        if (manager == null) {
            return;
        }
        try {
            int written = manager.flush();
            if (written > 0) {
                plugin.getLogger().info("Saved DTR for " + written + " teams on shutdown.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save DTR on shutdown", e);
        }
    }

    private void flushQuietly() {
        try {
            manager.flush();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Periodic DTR save failed; the affected teams stay queued for the next attempt.", e);
        }
    }

    /**
     * Applies a member's death and tells the team what it cost.
     *
     * @param playerName the dead player, for the message
     */
    public void applyDeath(Team team, String playerName) {
        // Before anything: a death that costs nothing freezes no regeneration and
        // announces nothing either.
        if (!deathCost.deathsCostDtr()) {
            return;
        }
        manager.applyDeath(team).ifPresent(remaining -> {
            if (settings.announcements().onDeath()) {
                teams.broadcast(team, null, DtrMessages.DEATH_LOST,
                        "player", playerName,
                        "loss", DtrManager.format(settings.lossPerDeath()),
                        "dtr", DtrManager.format(remaining));
            }
            // A death is the one raidability transition that happens instantly, so
            // it is announced here rather than waiting for the next poll.
            pollAnnouncements();
        });
    }

    /**
     * Fires {@link TeamRaidableEvent} and announces for every team whose
     * raidability flipped since the last check. Main thread only.
     */
    public void pollAnnouncements() {
        // Not before every load has landed. DTR's load clears the baseline, then
        // refills the states one by one; a poll caught in between records a team
        // as not raidable, and the next one "announces" a raid that was already
        // under way before the restart - and fires TeamRaidableEvent for it.
        if (manager == null || !startup.isReady()) {
            return;
        }
        Map<Team, Boolean> changes = manager.pollRaidabilityChanges();
        for (Map.Entry<Team, Boolean> change : changes.entrySet()) {
            Team team = change.getKey();
            boolean raidable = change.getValue();
            plugin.getServer().getPluginManager().callEvent(new TeamRaidableEvent(team, raidable));

            // The event reports the DTR, as its contract says; the broadcast reports
            // the territory, which a map-wide raid keeps open whatever the DTR does -
            // "no longer raidable" during EOTW would be a lie to the whole server, and
            // "now raidable" old news.
            if (!settings.announcements().onRaidableChange() || raidOverride.everyTeamRaidable()) {
                continue;
            }
            String key = raidable ? DtrMessages.BECAME_RAIDABLE : DtrMessages.NO_LONGER_RAIDABLE;
            String message = lang.get(key, "team", team.getName());
            if (!message.isEmpty()) {
                // A raid opening is server-wide news in HCF, not a team secret.
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage(message);
                }
            }
        }
    }

    public LangManager getLang() {
        return lang;
    }
}
