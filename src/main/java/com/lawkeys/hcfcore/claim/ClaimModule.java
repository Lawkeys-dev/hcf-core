package com.lawkeys.hcfcore.claim;

import com.lawkeys.hcfcore.claim.command.ClaimSubCommands;
import com.lawkeys.hcfcore.claim.listener.ClaimProtectionListener;
import com.lawkeys.hcfcore.claim.listener.CrossBorderListener;
import com.lawkeys.hcfcore.claim.listener.TeamDisbandClaimListener;
import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.database.dao.JdbcClaimStore;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.startup.StartupBarrier;
import com.lawkeys.hcfcore.startup.StartupGate;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.team.command.TeamSubCommand;
import com.lawkeys.hcfcore.util.ChunkPosition;
import com.lawkeys.hcfcore.util.WorldPosition;
import com.lawkeys.hcfcore.warmup.WarmupModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Wires the claim module into the server.
 *
 * <p>Enabled after {@code TeamModule}, which it depends on: territory belongs to
 * teams, and its {@code /team claim} family is grafted onto the existing team
 * command through {@link TeamModule#registerSubCommand}.
 */
public final class ClaimModule {

    private final Plugin plugin;
    private final TeamModule teams;
    private final LangManager lang;
    private final StartupGate startup;
    private final WarmupModule warmups;

    private volatile ClaimSettings settings = ClaimSettings.defaults();
    private volatile TeleportGuard teleportGuard = TeleportGuard.ALLOW;
    private volatile BuildOverride buildOverride = BuildOverride.NONE;
    private volatile BreakAllowance breakAllowance = BreakAllowance.NONE;
    private ClaimManager manager;
    private BukkitTask saveTask;
    private final com.lawkeys.hcfcore.claim.wand.WandSessions wandSessions;
    /** Blocks shown to one player only: {@code /team map}'s pillars, a locked claim's wall. */
    private final com.lawkeys.hcfcore.claim.view.ClientBlocks pillarView =
            new com.lawkeys.hcfcore.claim.view.ClientBlocks();
    private final com.lawkeys.hcfcore.claim.view.ClientBlocks wallView =
            new com.lawkeys.hcfcore.claim.view.ClientBlocks();
    private final com.lawkeys.hcfcore.claim.view.MapPillars mapPillars =
            new com.lawkeys.hcfcore.claim.view.MapPillars(this, pillarView);
    private volatile com.lawkeys.hcfcore.claim.view.SafeZoneWallPolicy safeZoneWallPolicy =
            com.lawkeys.hcfcore.claim.view.SafeZoneWallPolicy.NONE;
    private final com.lawkeys.hcfcore.claim.view.ClaimWalls claimWalls =
            new com.lawkeys.hcfcore.claim.view.ClaimWalls(this, wallView);

    /**
     * @param warmups the countdowns behind {@code /team hq}, {@code /team base} and
     *                {@code /team stuck}, shared with {@code general/}
     */
    public ClaimModule(Plugin plugin, TeamModule teams, LangManager lang, StartupGate startup,
                       WarmupModule warmups) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.lang = Objects.requireNonNull(lang, "lang");
        this.startup = Objects.requireNonNull(startup, "startup");
        this.warmups = Objects.requireNonNull(warmups, "warmups");
        this.wandSessions = new com.lawkeys.hcfcore.claim.wand.WandSessions(
                new com.lawkeys.hcfcore.claim.wand.ClaimWand(plugin), lang, () -> settings.wand());
    }

    /**
     * @return the claiming wands in use - the traditional HCF claim, and what
     *         {@code events/} draws its zones with
     */
    public com.lawkeys.hcfcore.claim.wand.WandSessions getWandSessions() {
        return wandSessions;
    }

    /** Installs who sees a wall around a safe zone. Called by {@code pvp/}. */
    public void setSafeZoneWallPolicy(com.lawkeys.hcfcore.claim.view.SafeZoneWallPolicy policy) {
        this.safeZoneWallPolicy = Objects.requireNonNull(policy, "policy");
    }

    /** @return who sees a wall around a safe zone; nobody without the PvP module */
    public com.lawkeys.hcfcore.claim.view.SafeZoneWallPolicy getSafeZoneWallPolicy() {
        return safeZoneWallPolicy;
    }

    /** @return {@code /team map} drawn in the world, on the corners of the claims around */
    public com.lawkeys.hcfcore.claim.view.MapPillars getMapPillars() {
        return mapPillars;
    }

    public WarmupModule getWarmups() {
        return warmups;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public ClaimManager getManager() {
        return manager;
    }

    public TeamModule getTeams() {
        return teams;
    }

    public LangManager getLang() {
        return lang;
    }

    public ClaimSettings getSettings() {
        return settings;
    }

    /**
     * Installs the guard consulted before a team home teleport. Called by the
     * {@code pvp/} module at startup; until then nothing is ever blocked.
     */
    public void setTeleportGuard(TeleportGuard teleportGuard) {
        this.teleportGuard = java.util.Objects.requireNonNull(teleportGuard, "teleportGuard");
    }

    public TeleportGuard getTeleportGuard() {
        return teleportGuard;
    }

    /**
     * Installs the build override. Called by the {@code staff/} module at startup,
     * so that its build toggle lifts protection for the staff member who turned it
     * on; until then {@link BuildOverride#NONE} changes nothing.
     */
    public void setBuildOverride(BuildOverride buildOverride) {
        this.buildOverride = Objects.requireNonNull(buildOverride, "buildOverride");
    }

    public BuildOverride getBuildOverride() {
        return buildOverride;
    }

    /**
     * Installs the break allowance. Called by {@code events/} at startup, so a
     * DTC or Last Break run under way can be broken through territory protection
     * for its own core; until then {@link BreakAllowance#NONE} changes nothing.
     */
    public void setBreakAllowance(BreakAllowance breakAllowance) {
        this.breakAllowance = Objects.requireNonNull(breakAllowance, "breakAllowance");
    }

    public BreakAllowance getBreakAllowance() {
        return breakAllowance;
    }

    /**
     * Whether this player builds through territory protection right now: they hold
     * {@code hcfcore.claim.bypass} <em>and</em> have the staff build toggle on.
     *
     * <p>Both, as {@link BuildOverride} describes: the permission says the rank may,
     * the toggle says the staff member is choosing to. Until 13/09/2026 either one
     * was enough, so an operator broke walls in normal play and a moderator's toggle
     * lifted protection without the permission - found while documenting the plugin,
     * against this seam's own javadoc and the limiter, which already asked for both.
     * The crowbar asks the same question.
     */
    public boolean bypassesProtection(Player player) {
        return player.hasPermission(ClaimProtectionListener.BYPASS_PERMISSION)
                && buildOverride.allows(player.getUniqueId());
    }

    /**
     * @param dataSource          pool to persist through, or {@code null} to run in memory
     * @param saveIntervalSeconds how often to flush; {@code 0} leaves only the shutdown save
     */
    public void enable(DataSource dataSource, long saveIntervalSeconds) {
        reloadSettings();

        ClaimStore store = dataSource == null
                ? ClaimStore.NO_OP
                : new JdbcClaimStore(dataSource, message -> plugin.getLogger().info(message));

        this.manager = new ClaimManager(() -> settings, teams.getManager(), store);

        // Schema and initial load are blocking I/O: off the main thread
        // (CONTRIBUTING.md section 5). Nothing may claim or protect before it lands
        // (StartupGate).
        StartupBarrier.Load load = startup.expect("claims");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int converted = manager.loadAll();
                if (converted > 0) {
                    plugin.getLogger().info("Converted " + converted + " chunk claims from an earlier version into "
                            + manager.getTotalClaimCount() + " block claims.");
                }
                plugin.getLogger().info("Loaded " + manager.getTotalClaimCount() + " claims.");
                load.succeeded();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load claims.", e);
                load.failed();
            }
        });

        if (saveIntervalSeconds > 0) {
            long ticks = saveIntervalSeconds * 20L;
            this.saveTask = Bukkit.getScheduler()
                    .runTaskTimerAsynchronously(plugin, this::flushQuietly, ticks, ticks);
        }

        plugin.getServer().getPluginManager()
                .registerEvents(new ClaimProtectionListener(this), plugin);
        plugin.getServer().getPluginManager()
                .registerEvents(new CrossBorderListener(this), plugin);
        plugin.getServer().getPluginManager()
                .registerEvents(new TeamDisbandClaimListener(this), plugin);
        plugin.getServer().getPluginManager()
                .registerEvents(new com.lawkeys.hcfcore.claim.wand.WandListener(wandSessions), plugin);
        plugin.getServer().getPluginManager()
                .registerEvents(new com.lawkeys.hcfcore.claim.listener.ClaimLockListener(this), plugin);

        plugin.getServer().getPluginManager().registerEvents(
                new com.lawkeys.hcfcore.claim.subclaim.SubclaimListener(this, () -> subclaims), plugin);
        plugin.getServer().getPluginManager()
                .registerEvents(new com.lawkeys.hcfcore.claim.view.ViewListener(pillarView, wallView, claimWalls), plugin);
        claimWalls.start();

        for (TeamSubCommand subCommand : ClaimSubCommands.all(this)) {
            teams.registerSubCommand(subCommand);
        }
        teams.setSystemLandTool((player, team) -> wandSessions.give(player,
                new com.lawkeys.hcfcore.claim.wand.TeamClaimTask(this, team.getId(), true)));
    }

    /** {@code claims.yml}, {@code subclaims}. */
    private volatile com.lawkeys.hcfcore.claim.subclaim.Subclaim.Rules subclaims =
            com.lawkeys.hcfcore.claim.subclaim.Subclaim.Rules.defaults();

    /** Re-reads {@code claims.yml}; the running manager picks the new values up immediately. */
    public void reloadSettings() {
        var file = ConfigManager.loadFile(plugin, "claims.yml");
        this.settings = ClaimSettingsLoader.load(file,
                warning -> plugin.getLogger().warning("claims.yml: " + warning));
        this.subclaims = loadSubclaims(file == null ? null : file.getConfigurationSection("subclaims"));
        if (manager != null) {
            // The wall may have been switched off, or its rate changed: redraw from
            // nothing rather than leave a wall nobody can see the settings behind.
            claimWalls.start();
        }
    }

    private com.lawkeys.hcfcore.claim.subclaim.Subclaim.Rules loadSubclaims(
            org.bukkit.configuration.ConfigurationSection section) {
        var d = com.lawkeys.hcfcore.claim.subclaim.Subclaim.Rules.defaults();
        if (section == null) {
            return d;
        }
        String openAny = section.getString("open-any", "co-leader");
        com.lawkeys.hcfcore.team.TeamRole role = "none".equalsIgnoreCase(openAny == null ? "" : openAny.trim())
                ? null
                : com.lawkeys.hcfcore.team.TeamRole.fromId(openAny).orElseGet(() -> {
                    plugin.getLogger().warning("claims.yml: subclaims.open-any '" + openAny
                            + "' is not leader, co-leader, member or none; co-leader is used.");
                    return com.lawkeys.hcfcore.team.TeamRole.CO_LEADER;
                });
        String header = section.getString("header", d.header());
        return new com.lawkeys.hcfcore.claim.subclaim.Subclaim.Rules(section.getBoolean("enabled", d.enabled()),
                header == null || header.isBlank() ? d.header() : header.trim(), role);
    }

    /** Stops the periodic save and writes what is still pending, synchronously. */
    public void disable() {
        claimWalls.stop();
        mapPillars.stop();
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (manager == null) {
            return;
        }
        try {
            int written = manager.flush();
            if (written > 0) {
                plugin.getLogger().info("Saved territory for " + written + " teams on shutdown.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save claims on shutdown", e);
        }
    }

    private void flushQuietly() {
        try {
            manager.flush();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Periodic claim save failed; the affected teams stay queued for the next attempt.", e);
        }
    }

    // ------------------------------------------------------------------
    // Moving somebody to the nearest free land
    // ------------------------------------------------------------------

    /** How far out the nearest free land is looked for, in chunks. */
    public static final int FREE_LAND_RADIUS = 16;

    /**
     * @return the nearest chunk whose middle this player may stand in - wilderness, or
     *         their own team's land - searched in rings around {@code from}; empty when
     *         there is none within {@link #FREE_LAND_RADIUS}. {@code from} itself when
     *         they may already stand there. The middle is where {@link #moveTo} lands
     *         them, so it is the block judged, claims being block-precise
     */
    public Optional<ChunkPosition> nearestFreeLand(ChunkPosition from, UUID playerTeamId) {
        ClaimManager claims = manager;
        return StuckSearch.nearest(from, FREE_LAND_RADIUS, chunk -> {
            int x = chunk.minBlockX() + 8;
            int z = chunk.minBlockZ() + 8;
            Optional<Team> owner = claims.getOwner(chunk.world(), x, z);
            return owner.isEmpty()
                    ? !claims.isWarzone(chunk.world(), x, z)
                    : owner.get().getId().equals(playerTeamId);
        });
    }

    /**
     * Moves a player to the middle of a chunk, on its highest block.
     *
     * <p>The height is read once the chunk has loaded asynchronously -
     * {@code getHighestBlockYAt} on an unloaded chunk would load it on the main
     * thread (CONTRIBUTING.md section 5). {@code World#getChunkAtAsync} runs its callback on
     * the main thread (26.2 javadoc); an exception in it would be swallowed by the
     * future, so it is caught and logged.
     *
     * @param arrived run once they have been sent on their way
     */
    public void moveTo(Player player, ChunkPosition destination, Runnable arrived) {
        World world = Bukkit.getWorld(destination.world());
        if (world == null) {
            return;
        }
        int x = destination.x() * 16 + 8;
        int z = destination.z() * 16 + 8;
        world.getChunkAtAsync(destination.x(), destination.z()).whenComplete((chunk, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Could not load the chunk to move " + player.getName() + " to.",
                        error);
                return;
            }
            if (!player.isOnline()) {
                return;
            }
            int y = world.getHighestBlockYAt(x, z) + 1;
            player.teleportAsync(new Location(world, x + 0.5, y, z + 0.5, player.getYaw(), player.getPitch()));
            arrived.run();
        });
    }

    // ------------------------------------------------------------------
    // Bukkit <-> pure-model conversions
    // ------------------------------------------------------------------

    /**
     * @return the team owning the block column at {@code location}, or empty for
     *         unclaimed land or while claims are still loading
     */
    public Optional<Team> ownerAt(Location location) {
        ClaimManager claims = manager;
        if (claims == null || location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        return claims.getOwner(location.getWorld().getName(), location.getBlockX(), location.getBlockZ());
    }

    /** @return whether {@code location} is unclaimed warzone land */
    public boolean isWarzoneAt(Location location) {
        ClaimManager claims = manager;
        return claims != null && location != null && location.getWorld() != null
                && claims.isWarzone(location.getWorld().getName(), location.getBlockX(), location.getBlockZ());
    }

    public static ChunkPosition toChunk(Location location) {
        return ChunkPosition.fromBlock(location.getWorld().getName(),
                location.getBlockX(), location.getBlockZ());
    }

    public static WorldPosition toPosition(Location location) {
        return new WorldPosition(location.getWorld().getName(), location.getX(), location.getY(),
                location.getZ(), location.getYaw(), location.getPitch());
    }

    /** @return the Bukkit location, or empty if the world is no longer loaded. */
    public static Optional<Location> toLocation(WorldPosition position) {
        World world = Bukkit.getWorld(position.world());
        return world == null
                ? Optional.empty()
                : Optional.of(new Location(world, position.x(), position.y(), position.z(),
                        position.yaw(), position.pitch()));
    }
}
