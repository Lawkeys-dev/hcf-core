package com.lawkeys.hcfcore.events.totem;

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
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The Totem on the server: who may break a block of the column, what the column
 * looks like after each break, keeping it whole otherwise, and the announcements and
 * rewards. The rules are in {@link TotemManager}.
 *
 * <p><strong>The column is drawn from the run, never edited block by block</strong>:
 * after every break, and when a run starts or ends, each block of it is set to what
 * the run says - active (quartz), broken (bedrock) or, with no run, idle (bedrock).
 * So a reset, a win or a crash mid-run all come back to the same picture.
 *
 * <p><strong>Listener order on {@link BlockBreakEvent}</strong>, as for the DTC:
 * {@code HIGH} refuses a break that does not count (no sword, no team, creative or
 * spectator, or no run) and suppresses the drops of one that does; {@code MONITOR},
 * {@code ignoreCancelled}, records it - so a player frozen by staff, or refused by
 * any other plugin, never scores - and redraws the column a tick later.
 */
public final class TotemController implements Listener {

    /** How often a refused break is explained again while it keeps happening. */
    private static final long REFUSAL_MESSAGE_INTERVAL_MILLIS = 2000L;

    private final Plugin plugin;
    private final EventModule events;
    private final TeamModule teams;
    private final ClaimModule claims;
    private final RefusalThrottle refusals = new RefusalThrottle(REFUSAL_MESSAGE_INTERVAL_MILLIS);

    private volatile TotemSettings settings = TotemSettings.defaults();
    private final TotemManager manager;
    private BukkitTask tickTask;

    /** @param claims may be {@code null} if the claim module is not running */
    public TotemController(Plugin plugin, EventModule events, TeamModule teams, ClaimModule claims) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.events = Objects.requireNonNull(events, "events");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.claims = claims;
        this.manager = new TotemManager(() -> settings, System::currentTimeMillis);
    }

    public TotemManager getManager() {
        return manager;
    }

    public TotemSettings getSettings() {
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

    /** A running Totem whose definition changed or vanished is stopped, its column put back. */
    public void applySettings(TotemSettings replacement, long tickSeconds) {
        this.settings = Objects.requireNonNull(replacement, "replacement");
        manager.resetScheduleWindow();
        manager.getCurrent().ifPresent(run -> {
            Optional<TotemDefinition> now = replacement.find(run.getDefinition().id());
            if (now.isEmpty() || !now.get().equals(run.getDefinition())) {
                manager.stop().ifPresent(this::announce);
                draw(run.getDefinition(), null);
                plugin.getLogger().info("Stopped totem '" + run.getDefinition().id()
                        + "': it changed or was removed in events.yml.");
            }
        });
        if (tickTask != null) {
            schedule(tickSeconds);
        }
    }

    /** Stops for good; a column left mid-run is put back as it stands between runs. */
    public void disable() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        manager.getCurrent().ifPresent(run -> draw(run.getDefinition(), null));
        manager.stopAll();
    }

    private void tick() {
        try {
            for (TotemUpdate update : manager.tick()) {
                announce(update);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "A Totem tick failed", e);
        }
    }

    // ------------------------------------------------------------------
    // Breaking the column
    // ------------------------------------------------------------------

    /** An allowed hit breaks a block at once, when the Totem says so ({@code instant-break}). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        Block block = event.getBlock();
        if (manager.activeLevel(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()) < 0) {
            return;
        }
        TotemDefinition definition = manager.getCurrent().map(TotemRun::getDefinition).orElse(null);
        if (definition != null && definition.instantBreak() && refusal(event.getPlayer(), definition) == null) {
            event.setInstaBreak(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreakRefuse(BlockBreakEvent event) {
        Block block = event.getBlock();
        String world = block.getWorld().getName();
        Optional<TotemDefinition> column = settings.columnAt(world, block.getX(), block.getY(), block.getZ());
        if (column.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        if (manager.activeLevel(world, block.getX(), block.getY(), block.getZ()) < 0) {
            // Between runs, or a block already broken: the column is scenery, and only
            // a staff member in creative may take it apart.
            if (player.getGameMode() != GameMode.CREATIVE) {
                event.setCancelled(true);
            }
            return;
        }
        String refused = refusal(player, column.get());
        if (refused != null) {
            event.setCancelled(true);
            if (refusals.tryTell(player.getUniqueId(), System.currentTimeMillis())) {
                events.getLang().send(player, refused, "event", column.get().displayName());
            }
            return;
        }
        // The reward is the win, not loose blocks and experience.
        event.setDropItems(false);
        event.setExpToDrop(0);
    }

    /** @return why this player's break would not count, as a message key, or {@code null} when it would */
    private String refusal(Player player, TotemDefinition definition) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return TotemMessages.NO_TEAM;
        }
        if (!definition.allowsTool(player.getInventory().getItemInMainHand().getType().name())) {
            return TotemMessages.WRONG_TOOL;
        }
        return manager.check(teamOf(player)) == TotemManager.BreakCheck.ALLOWED ? null : TotemMessages.NO_TEAM;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreakRecord(BlockBreakEvent event) {
        Block block = event.getBlock();
        int level = manager.activeLevel(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        Optional<TotemRun> run = manager.getCurrent();
        if (level < 0 || run.isEmpty()) {
            return;
        }
        TotemDefinition definition = run.get().getDefinition();
        for (TotemUpdate update : manager.recordBreak(teamOf(event.getPlayer()), level)) {
            announce(update);
        }
        // A tick later: the block this event removes must be gone before it is drawn again.
        Bukkit.getScheduler().runTask(plugin, () -> draw(definition, manager.getCurrent()
                .filter(current -> current.getDefinition().id().equals(definition.id())).orElse(null)));
    }

    private UUID teamOf(Player player) {
        return teams.getManager() == null ? null
                : teams.getManager().getTeamOf(player.getUniqueId()).map(Team::getId).orElse(null);
    }

    /**
     * Sets every block of the column to what {@code run} says; with no run, to the
     * idle block it stands as between runs.
     */
    void draw(TotemDefinition definition, TotemRun run) {
        World world = Bukkit.getWorld(definition.zone().world());
        if (world == null) {
            return;
        }
        for (int level = 0; level < definition.height(); level++) {
            String name = run == null ? definition.idleMaterial()
                    : run.isBroken(level) ? definition.brokenMaterial() : definition.activeMaterial();
            Material material = Material.matchMaterial(name);
            Block block = world.getBlockAt(definition.baseX(), definition.baseY() + level, definition.baseZ());
            if (material != null && block.getType() != material) {
                block.setType(material, false);
            }
        }
    }

    // ------------------------------------------------------------------
    // Explosions and pistons: the column is protected at all times
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isColumn);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isColumn);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isColumn)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isColumn)) {
            event.setCancelled(true);
        }
    }

    private boolean isColumn(Block block) {
        return settings.columnAt(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()).isPresent();
    }

    // ------------------------------------------------------------------
    // Announcements and rewards
    // ------------------------------------------------------------------

    public void announce(TotemUpdate update) {
        Map<String, String> placeholders = new LinkedHashMap<>(update.placeholders());
        Optional<Team> team = teamById(update.teamId());
        placeholders.put("team", team.map(Team::getName).orElse(""));
        if (placeholders.containsKey("previous")) {
            placeholders.put("previous", teamById(parse(placeholders.get("previous"))).map(Team::getName).orElse("?"));
        }
        Optional<TotemDefinition> definition = settings.find(placeholders.getOrDefault("id", ""));
        boolean quiet = update.type() == TotemUpdate.Type.BROKEN
                && definition.map(found -> !found.announceBreaks()).orElse(false);
        if (!quiet) {
            events.broadcast(update.messageKey(), placeholders);
        }
        switch (update.type()) {
            case STARTED -> definition.ifPresent(found -> {
                draw(found, manager.getCurrent().orElse(null));
                checkClaim(found);
            });
            case WON, EXPIRED, STOPPED -> definition.ifPresent(found -> draw(found, null));
            default -> {
            }
        }
        if (update.type() == TotemUpdate.Type.WON) {
            team.filter(winner -> !winner.getType().isSystem()).ifPresent(winner -> definition.ifPresent(found -> {
                teams.getManager().recordTotemWin(winner);
                RewardCommands.run(found.rewardCommands(),
                        Map.of("team", winner.getName(), "event", found.displayName()),
                        command -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command),
                        (command, e) -> plugin.getLogger().log(Level.WARNING,
                                "A Totem reward command failed: " + command, e));
            }));
        }
    }

    private Optional<Team> teamById(UUID id) {
        return teams.getManager() == null || id == null ? Optional.empty() : teams.getManager().getTeam(id);
    }

    private static UUID parse(String id) {
        try {
            return id == null ? null : UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** @return whether this Totem's column stands on a server team's claim, out of reach between runs */
    public boolean isOnSystemClaim(TotemDefinition definition) {
        World world = Bukkit.getWorld(definition.zone().world());
        if (claims == null || claims.getManager() == null || world == null) {
            return false;
        }
        return claims.ownerAt(new Location(world, definition.baseX(), definition.baseY(), definition.baseZ()))
                .map(team -> team.getType().isSystem()).orElse(false);
    }

    private void checkClaim(TotemDefinition definition) {
        if (claims != null && claims.getManager() != null && !isOnSystemClaim(definition)) {
            plugin.getLogger().warning("Totem '" + definition.id() + "': its column is not on a server team's claim. "
                    + "Draw its territory with /events claim " + definition.id() + ".");
        }
    }
}
