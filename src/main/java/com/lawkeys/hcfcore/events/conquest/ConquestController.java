package com.lawkeys.hcfcore.events.conquest;

import com.lawkeys.hcfcore.events.EventModule;
import com.lawkeys.hcfcore.events.Occupant;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.util.RewardCommands;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Conquest on the server: who stands in which zone, deaths, announcements and
 * rewards. The rules are in {@link ConquestManager}.
 *
 * <p>A third engine in family A of ARCHITECTURE.md section 9, beside the captures
 * and Kill the King, sharing what is really common with them: {@code /events},
 * {@code events.yml}, the daily schedule and the id space.
 */
public final class ConquestController implements Listener {

    private final Plugin plugin;
    private final EventModule events;
    private final TeamModule teams;

    private volatile ConquestSettings settings = ConquestSettings.defaults();
    private final ConquestManager manager;
    private BukkitTask tickTask;

    public ConquestController(Plugin plugin, EventModule events, TeamModule teams) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.events = Objects.requireNonNull(events, "events");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.manager = new ConquestManager(() -> settings, System::currentTimeMillis);
    }

    public ConquestManager getManager() {
        return manager;
    }

    public ConquestSettings getSettings() {
        return settings;
    }

    public void enable(long tickSeconds) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        schedule(tickSeconds);
    }

    private void schedule(long tickSeconds) {
        if (tickTask != null) {
            tickTask.cancel();
        }
        long ticks = Math.max(1L, tickSeconds) * 20L;
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, ticks, ticks);
    }

    /**
     * Takes new settings. A running Conquest whose definition changed or vanished is
     * stopped: it would otherwise keep scoring zones nobody can see any more.
     */
    public void applySettings(ConquestSettings replacement, long tickSeconds) {
        this.settings = Objects.requireNonNull(replacement, "replacement");
        manager.resetScheduleWindow();
        manager.getCurrent().ifPresent(run -> {
            Optional<ConquestDefinition> now = replacement.find(run.getDefinition().id());
            if (now.isEmpty() || !now.get().equals(run.getDefinition())) {
                manager.stop().ifPresent(this::announce);
                plugin.getLogger().info("Stopped Conquest '" + run.getDefinition().id()
                        + "': it changed or was removed in events.yml.");
            }
        });
        if (tickTask != null) {
            schedule(tickSeconds);
        }
    }

    public void disable() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        manager.stopAll();
    }

    private void tick() {
        try {
            for (ConquestUpdate update : manager.tick(occupants())) {
                announce(update);
            }
        } catch (Exception e) {
            // One bad tick must not kill the repeating task for the session.
            plugin.getLogger().log(Level.SEVERE, "A Conquest tick failed", e);
        }
    }

    /** @return who stands in each zone of the running Conquest, in one pass over the players */
    private Map<String, List<Occupant>> occupants() {
        Optional<ConquestRun> run = manager.getCurrent();
        if (run.isEmpty()) {
            return Map.of();
        }
        List<ConquestRun.ZoneState> zones = run.get().zones();
        Map<String, List<Occupant>> occupants = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location at = player.getLocation();
            if (at.getWorld() == null) {
                continue;
            }
            for (ConquestRun.ZoneState zone : zones) {
                if (zone.zone().area().contains(at.getWorld().getName(), at.getX(), at.getY(), at.getZ())) {
                    UUID teamId = teams.getManager().getTeamOf(player.getUniqueId()).map(Team::getId).orElse(null);
                    occupants.computeIfAbsent(zone.zone().id(), id -> new ArrayList<>())
                            .add(Occupant.of(player.getUniqueId(), teamId));
                }
            }
        }
        return occupants;
    }

    /**
     * A member's death costs the team points, wherever it happens; a cancelled one,
     * where they are revived, does not.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (manager.getCurrent().isEmpty() || teams.getManager() == null) {
            return;
        }
        UUID teamId = teams.getManager().getTeamOf(event.getEntity().getUniqueId()).map(Team::getId).orElse(null);
        manager.recordDeath(teamId).ifPresent(this::announce);
    }

    /** Says it to everybody, and pays the reward when somebody won. */
    public void announce(ConquestUpdate update) {
        Map<String, String> placeholders = new LinkedHashMap<>(update.placeholders());
        Optional<Team> team = Optional.ofNullable(update.teamId()).flatMap(id -> teams.getManager().getTeam(id));
        placeholders.put("team", team.map(Team::getName).orElse(""));
        events.broadcast(update.messageKey(), placeholders);
        if (update.type() == ConquestUpdate.Type.WON) {
            team.ifPresent(winner -> {
                teams.getManager().recordConquestWin(winner);
                reward(winner, placeholders.getOrDefault("id", ""));
            });
        }
    }

    private void reward(Team winner, String id) {
        settings.find(id).ifPresent(definition -> RewardCommands.run(
                definition.rewardCommands(),
                Map.of("team", winner.getName(), "event", definition.displayName()),
                command -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command),
                (command, e) -> plugin.getLogger().log(Level.WARNING,
                        "A Conquest reward command failed: " + command, e)));
    }
}
