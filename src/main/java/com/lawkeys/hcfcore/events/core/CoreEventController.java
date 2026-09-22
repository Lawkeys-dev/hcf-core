package com.lawkeys.hcfcore.events.core;

import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.events.EventModule;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.util.RefusalThrottle;
import com.lawkeys.hcfcore.util.RewardCommands;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * DTC and Last Break on the server: who may break the core right now, putting it
 * back, protecting it from anything but a legitimate break, and the
 * announcements and rewards. The rules are in {@link CoreEventManager}.
 *
 * <p><strong>Listener order on {@link BlockBreakEvent}</strong> (both
 * {@code ignoreCancelled = true}, so an event another plugin or the claim
 * protection listener already cancelled - see the {@code BreakAllowance} seam -
 * never reaches either):
 * <ol>
 *   <li>{@code HIGH} - refuses the break outright (creative, spectator, no team,
 *       a team still in cooldown), or lets it through and suppresses the normal
 *       drops and experience, since the core rewards a win, not loose blocks;</li>
 *   <li>{@code MONITOR} - the break is real: records it, announces whatever the
 *       rules say happened, and puts the core back one tick later - even when
 *       this very break just ended the run, since the block is permanent and
 *       stays in place either way.</li>
 * </ol>
 *
 * <p>The core is protected from explosions and pistons at all times, whether a
 * run is under way or not: it is scenery for the next run, not a target.
 */
public final class CoreEventController implements Listener {

    /** How often a refused break is explained again while it keeps happening. */
    private static final long REFUSAL_MESSAGE_INTERVAL_MILLIS = 2000L;

    private final Plugin plugin;
    private final EventModule events;
    private final TeamModule teams;
    private final ClaimModule claims;
    private final RefusalThrottle refusals = new RefusalThrottle(REFUSAL_MESSAGE_INTERVAL_MILLIS);

    private volatile CoreSettings settings = CoreSettings.defaults();
    private final CoreEventManager manager;
    private BukkitTask tickTask;

    /** @param claims may be {@code null} if the claim module is not running */
    public CoreEventController(Plugin plugin, EventModule events, TeamModule teams, ClaimModule claims) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.events = Objects.requireNonNull(events, "events");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.claims = claims;
        this.manager = new CoreEventManager(() -> settings, System::currentTimeMillis);
    }

    public CoreEventManager getManager() {
        return manager;
    }

    public CoreSettings getSettings() {
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

    /** A running event whose definition changed or vanished is stopped, like Conquest. */
    public void applySettings(CoreSettings replacement, long tickSeconds) {
        this.settings = Objects.requireNonNull(replacement, "replacement");
        manager.resetScheduleWindow();
        manager.getCurrent().ifPresent(run -> {
            Optional<CoreEventDefinition> now = replacement.find(run.getDefinition().id());
            if (now.isEmpty() || !now.get().equals(run.getDefinition())) {
                manager.stop().ifPresent(this::announce);
                plugin.getLogger().info("Stopped core event '" + run.getDefinition().id()
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
            for (CoreUpdate update : manager.tick()) {
                announce(update);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "A DTC/Last Break tick failed", e);
        }
    }

    // ------------------------------------------------------------------
    // Break protection
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreakRefuse(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!manager.isCore(block.getWorld().getName(), block.getX(), block.getY(), block.getZ())) {
            return;
        }
        Player player = event.getPlayer();
        // Creative and spectator never score, and never break the core - hardcoded,
        // like a teamless player never holding a KOTH: this is not a rule an
        // operator would reasonably want to turn off.
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            refuse(event, player, null);
            return;
        }
        UUID teamId = teams.getManager() == null ? null
                : teams.getManager().getTeamOf(player.getUniqueId()).map(Team::getId).orElse(null);
        CoreEventManager.BreakCheck check = manager.check(teamId);
        if (!check.isAllowed()) {
            refuse(event, player, check);
            return;
        }
        // The reward is the win, not loose blocks and experience from a core
        // that is about to be put back exactly as it was.
        event.setDropItems(false);
        event.setExpToDrop(0);
    }

    private void refuse(BlockBreakEvent event, Player player, CoreEventManager.BreakCheck check) {
        event.setCancelled(true);
        if (!refusals.tryTell(player.getUniqueId(), System.currentTimeMillis())) {
            return;
        }
        manager.getCurrent().ifPresent(run -> {
            CoreEventDefinition definition = run.getDefinition();
            boolean cooldown = check != null && check.status() == CoreEventManager.BreakCheck.Status.COOLDOWN;
            String key = definition.kind() == CoreEventKind.DTC
                    ? (cooldown ? CoreMessages.DTC_COOLDOWN : CoreMessages.DTC_NO_TEAM)
                    : (cooldown ? CoreMessages.LAST_BREAK_COOLDOWN : CoreMessages.LAST_BREAK_NO_TEAM);
            events.getLang().send(player, key, "time", cooldown ? String.valueOf(check.secondsLeft()) : "0");
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreakRecord(BlockBreakEvent event) {
        Block block = event.getBlock();
        String world = block.getWorld().getName();
        Optional<CoreRun> run = manager.getCurrent()
                .filter(current -> current.getDefinition().isCoreBlock(world, block.getX(), block.getY(), block.getZ()));
        if (run.isEmpty()) {
            return;
        }
        CoreEventDefinition definition = run.get().getDefinition();
        UUID teamId = teams.getManager() == null ? null
                : teams.getManager().getTeamOf(event.getPlayer().getUniqueId()).map(Team::getId).orElse(null);
        for (CoreUpdate update : manager.recordBreak(teamId)) {
            announce(update);
        }
        // Always put back, even when this break just ended the run: the core is
        // permanent scenery, not a one-time objective.
        Bukkit.getScheduler().runTask(plugin, () -> resetCore(definition, block));
    }

    private void resetCore(CoreEventDefinition definition, Block block) {
        Material material = Material.matchMaterial(definition.coreMaterial());
        if (material != null && block.getWorld() != null) {
            block.setBlockData(material.createBlockData(), false);
        }
    }

    // ------------------------------------------------------------------
    // Explosions and pistons: the core is protected at all times
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isConfiguredCore);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isConfiguredCore);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isConfiguredCore)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isConfiguredCore)) {
            event.setCancelled(true);
        }
    }

    private boolean isConfiguredCore(Block block) {
        String world = block.getWorld().getName();
        for (CoreEventDefinition definition : settings.definitions()) {
            if (definition.isCoreBlock(world, block.getX(), block.getY(), block.getZ())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Announcements, rewards and the claim warning
    // ------------------------------------------------------------------

    public void announce(CoreUpdate update) {
        Map<String, String> placeholders = new LinkedHashMap<>(update.placeholders());
        Optional<Team> team = teams.getManager() == null ? Optional.empty()
                : Optional.ofNullable(update.teamId()).flatMap(id -> teams.getManager().getTeam(id));
        placeholders.put("team", team.map(Team::getName).orElse(""));
        events.broadcast(update.messageKey(), placeholders);

        if (update.type() == CoreUpdate.Type.STARTED) {
            settings.find(placeholders.getOrDefault("id", "")).ifPresent(definition -> {
                placeholders.put("id", definition.id());
                ensureCorePlaced(definition);
                checkClaim(definition);
            });
        }
        if (update.type() == CoreUpdate.Type.WON) {
            team.ifPresent(winner -> {
                if (winner.getType().isSystem()) {
                    return;
                }
                settings.find(placeholders.getOrDefault("id", "")).ifPresent(definition -> {
                    if (definition.kind() == CoreEventKind.DTC) {
                        teams.getManager().recordDtcWin(winner);
                    } else {
                        teams.getManager().recordLastBreakWin(winner);
                    }
                    reward(winner, definition);
                });
            });
        }
    }

    private void reward(Team winner, CoreEventDefinition definition) {
        RewardCommands.run(definition.rewardCommands(),
                Map.of("team", winner.getName(), "event", definition.displayName()),
                command -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command),
                (command, e) -> plugin.getLogger().log(Level.WARNING,
                        "A core event reward command failed: " + command, e));
    }

    private void ensureCorePlaced(CoreEventDefinition definition) {
        World world = Bukkit.getWorld(definition.zone().world());
        if (world == null) {
            return;
        }
        Block block = world.getBlockAt(definition.coreX(), definition.coreY(), definition.coreZ());
        Material material = Material.matchMaterial(definition.coreMaterial());
        if (material != null && block.getType() != material) {
            block.setType(material, false);
        }
    }

    /**
     * Whether this core stands on a system team's claim. Used both to warn the
     * console when a run starts and, directly, to warn staff at {@code /events
     * setcore}: the core is permanent, so an unclaimed one stays reachable
     * (and breakable through territory protection - there is none) between runs.
     */
    public boolean isOnSystemClaim(CoreEventDefinition definition) {
        if (claims == null || claims.getManager() == null) {
            return false;
        }
        World world = Bukkit.getWorld(definition.zone().world());
        if (world == null) {
            return false;
        }
        Location at = new Location(world, definition.coreX(), definition.coreY(), definition.coreZ());
        return claims.ownerAt(at)
                .map(team -> team.getType().isSystem())
                .orElse(false);
    }

    private void checkClaim(CoreEventDefinition definition) {
        if (claims == null || claims.getManager() == null) {
            return;
        }
        if (!isOnSystemClaim(definition)) {
            plugin.getLogger().warning("Core event '" + definition.id() + "': its core is not on a system team's "
                    + "claim. Territory protection will not apply to it between runs - draw its territory with "
                    + "/events claim " + definition.id() + ".");
        }
    }

    /** @return whether this exact block is the core of a configured DTC or Last Break, running or not */
    public boolean isConfiguredCore(String world, int x, int y, int z) {
        for (CoreEventDefinition definition : settings.definitions()) {
            if (definition.isCoreBlock(world, x, y, z)) {
                return true;
            }
        }
        return false;
    }
}
