package com.lawkeys.hcfcore.team;

import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.database.dao.JdbcTeamStore;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.startup.StartupBarrier;
import com.lawkeys.hcfcore.startup.StartupGate;
import com.lawkeys.hcfcore.team.command.TeamCommand;
import com.lawkeys.hcfcore.team.command.TeamSubCommand;
import com.lawkeys.hcfcore.util.WorldPosition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Wires the team module into the server: builds the manager, loads it from the
 * database off the main thread, schedules the periodic async save, registers the
 * command, and flushes on shutdown.
 *
 * <p>Everything server-shaped about the module lives here or in
 * {@code team/command}; the rules themselves are in {@link TeamManager}.
 */
public final class TeamModule {

    private final Plugin plugin;
    private final LangManager lang;
    private final StartupGate startup;

    private volatile TeamSettings settings = TeamSettings.defaults();
    /** How many strikes still count against a team: installed by the staff module; none until then. */
    private volatile java.util.function.ToIntFunction<java.util.UUID> strikeCount = teamId -> 0;
    /**
     * Hands a staff member the tool to draw a new server team's land: installed by the
     * claim module, whose claiming wand it is; nothing until then.
     */
    private volatile java.util.function.BiConsumer<org.bukkit.entity.Player, Team> systemLandTool = (player, team) -> {
    };
    private TeamManager manager;
    private TeamCommand teamCommand;
    private BukkitTask saveTask;

    public TeamModule(Plugin plugin, LangManager lang, StartupGate startup) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lang = Objects.requireNonNull(lang, "lang");
        this.startup = Objects.requireNonNull(startup, "startup");
    }

    public Plugin getPlugin() {
        return plugin;
    }

    /** @return how {@code lang/en.yml} writes this role - "Co-leader", not "CO_LEADER" */
    public String roleName(TeamRole role) {
        return lang.get("team.role." + role.name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    /**
     * @return a result's placeholders with its roles ({@code %role%},
     *         {@code %required%}) readable: {@link TeamManager} names them by their
     *         constant, having no language file, and a player was told "Promoted to
     *         CO_LEADER" (found in game, 13/09/2026)
     */
    public Map<String, String> readable(Map<String, String> placeholders) {
        Map<String, String> out = new LinkedHashMap<>(placeholders);
        for (String key : List.of("role", "required")) {
            String value = out.get(key);
            if (value != null) {
                TeamRole.fromId(value).ifPresent(role -> out.put(key, roleName(role)));
            }
        }
        return out;
    }

    public TeamManager getManager() {
        return manager;
    }

    /**
     * Installs how many strikes count against a team, for {@code /team show}. Called by
     * the {@code staff/} module, which keeps the strikes.
     */
    public void setStrikeCounter(java.util.function.ToIntFunction<java.util.UUID> counter) {
        this.strikeCount = java.util.Objects.requireNonNull(counter, "counter");
    }

    /** Installs what {@code /team createsystem} hands its sender. Called by the {@code claim/} module. */
    public void setSystemLandTool(java.util.function.BiConsumer<org.bukkit.entity.Player, Team> tool) {
        this.systemLandTool = java.util.Objects.requireNonNull(tool, "tool");
    }

    /** Hands {@code player} the tool to draw {@code team}'s land, if a module installed one. */
    public void giveSystemLandTool(org.bukkit.entity.Player player, Team team) {
        systemLandTool.accept(player, team);
    }

    /** @return how many strikes still count against this team, {@code 0} without the staff module */
    public int activeStrikes(java.util.UUID teamId) {
        return strikeCount.applyAsInt(teamId);
    }

    public LangManager getLang() {
        return lang;
    }

    public StartupGate getStartup() {
        return startup;
    }

    public TeamSettings getSettings() {
        return settings;
    }

    /**
     * Starts the module.
     *
     * @param dataSource         pool to persist through, or {@code null} to run
     *                           entirely in memory (no database configured)
     * @param saveIntervalSeconds how often to flush dirty teams; {@code 0} disables
     *                            the periodic save, leaving only the shutdown flush
     */
    public void enable(DataSource dataSource, long saveIntervalSeconds) {
        reloadSettings();

        TeamStore store = dataSource == null
                ? TeamStore.NO_OP
                : new JdbcTeamStore(dataSource, message -> plugin.getLogger().info(message));

        this.manager = new TeamManager(() -> settings, store,
                new BukkitTeamEventDispatcher(plugin.getServer().getPluginManager()));

        // Schema creation and the initial load are blocking I/O, so they run off
        // the main thread (CONTRIBUTING.md section 5). Commands are registered up front,
        // but the load starts by clearing the cache, so nothing may change it
        // before the load lands: players cannot join and /team refuses to run
        // until every module has loaded (StartupGate).
        StartupBarrier.Load load = startup.expect("teams");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                manager.loadAll();
                plugin.getLogger().info("Loaded " + manager.getTeamCount() + " teams.");
                load.succeeded();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load teams.", e);
                load.failed();
            }
        });

        if (saveIntervalSeconds > 0) {
            long ticks = saveIntervalSeconds * 20L;
            this.saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushQuietly, ticks, ticks);
        }

        registerCommand();
    }

    private void registerCommand() {
        PluginCommand command = plugin.getServer().getPluginCommand("team");
        if (command == null) {
            plugin.getLogger().severe("The 'team' command is missing from plugin.yml; "
                    + "no team command will be available.");
            return;
        }
        plugin.getServer().getPluginManager()
                .registerEvents(new com.lawkeys.hcfcore.team.listener.TeamPointsListener(this), plugin);
        this.teamCommand = new TeamCommand(this);
        command.setExecutor(teamCommand);
        command.setTabCompleter(teamCommand);
    }

    /**
     * Adds a {@code /team} subcommand owned by another module.
     *
     * <p>This is how {@code claim/} attaches {@code /team claim}, {@code /team map}
     * and friends without the team module depending on territory. Call it after
     * this module is enabled; a no-op with a warning if {@code /team} is missing
     * from plugin.yml.
     */
    public void registerSubCommand(TeamSubCommand subCommand) {
        if (teamCommand == null) {
            plugin.getLogger().warning("Cannot register the '" + subCommand.getName()
                    + "' subcommand: the team command is not available.");
            return;
        }
        teamCommand.register(subCommand);
    }

    /** Re-reads {@code teams.yml}. Every subsequent manager decision uses the new values. */
    public void reloadSettings() {
        this.settings = TeamSettingsLoader.load(
                ConfigManager.loadFile(plugin, "teams.yml"),
                warning -> plugin.getLogger().warning("teams.yml: " + warning));
    }

    /** Stops the periodic save and writes everything still pending, synchronously. */
    public void disable() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (manager == null) {
            return;
        }
        // Deliberately synchronous: the scheduler no longer runs tasks at this
        // point, so this is the last chance to persist (ARCHITECTURE.md section 3).
        try {
            int written = manager.flush();
            if (written > 0) {
                plugin.getLogger().info("Saved " + written + " teams on shutdown.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save teams on shutdown", e);
        }
    }

    private void flushQuietly() {
        try {
            manager.flush();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Periodic team save failed; the affected teams stay queued for the next attempt.", e);
        }
    }

    // ------------------------------------------------------------------
    // Helpers shared by the command layer
    // ------------------------------------------------------------------

    /** @return the online members of {@code team} */
    public List<Player> getOnlineMembers(Team team) {
        List<Player> online = new ArrayList<>();
        for (UUID member : team.getMemberIds()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                online.add(player);
            }
        }
        return online;
    }

    /** Sends a language key to every online member, optionally skipping one player. */
    public void broadcast(Team team, UUID except, String key, String... placeholders) {
        broadcastTo(getOnlineMembers(team), except, key, placeholders);
    }

    /**
     * Sends a language key to a previously captured audience.
     *
     * <p>Needed for operations that empty a team - a disband announced after the
     * fact would reach nobody, and announcing it before would lie if the operation
     * turned out to be refused. Capture the members first, act, then announce.
     */
    public void broadcastTo(List<Player> recipients, UUID except, String key, String... placeholders) {
        String message = lang.get(key, placeholders);
        if (message.isEmpty()) {
            return;
        }
        for (Player player : recipients) {
            if (!player.getUniqueId().equals(except)) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * Finds a member of {@code team} by name.
     *
     * <p>Resolved from the team's own membership rather than through a global
     * name lookup, so kicking or promoting an offline member never blocks the main
     * thread on a web request.
     */
    public Optional<UUID> findMemberByName(Team team, String name) {
        for (UUID member : team.getMemberIds()) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(member);
            String memberName = offline.getName();
            if (memberName != null && memberName.equalsIgnoreCase(name)) {
                return Optional.of(member);
            }
        }
        return Optional.empty();
    }

    /** @return a display name for {@code player}, falling back to the uuid for an unknown player */
    public String nameOf(UUID player) {
        if (player == null) {
            return "console";
        }
        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            return online.getName();
        }
        String name = Bukkit.getOfflinePlayer(player).getName();
        return name != null ? name : player.toString();
    }

    /** @return the names of {@code members}, comma-separated, or the "none" message when empty */
    public String describeMembers(Iterable<UUID> members) {
        List<String> names = new ArrayList<>();
        for (UUID member : members) {
            names.add(nameOf(member));
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names.isEmpty() ? lang.get(TeamMessages.INFO_NONE) : String.join(", ", names);
    }

    public static WorldPosition toPosition(Location location) {
        return new WorldPosition(location.getWorld().getName(), location.getX(), location.getY(),
                location.getZ(), location.getYaw(), location.getPitch());
    }

    /** @return the Bukkit location, or empty if the world is not loaded any more */
    public static Optional<Location> toLocation(WorldPosition position) {
        World world = Bukkit.getWorld(position.world());
        return world == null
                ? Optional.empty()
                : Optional.of(new Location(world, position.x(), position.y(), position.z(),
                        position.yaw(), position.pitch()));
    }

    /** Points every online member's compass at the team's rally point. */
    public void updateCompasses(Team team) {
        Optional<WorldPosition> rally = manager.getRally(team);
        if (rally.isEmpty()) {
            return;
        }
        Optional<Location> location = toLocation(rally.get());
        if (location.isEmpty()) {
            plugin.getLogger().warning("Rally for team " + team.getName()
                    + " points at unloaded world '" + rally.get().world() + "'.");
            return;
        }
        for (Player player : getOnlineMembers(team)) {
            player.setCompassTarget(location.get());
        }
    }

}
