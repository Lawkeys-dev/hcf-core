package com.lawkeys.hcfcore.events;

import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.events.command.EventsCommand;
import com.lawkeys.hcfcore.events.conquest.ConquestController;
import com.lawkeys.hcfcore.events.conquest.ConquestDefinition;
import com.lawkeys.hcfcore.events.conquest.ConquestRun;
import com.lawkeys.hcfcore.events.conquest.ConquestSettings;
import com.lawkeys.hcfcore.events.conquest.ConquestSettingsLoader;
import com.lawkeys.hcfcore.events.conquest.ConquestZone;
import com.lawkeys.hcfcore.events.king.KingEventController;
import com.lawkeys.hcfcore.events.king.KingSettingsLoader;
import com.lawkeys.hcfcore.events.listener.CitadelListener;
import com.lawkeys.hcfcore.hologram.HologramSource;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.startup.StartupGate;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.team.TeamResult;
import com.lawkeys.hcfcore.util.ColorCodes;
import com.lawkeys.hcfcore.util.Cuboid;
import com.lawkeys.hcfcore.util.Durations;
import com.lawkeys.hcfcore.util.RewardCommands;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import javax.sql.DataSource;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Wires the capture events into the server.
 *
 * <p>This class is the whole server-facing half of the module: it samples who is
 * standing in each zone, hands that to the rule engine, and renders whatever the
 * engine reports. No capture rule lives here (CONTRIBUTING.md section 3).
 *
 * <p><strong>Nothing is persisted, deliberately.</strong> A KOTH in progress is a
 * few minutes of state; carrying it across a restart would mean restoring a
 * countdown for players who are no longer in the zone. A restart cancels the run,
 * exactly as it cancels a combat tag in {@code pvp/}. What does outlive a restart
 * is the score, and that already lives on the team.
 */
public final class EventModule {

    /** Staff may open and close events by hand. Declared in plugin.yml. */
    public static final String ADMIN_PERMISSION = "hcfcore.events.admin";

    private final Plugin plugin;
    private final TeamModule teams;
    private final ClaimModule claims;
    private final LangManager lang;
    private final StartupGate startup;

    private volatile EventSettings settings = EventSettings.defaults();
    /** {@code zone-holograms} in events.yml. */
    private volatile boolean zoneHolograms = true;
    private volatile double zoneHologramHeight = 3.0;
    private static final DateTimeFormatter HOLOGRAM_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private EventManager manager;
    private KingEventController king;
    private ConquestController conquest;
    private BukkitTask tickTask;

    /**
     * Other modules with something scheduled to show in {@code /events}.
     *
     * <p>Copy-on-write: it is read on every {@code /events} and written once per
     * module at startup.
     */
    private final List<AgendaContributor> agendaContributors = new CopyOnWriteArrayList<>();
    /** Recognises a partner item, refused inside a Citadel. Installed by {@code kit/}; until then, none is. */
    private volatile Predicate<ItemStack> partnerItems = item -> false;

    /** @param claims may be {@code null} if the claim module is not running; Kill the King then cannot start */
    public EventModule(Plugin plugin, TeamModule teams, ClaimModule claims, LangManager lang, StartupGate startup) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.claims = claims;
        this.lang = Objects.requireNonNull(lang, "lang");
        this.startup = Objects.requireNonNull(startup, "startup");
    }

    public EventManager getManager() {
        return manager;
    }

    /** @return Kill the King, which shares {@code /events} and {@code events.yml} with the captures */
    public KingEventController getKing() {
        return king;
    }

    public StartupGate getStartup() {
        return startup;
    }

    public EventSettings getSettings() {
        return settings;
    }

    public LangManager getLang() {
        return lang;
    }

    public TeamModule getTeams() {
        return teams;
    }

    /**
     * Registers a module that has its own scheduled things to list in
     * {@code /events}. See {@link AgendaContributor}.
     */
    public void addAgendaContributor(AgendaContributor contributor) {
        agendaContributors.add(Objects.requireNonNull(contributor, "contributor"));
    }

    /** @return the lines contributed by other modules, in registration order */
    public List<AgendaEntry> collectAgenda() {
        List<AgendaEntry> entries = new ArrayList<>();
        for (AgendaContributor contributor : agendaContributors) {
            try {
                entries.addAll(contributor.agendaEntries());
            } catch (Exception e) {
                // One module with a broken agenda must not take /events down with it:
                // the command's own half still has something useful to say.
                plugin.getLogger().log(Level.WARNING, "An agenda contributor failed", e);
            }
        }
        return entries;
    }

    /** @return Conquest, which shares {@code /events} and {@code events.yml} with the captures */
    public ConquestController getConquest() {
        return conquest;
    }

    /**
     * Answers {@code pvp/}'s {@code AllyCombatZone}: the "event areas" where the
     * owner lets allies fight (13/09/2026) - the zone of a running KOTH or Citadel,
     * a zone of the running Conquest, and the King of a running Kill the King,
     * wherever they stand. Mountains are not among them: nobody captures a node.
     */
    public boolean coversAllyCombat(UUID playerId, String world, double x, double y, double z) {
        for (RunningEvent run : manager.getActiveEvents()) {
            if (run.getDefinition().zone().contains(world, x, y, z)) {
                return true;
            }
        }
        if (conquest != null) {
            Optional<ConquestRun> run = conquest.getManager().getCurrent();
            if (run.isPresent()) {
                for (ConquestRun.ZoneState state : run.get().zones()) {
                    if (state.zone().area().contains(world, x, y, z)) {
                        return true;
                    }
                }
            }
        }
        return king != null && king.getManager().isKing(playerId);
    }

    /**
     * @return whether this location is in the zone of a running event: a KOTH or
     *         Citadel zone, or a zone of the running Conquest - where partner items
     *         may be refused ({@code abilities.yml}, {@code disabled-in.events})
     */
    public boolean inEventZone(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        String world = location.getWorld().getName();
        for (RunningEvent run : manager.getActiveEvents()) {
            if (run.getDefinition().zone().contains(world, location.getX(), location.getY(), location.getZ())) {
                return true;
            }
        }
        if (conquest != null) {
            Optional<ConquestRun> run = conquest.getManager().getCurrent();
            if (run.isPresent()) {
                for (ConquestRun.ZoneState state : run.get().zones()) {
                    if (state.zone().area().contains(world, location.getX(), location.getY(), location.getZ())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** @param dataSource pool Kill the King keeps its stashes in, or {@code null} to keep them in memory */
    public void enable(DataSource dataSource) {
        this.king = new KingEventController(plugin, teams, claims, lang, startup);
        this.conquest = new ConquestController(plugin, this, teams);
        reloadSettings();
        this.manager = new EventManager(() -> settings);
        king.enable(dataSource);
        conquest.enable(settings.tickSeconds());

        registerCommand("events", new EventsCommand(this));
        plugin.getServer().getPluginManager().registerEvents(new CitadelListener(this), plugin);

        if (settings.definitions().isEmpty()) {
            plugin.getLogger().info("No capture events are configured; see events.yml.");
        } else {
            plugin.getLogger().info("Loaded " + settings.definitions().size() + " capture event(s).");
        }
        int kingEvents = king.getSettings().definitions().size();
        if (kingEvents > 0) {
            plugin.getLogger().info("Loaded " + kingEvents + " Kill the King event(s).");
        }
        int conquests = conquest.getSettings().definitions().size();
        if (conquests > 0) {
            plugin.getLogger().info("Loaded " + conquests + " Conquest(s).");
        }

        scheduleTick();
    }

    private void scheduleTick() {
        long ticks = Math.max(1L, settings.tickSeconds()) * 20L;
        // Main thread: this reads player locations, sends messages and dispatches
        // reward commands, none of which is safe off it.
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, ticks, ticks);
    }

    private void tick() {
        try {
            List<EventUpdate> updates = manager.tick(collectOccupants());
            for (EventUpdate update : updates) {
                handle(update);
            }
        } catch (Exception e) {
            // One bad tick must not kill the repeating task and silently stop every
            // event on the server for the rest of the session.
            plugin.getLogger().log(Level.SEVERE, "An event tick failed", e);
        }
    }

    /** @return for each running event, who is standing in its zone right now */
    private Map<String, List<Occupant>> collectOccupants() {
        List<RunningEvent> running = List.copyOf(manager.getActiveEvents());
        if (running.isEmpty()) {
            return Map.of();
        }
        Map<String, List<Occupant>> occupants = new HashMap<>();
        for (RunningEvent event : running) {
            occupants.put(event.getDefinition().id(), new ArrayList<>());
        }
        // One pass over the online players rather than one pass per event: the outer
        // loop is the expensive one on a full server.
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location location = player.getLocation();
            if (location == null || location.getWorld() == null) {
                continue;
            }
            String world = location.getWorld().getName();
            Occupant occupant = null;
            for (RunningEvent event : running) {
                Cuboid zone = event.getDefinition().zone();
                if (!zone.contains(world, location.getX(), location.getY(), location.getZ())) {
                    continue;
                }
                if (occupant == null) {
                    UUID teamId = teams.getManager().getTeamOf(player.getUniqueId())
                            .map(Team::getId).orElse(null);
                    occupant = Occupant.of(player.getUniqueId(), teamId);
                }
                occupants.get(event.getDefinition().id()).add(occupant);
            }
        }
        return occupants;
    }

    /**
     * Installs how a partner item is recognised. Called by the {@code kit/} module's
     * wiring at startup, since partner items are kit abilities; this module does not
     * need to know what one is (ARCHITECTURE.md section 14).
     */
    public void setPartnerItems(Predicate<ItemStack> partnerItems) {
        this.partnerItems = Objects.requireNonNull(partnerItems, "partnerItems");
    }

    public boolean isPartnerItem(ItemStack item) {
        return item != null && partnerItems.test(item);
    }

    /**
     * @return the Citadel whose claim this location stands on: the land of the server
     *         team a {@code citadels:} entry names. Its rules hold at all times, whether
     *         the event runs or not
     */
    public Optional<CitadelDefinition> citadelAt(Location location) {
        EventSettings current = settings;
        if (!current.enabled() || current.citadels().isEmpty() || location == null || location.getWorld() == null
                || claims == null || claims.getManager() == null) {
            return Optional.empty();
        }
        return claims.getManager().getOwner(ClaimModule.toChunk(location))
                .filter(team -> team.getType().isSystem())
                .flatMap(team -> current.citadelClaimedBy(team.getName()));
    }

    /**
     * Says in the console when a Citadel starts without its claim: the zone can be
     * held, but nothing is refused around it. Checked here rather than at load, since
     * teams and claims load after the settings.
     */
    private void checkCitadelClaim(String eventId) {
        Optional<CitadelDefinition> citadel = settings.citadel(eventId);
        Optional<CaptureEventDefinition> capture = settings.find(eventId);
        if (citadel.isEmpty() || capture.isEmpty() || claims == null || claims.getManager() == null) {
            return;
        }
        Optional<Team> owner = teams.getManager().getTeamByName(citadel.get().claim())
                .filter(team -> team.getType().isSystem());
        if (owner.isEmpty()) {
            plugin.getLogger().warning("Citadel '" + eventId + "' names the claim '" + citadel.get().claim()
                    + "', but no server team has that name: nothing is refused around the zone. Create it with "
                    + "/team createsystem " + citadel.get().claim() + " combat, then /team forceclaim.");
            return;
        }
        Cuboid zone = capture.get().zone();
        Location centre = new Location(Bukkit.getWorld(zone.world()), (zone.minX() + zone.maxX()) / 2.0,
                zone.minY(), (zone.minZ() + zone.maxZ()) / 2.0);
        if (centre.getWorld() == null || citadelAt(centre).isEmpty()) {
            plugin.getLogger().warning("Citadel '" + eventId + "': its zone to hold is not on the land of '"
                    + citadel.get().claim() + "'. Claim the Citadel around the zone with /team forceclaim.");
        }
    }

    /** Renders an update, and applies the rewards when somebody actually won. */
    private void handle(EventUpdate update) {
        if (update.type() == EventUpdate.Type.STARTED) {
            checkCitadelClaim(update.eventId());
        }
        Map<String, String> placeholders = new LinkedHashMap<>(update.placeholders());
        Optional<Team> team = Optional.ofNullable(update.teamId())
                .flatMap(id -> teams.getManager().getTeam(id));
        placeholders.put("team", team.map(Team::getName).orElse(""));

        broadcast(update.messageKey(), placeholders);

        if (update.type() == EventUpdate.Type.CAPTURED) {
            team.ifPresent(winner -> award(winner, update, placeholders));
        }
    }

    /**
     * Credits a capture and runs its reward commands.
     *
     * <p>The score itself is the team module's business: {@code recordKothCapture}
     * already owns the cap and the points-per-capture scale, so this module counts
     * nothing of its own.
     */
    private void award(Team winner, EventUpdate update, Map<String, String> placeholders) {
        TeamResult result = teams.getManager().recordKothCapture(winner);
        // A capped team still captured the event in-game; it simply stops counting
        // towards the ranking. Telling the team why is better than silence.
        teams.broadcast(winner, null, result.getMessageKey(), flatten(result.getPlaceholders()));

        String eventName = placeholders.getOrDefault("event", update.eventId());
        settings.find(update.eventId()).ifPresent(definition -> RewardCommands.run(
                definition.rewardCommands(),
                Map.of("team", winner.getName(), "event", eventName),
                command -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command),
                (command, e) -> plugin.getLogger().log(Level.WARNING,
                        "Reward command failed for event " + update.eventId() + ": " + command, e)));
    }

    /** @return a placeholder map as the alternating key/value array the team module takes */
    private static String[] flatten(Map<String, String> placeholders) {
        String[] flat = new String[placeholders.size() * 2];
        int index = 0;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            flat[index++] = entry.getKey();
            flat[index++] = entry.getValue();
        }
        return flat;
    }

    /** Sends a language key to everybody online, and mirrors it to the console log. */
    public void broadcast(String key, Map<String, String> placeholders) {
        String message = lang.get(key, placeholders);
        if (message.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
        plugin.getLogger().info(ColorCodes.strip(message));
    }

    private void registerCommand(String name, TabExecutor executor) {
        PluginCommand command = plugin.getServer().getPluginCommand(name);
        if (command == null) {
            plugin.getLogger().severe("The '" + name + "' command is missing from plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    public void reloadSettings() {
        ConfigurationSection file = ConfigManager.loadFile(plugin, "events.yml");
        Consumer<String> warn = warning -> plugin.getLogger().warning("events.yml: " + warning);
        this.settings = EventSettingsLoader.load(file, warn);
        ConfigurationSection holograms = file == null ? null : file.getConfigurationSection("zone-holograms");
        this.zoneHolograms = holograms == null || holograms.getBoolean("enabled", true);
        this.zoneHologramHeight = holograms == null ? 3.0 : holograms.getDouble("height", 3.0);
        if (king != null) {
            king.applySettings(KingSettingsLoader.load(file, settings, warn));
        }
        if (conquest != null && king != null) {
            conquest.applySettings(ConquestSettingsLoader.load(
                    file, settings, king.getSettings(), warn), settings.tickSeconds());
        }
        if (manager != null) {
            // A reload may have changed the schedule or the times themselves; forget
            // the window so an event whose hour just passed is not fired retroactively.
            manager.resetScheduleWindow();
            // An event deleted from the config while it was running would otherwise
            // keep ticking against a definition nobody can see any more.
            for (RunningEvent running : manager.getActiveEvents()) {
                String id = running.getDefinition().id();
                if (settings.find(id).isEmpty()) {
                    manager.stop(id);
                    plugin.getLogger().info("Stopped '" + id
                            + "': it was removed from events.yml while running.");
                }
            }
            if (tickTask != null) {
                tickTask.cancel();
                scheduleTick();
            }
        }
    }

    // ------------------------------------------------------------------
    // Zone holograms
    // ------------------------------------------------------------------

    /**
     * A hologram above every capture zone - KOTH, Citadel and each Conquest zone - as
     * the project owner chose on 12/09/2026: always there, reading the time left and
     * who holds the zone while its event runs, and when it runs next otherwise.
     * Registered with {@code hologram/} as a {@link HologramSource}; KTK has no zone of
     * its own and so no hologram.
     */
    public List<HologramSource.Placed> zoneHolograms() {
        if (!zoneHolograms || manager == null) {
            return List.of();
        }
        List<HologramSource.Placed> placed = new ArrayList<>();
        EventSettings current = settings;
        if (current.enabled()) {
            for (CaptureEventDefinition definition : current.definitions()) {
                List<String> lines = new ArrayList<>();
                lines.add(lang.get(EventMessages.HOLOGRAM_TITLE, "event", definition.displayName()));
                Optional<RunningEvent> running = manager.getActiveEvent(definition.id());
                if (running.isPresent()) {
                    status(lines, running.get().getRemainingSeconds(), running.get().getHolderTeamId(),
                            running.get().isContested());
                } else {
                    next(lines, manager.getNextOccurrence(definition));
                }
                placed.add(above("zone:" + definition.id(), definition.zone(), lines));
            }
        }
        if (conquest != null) {
            ConquestSettings conquests = conquest.getSettings();
            Optional<ConquestRun> run = conquest.getManager().getCurrent();
            for (ConquestDefinition definition : conquests.enabled() ? conquests.definitions()
                    : List.<ConquestDefinition>of()) {
                Optional<ConquestRun> thisOne = run.filter(r -> r.getDefinition().id().equals(definition.id()));
                for (ConquestZone zone : definition.zones()) {
                    List<String> lines = new ArrayList<>();
                    lines.add(lang.get(EventMessages.HOLOGRAM_CONQUEST_TITLE, "event", definition.displayName(),
                            "zone", zone.displayName()));
                    Optional<ConquestRun.ZoneState> state = thisOne.flatMap(r -> r.zones().stream()
                            .filter(s -> s.zone().id().equals(zone.id())).findFirst());
                    if (state.isPresent()) {
                        status(lines, state.get().remainingSeconds(), state.get().holder(), state.get().isContested());
                    } else {
                        next(lines, conquest.getManager().getNextOccurrence(definition));
                    }
                    placed.add(above("zone:" + definition.id() + ":" + zone.id(), zone.area(), lines));
                }
            }
        }
        return placed;
    }

    private void status(List<String> lines, long secondsLeft, UUID holder, boolean contested) {
        lines.add(lang.get(EventMessages.HOLOGRAM_TIME_LEFT, "time", Durations.formatWithSeconds(secondsLeft)));
        if (contested) {
            lines.add(lang.get(EventMessages.HOLOGRAM_CONTESTED));
        } else if (holder != null) {
            String name = teams.getManager() == null ? "?"
                    : teams.getManager().getTeam(holder).map(Team::getName).orElse("?");
            lines.add(lang.get(EventMessages.HOLOGRAM_HELD_BY, "team", name));
        } else {
            lines.add(lang.get(EventMessages.HOLOGRAM_FREE));
        }
    }

    private void next(List<String> lines, Optional<ZonedDateTime> next) {
        lines.add(next.map(time -> lang.get(EventMessages.HOLOGRAM_NEXT, "time", HOLOGRAM_TIME.format(time)))
                .orElseGet(() -> lang.get(EventMessages.HOLOGRAM_UNSCHEDULED)));
    }

    /** Centred over the zone, {@code height} blocks above its top. */
    private HologramSource.Placed above(String id, Cuboid zone, List<String> lines) {
        return new HologramSource.Placed(id, zone.world(),
                (zone.minX() + zone.maxX() + 1) / 2.0,
                zone.maxY() + 1 + zoneHologramHeight,
                (zone.minZ() + zone.maxZ() + 1) / 2.0, lines);
    }

    public void disable() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (king != null) {
            king.disable();
        }
        if (conquest != null) {
            conquest.disable();
        }
        if (manager != null) {
            manager.stopAll();
        }
    }
}
