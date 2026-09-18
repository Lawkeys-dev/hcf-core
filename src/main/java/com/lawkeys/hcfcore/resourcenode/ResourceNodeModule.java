package com.lawkeys.hcfcore.resourcenode;

import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.events.AgendaContributor;
import com.lawkeys.hcfcore.events.AgendaEntry;
import com.lawkeys.hcfcore.events.EventModule;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.resourcenode.command.ResourceNodeCommand;
import com.lawkeys.hcfcore.resourcenode.listener.ResourceNodeProtectionListener;
import com.lawkeys.hcfcore.util.ChunkPosition;
import com.lawkeys.hcfcore.util.ColorCodes;
import com.lawkeys.hcfcore.util.Cuboid;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Wires the resource nodes into the server - family B of ARCHITECTURE.md
 * section 9 (Mountain, Glowstone Mountain, Ore Mountain).
 *
 * <p>This class is the whole server-facing half of the module: it asks the rule
 * engine what is due, turns a refill into blocks in the world a batch at a time,
 * and renders whatever the engine says should be announced. No refill rule lives
 * here (CONTRIBUTING.md section 3).
 *
 * <p>It closes two seams declared by modules written before it:
 * <ul>
 *   <li>{@code claim.ReservedRegionPolicy}, so a node's region cannot be claimed;</li>
 *   <li>{@code events.AgendaContributor}, so refills appear in {@code /events}
 *       alongside the KOTHs, which is the single agenda FEATURES.md section 10
 *       asks for.</li>
 * </ul>
 * Both are optional: the module works on a server where either is absent.
 */
public final class ResourceNodeModule implements AgendaContributor {

    /** Staff may refill by hand. Declared in plugin.yml. */
    public static final String ADMIN_PERMISSION = "hcfcore.resourcenode.admin";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final Plugin plugin;
    private final ClaimModule claims;
    private final LangManager lang;

    private volatile ResourceNodeSettings settings = ResourceNodeSettings.defaults();
    private ResourceNodeManager manager;
    private BukkitTask tickTask;

    /**
     * Block names resolved to the server's own enum, once per reload.
     *
     * <p>Resolving on every placed block would be a map lookup per block for no
     * reason, and - more to the point - an unknown material would be reported tens
     * of thousands of times instead of once.
     */
    private volatile Map<String, Material> materials = Map.of();

    /**
     * Refills in progress, by node id. At most one per node.
     *
     * <p>Main thread only, and a plain map on purpose: every path that touches it -
     * the tick, the command, a reload, shutdown - is a Bukkit callback. Placing
     * blocks off the main thread is not allowed in the first place, so a refill has
     * nowhere else it could be driven from.
     */
    private final Map<String, RefillJob> running = new LinkedHashMap<>();

    /** Which running refills hold which chunks loaded; main thread only, like {@link #running}. */
    private final ChunkHolds chunkHolds = new ChunkHolds();

    public ResourceNodeModule(Plugin plugin, ClaimModule claims, LangManager lang) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.lang = Objects.requireNonNull(lang, "lang");
    }

    public ResourceNodeManager getManager() {
        return manager;
    }

    public ResourceNodeSettings getSettings() {
        return settings;
    }

    public LangManager getLang() {
        return lang;
    }

    /** @return whether a refill is currently running for that node */
    public boolean isRefilling(String nodeId) {
        return nodeId != null && running.containsKey(nodeId.toLowerCase(Locale.ROOT));
    }

    public void enable(EventModule events) {
        reloadSettings();
        this.manager = new ResourceNodeManager(() -> settings);

        registerCommand("resourcenode", new ResourceNodeCommand(this));
        plugin.getServer().getPluginManager()
                .registerEvents(new ResourceNodeProtectionListener(this), plugin);

        // The claim module was written first and cannot know what a mountain is, so
        // it asked the question instead (ARCHITECTURE.md section 14). This line is
        // what makes a node's region unclaimable.
        if (claims.getManager() != null) {
            claims.getManager().setReservedRegionPolicy(manager);
        }
        // Same idea for the agenda: /events belongs to the capture module, and a
        // refill is not a capture, so the line is contributed rather than owned.
        if (events != null) {
            events.addAgendaContributor(this);
        }

        if (settings.nodes().isEmpty()) {
            plugin.getLogger().info("No resource node is configured; see resourcenodes.yml.");
        } else {
            plugin.getLogger().info("Loaded " + settings.nodes().size() + " resource node(s).");
        }

        // Announced when done, like every other refill.
        for (ResourceNodeDefinition node : manager.getStartupFills()) {
            refillNow(node);
        }

        scheduleTick();
    }

    private void scheduleTick() {
        long ticks = Math.max(1L, settings.tickSeconds()) * 20L;
        // Main thread: this places blocks and sends messages, neither of which is
        // safe off it.
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, ticks, ticks);
    }

    private void tick() {
        try {
            for (NodeUpdate update : manager.tick()) {
                handle(update);
            }
            advanceRefills();
        } catch (Exception e) {
            // One bad tick must not kill the repeating task and stop every refill on
            // the server for the rest of the session.
            plugin.getLogger().log(Level.SEVERE, "A resource node tick failed", e);
        }
    }

    /**
     * Acts on one decision of the rule engine.
     *
     * <p>A refill is announced when it is <em>done</em>, not when it starts. It
     * spreads over many ticks - tens of seconds for a large mountain at the default
     * budget, plus the wait for its chunks - and "has just refilled" broadcast at
     * the start sends every team to a region that is still half empty. Nothing is
     * announced for a refill that could not start, or that was dropped before it
     * finished: a broadcast is worth exactly as much as the refill behind it.
     * Warnings, which are about the future, still go out at once.
     */
    private void handle(NodeUpdate update) {
        if (update.type() == NodeUpdate.Type.REFILL_DUE) {
            Optional<ResourceNodeDefinition> node = settings.find(update.nodeId());
            if (node.isPresent() && startRefill(node.get()) == RefillOutcome.STARTED) {
                announceWhenDone(node.get(), update);
            }
            return;
        }
        if (update.hasMessage()) {
            broadcast(update.messageKey(), update.placeholders());
        }
    }

    private void announceWhenDone(ResourceNodeDefinition node, NodeUpdate update) {
        RefillJob job = running.get(node.id().toLowerCase(Locale.ROOT));
        if (job != null) {
            job.announceWhenDone(update);
        }
    }

    /** What happened when a refill was asked for. */
    public enum RefillOutcome {
        STARTED,
        /** One was already running; a second walk over the same region is refused. */
        BUSY,
        /** The node's world is not loaded on this server. */
        WORLD_MISSING,
        /** The region sits entirely outside its world's height range. */
        OUTSIDE_WORLD
    }

    /** Starts a refill, unless one is already running for that node. */
    public RefillOutcome startRefill(ResourceNodeDefinition node) {
        String key = node.id().toLowerCase(Locale.ROOT);
        if (running.containsKey(key)) {
            // Refusing rather than restarting: a schedule tighter than the time a
            // refill takes should not compound into two walks over the same region.
            plugin.getLogger().warning("Skipping a refill of '" + node.id()
                    + "': the previous one is still running. Its interval is shorter than the "
                    + "time it takes to fill, or blocks-per-tick is too low.");
            return RefillOutcome.BUSY;
        }
        World world = Bukkit.getWorld(node.region().world());
        if (world == null) {
            plugin.getLogger().warning("Cannot refill '" + node.id() + "': world '"
                    + node.region().world() + "' is not loaded.");
            return RefillOutcome.WORLD_MISSING;
        }
        // getMaxHeight() is exclusive - "if the max height is 100, there are only
        // blocks from y=0 to y=99" (Paper 26.2 javadoc, WorldInfo).
        Optional<Cuboid> region = node.region()
                .withYClampedTo(world.getMinHeight(), world.getMaxHeight() - 1);
        if (region.isEmpty()) {
            plugin.getLogger().warning("Cannot refill '" + node.id() + "': its region "
                    + node.region() + " is entirely outside the height range of world '"
                    + world.getName() + "' (" + world.getMinHeight() + " to "
                    + (world.getMaxHeight() - 1) + ").");
            return RefillOutcome.OUTSIDE_WORLD;
        }

        RefillJob job = new RefillJob(node, world, region.get(), materials, settings.applyPhysics());
        running.put(key, job);
        // The job waits until every chunk of its region is loaded, and only then
        // places anything: a mountain nobody stands near is usually unloaded, and
        // World#getBlockAt would load it synchronously, from disk, on the main
        // thread (CONTRIBUTING.md section 5). The Paper javadoc guarantees this callback
        // runs on the main thread, once every requested chunk is loaded.
        Cuboid box = region.get();
        world.getChunksAtAsync(box.minChunkX(), box.minChunkZ(), box.maxChunkX(), box.maxChunkZ(), false,
                () -> onChunksLoaded(key, job));
        return RefillOutcome.STARTED;
    }

    /**
     * Main thread, every chunk of the job's region loaded: hold them with a ticket
     * so none unloads - and has to be reloaded synchronously - before the walk ends.
     */
    private void onChunksLoaded(String key, RefillJob job) {
        // Dropped while the chunks were loading: by a reload that changed the node,
        // or by shutdown. Adding a ticket to a disabled plugin also throws.
        if (!plugin.isEnabled() || running.get(key) != job) {
            return;
        }
        for (ChunkPosition chunk : chunkHolds.hold(job, job.region())) {
            job.world().addPluginChunkTicket(chunk.x(), chunk.z(), plugin);
        }
        job.markReady();
    }

    /**
     * Releases what a job was holding, whether it finished or was dropped. Harmless
     * for a job still waiting for its chunks: it holds nothing yet.
     */
    private void release(RefillJob job) {
        for (ChunkPosition chunk : chunkHolds.release(job)) {
            job.world().removePluginChunkTicket(chunk.x(), chunk.z(), plugin);
        }
    }

    /**
     * Refills a node now, on a staff request.
     *
     * @return what happened, so the command can say which of the three it was
     */
    public RefillOutcome refillNow(ResourceNodeDefinition node) {
        RefillOutcome outcome = startRefill(node);
        if (outcome != RefillOutcome.STARTED) {
            return outcome;
        }
        // The engine owns the announcement, including whether this node announces
        // at all - the command only decides that a refill should happen. Like a
        // scheduled one, it is broadcast when the region is actually full.
        manager.forceRefill(node.id()).ifPresent(update -> announceWhenDone(node, update));
        return outcome;
    }

    /** Advances every running refill by this tick's budget, and reports the ones that finish. */
    private void advanceRefills() {
        long ready = running.values().stream().filter(RefillJob::isReady).count();
        if (ready == 0) {
            return;
        }
        int budget = Math.max(1, settings.blocksPerTick());
        // Shared between the refills walking at once, so two mountains filling
        // together cost the same per tick as one. A refill still waiting for its
        // chunks takes no share.
        int perJob = (int) Math.max(1L, budget / ready);

        running.values().removeIf(job -> {
            if (!job.isReady()) {
                return false;
            }
            job.advance(perJob, occupancyAround(job));
            if (!job.isDone()) {
                return false;
            }
            release(job);
            plugin.getLogger().info("Refilled " + job.node().id() + ": " + job.placed()
                    + " blocks placed out of " + job.examined() + " examined, "
                    + job.leftForPlayers() + " left empty under players.");
            job.announcement().ifPresent(update -> broadcast(update.messageKey(), update.placeholders()));
            return true;
        });
    }

    /**
     * Where players' bodies are around a refill, sampled for each step since they
     * move between steps. Spectators are left out: the javadoc has them pass
     * through the world, so a block cannot trap them, and staff watching a refill
     * should not leave holes in it.
     */
    private Occupancy occupancyAround(RefillJob job) {
        Occupancy occupancy = new Occupancy(job.region());
        if (!settings.skipOccupiedBlocks()) {
            return occupancy;
        }
        for (Player player : job.world().getPlayers()) {
            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            BoundingBox box = player.getBoundingBox();
            occupancy.addBody(box.getMinX(), box.getMinY(), box.getMinZ(),
                    box.getMaxX(), box.getMaxY(), box.getMaxZ());
        }
        return occupancy;
    }

    // ------------------------------------------------------------------
    // The /events agenda
    // ------------------------------------------------------------------

    @Override
    public List<AgendaEntry> agendaEntries() {
        List<AgendaEntry> entries = new ArrayList<>();
        if (manager == null || !settings.enabled()) {
            return entries;
        }
        for (ResourceNodeDefinition node : settings.nodes()) {
            entries.add(describe(node));
        }
        return entries;
    }

    /** @return the one-line status of a node, shared by {@code /events} and {@code /resourcenode} */
    public AgendaEntry describe(ResourceNodeDefinition node) {
        if (isRefilling(node.id())) {
            return AgendaEntry.of(ResourceNodeMessages.LIST_REFILLING, "node", node.displayName());
        }
        Optional<ZonedDateTime> next = manager.getNextRefill(node);
        return next.map(time -> AgendaEntry.of(ResourceNodeMessages.LIST_SCHEDULED,
                        "node", node.displayName(), "time", TIME.format(time)))
                .orElseGet(() -> AgendaEntry.of(ResourceNodeMessages.LIST_UNSCHEDULED,
                        "node", node.displayName()));
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

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
        this.settings = ResourceNodeSettingsLoader.load(
                ConfigManager.loadFile(plugin, "resourcenodes.yml"),
                warning -> plugin.getLogger().warning("resourcenodes.yml: " + warning));
        this.materials = resolveMaterials(settings);

        if (manager != null) {
            // The times themselves may have changed; forget the window so a refill
            // whose hour just passed is not fired retroactively.
            manager.resetScheduleWindow();
            // A refill walking a region that no longer exists - or that no longer
            // looks like what it was started from - would keep placing blocks nobody
            // configured. Definitions are records, so "still the same node" is an
            // equals: a moved region or an edited palette drops the job, and the
            // next scheduled refill picks the new definition up.
            running.entrySet().removeIf(entry -> {
                RefillJob job = entry.getValue();
                if (settings.find(entry.getKey())
                        .filter(node -> node.equals(job.node())).isPresent()) {
                    return false;
                }
                release(job);
                plugin.getLogger().info("Stopped refilling '" + entry.getKey()
                        + "': it changed or was removed in resourcenodes.yml.");
                return true;
            });
            if (tickTask != null) {
                tickTask.cancel();
                scheduleTick();
            }
        }
    }

    /**
     * Resolves every configured block name against this server's own material
     * list, reporting the ones it does not know.
     *
     * <p>Block names are not verifiable at compile time and they do change between
     * Minecraft versions, so an unknown one is a warning naming the node, not a
     * silent no-op and not a crash (CONTRIBUTING.md section 6).
     */
    private Map<String, Material> resolveMaterials(ResourceNodeSettings config) {
        Map<String, Material> resolved = new HashMap<>();
        for (ResourceNodeDefinition node : config.nodes()) {
            for (String name : node.palette().materials()) {
                if (resolved.containsKey(name)) {
                    continue;
                }
                Material material = Material.matchMaterial(name);
                if (material == null) {
                    plugin.getLogger().warning("resourcenodes.yml: node '" + node.id()
                            + "' lists '" + name + "', which this server does not know. "
                            + "It will be skipped when refilling.");
                    continue;
                }
                if (!material.isBlock()) {
                    plugin.getLogger().warning("resourcenodes.yml: node '" + node.id()
                            + "' lists '" + name + "', which is an item and not a block. "
                            + "It will be skipped when refilling.");
                    continue;
                }
                resolved.put(name, material);
            }
        }
        return Map.copyOf(resolved);
    }

    public void disable() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        // Refills are dropped, not finished: a half-filled mountain is exactly what
        // the next scheduled refill repairs, and blocking a shutdown to place
        // thousands of blocks would be worse.
        running.values().forEach(this::release);
        running.clear();
    }
}
