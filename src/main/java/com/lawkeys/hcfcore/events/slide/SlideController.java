package com.lawkeys.hcfcore.events.slide;

import com.lawkeys.hcfcore.events.EventModule;
import com.lawkeys.hcfcore.events.Occupant;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.util.RewardCommands;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Slide on the server: who stands in the zone, deaths, announcements and
 * rewards. The rules are in {@link SlideManager}.
 *
 * <p>Creative and spectator players never score, and a teamless player never
 * scores either - both hardcoded, like the same rule for the core events and for
 * a teamless player holding a KOTH.
 */
public final class SlideController implements Listener {

    private final Plugin plugin;
    private final EventModule events;
    private final TeamModule teams;

    private volatile SlideSettings settings = SlideSettings.defaults();
    private final SlideManager manager;
    private BukkitTask tickTask;

    public SlideController(Plugin plugin, EventModule events, TeamModule teams) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.events = Objects.requireNonNull(events, "events");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.manager = new SlideManager(() -> settings, System::currentTimeMillis);
    }

    public SlideManager getManager() {
        return manager;
    }

    public SlideSettings getSettings() {
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

    public void applySettings(SlideSettings replacement, long tickSeconds) {
        this.settings = Objects.requireNonNull(replacement, "replacement");
        manager.resetScheduleWindow();
        manager.getCurrent().ifPresent(run -> {
            Optional<SlideDefinition> now = replacement.find(run.getDefinition().id());
            if (now.isEmpty() || !now.get().equals(run.getDefinition())) {
                manager.stop().ifPresent(this::announce);
                plugin.getLogger().info("Stopped Slide '" + run.getDefinition().id()
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
            for (SlideUpdate update : manager.tick(occupants())) {
                announce(update);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "A Slide tick failed", e);
        }
    }

    /** @return every team member standing in the running Slide's zone right now - creative and spectator left out */
    private List<Occupant> occupants() {
        Optional<SlideRun> run = manager.getCurrent();
        if (run.isEmpty() || teams.getManager() == null) {
            return List.of();
        }
        com.lawkeys.hcfcore.util.Cuboid zone = run.get().getDefinition().zone();
        List<Occupant> inZone = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Location at = player.getLocation();
            if (at.getWorld() == null || !zone.contains(at.getWorld().getName(), at.getX(), at.getY(), at.getZ())) {
                continue;
            }
            UUID teamId = teams.getManager().getTeamOf(player.getUniqueId()).map(Team::getId).orElse(null);
            if (teamId != null) {
                inZone.add(Occupant.of(player.getUniqueId(), teamId));
            }
        }
        return inZone;
    }

    /**
     * A member's death costs the team points, wherever it happens; a cancelled
     * one, where they are revived, does not. {@code announce-deaths} only mutes
     * the broadcast - the points are always lost.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (teams.getManager() == null) {
            return;
        }
        Optional<SlideRun> run = manager.getCurrent();
        if (run.isEmpty()) {
            return;
        }
        UUID teamId = teams.getManager().getTeamOf(event.getEntity().getUniqueId()).map(Team::getId).orElse(null);
        boolean announce = run.get().getDefinition().announceDeaths();
        manager.recordDeath(teamId).ifPresent(update -> {
            if (announce) {
                announce(update);
            }
        });
    }

    /** Says it to everybody, and pays the reward when somebody won. */
    public void announce(SlideUpdate update) {
        Map<String, String> placeholders = new LinkedHashMap<>(update.placeholders());
        Optional<Team> team = teams.getManager() == null ? Optional.empty()
                : Optional.ofNullable(update.teamId()).flatMap(id -> teams.getManager().getTeam(id));
        placeholders.put("team", team.map(Team::getName).orElse(""));
        events.broadcast(update.messageKey(), placeholders);
        if (update.type() == SlideUpdate.Type.WON) {
            team.ifPresent(winner -> {
                if (winner.getType().isSystem()) {
                    return;
                }
                teams.getManager().recordSlideWin(winner);
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
                        "A Slide reward command failed: " + command, e)));
    }
}
