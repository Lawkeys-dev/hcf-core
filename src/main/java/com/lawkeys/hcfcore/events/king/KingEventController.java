package com.lawkeys.hcfcore.events.king;

import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.claim.ClaimSettings.WarzoneRules.Area;
import com.lawkeys.hcfcore.database.dao.JdbcKingStashStore;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.startup.StartupBarrier;
import com.lawkeys.hcfcore.startup.StartupGate;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.util.ChunkPosition;
import com.lawkeys.hcfcore.util.ColorCodes;
import com.lawkeys.hcfcore.util.Durations;
import com.lawkeys.hcfcore.util.EffectCaps;
import com.lawkeys.hcfcore.util.RewardCommands;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.random.RandomGenerator;

/**
 * The server-facing half of Kill the King: draws and equips the King, sends them
 * into the warzone, hurts them outside it, puts their position in chat, pays the
 * winner and gives them their own items back. No rule lives here - who wins, when the
 * Wither grows, when time is up are all {@link KingEventManager}'s answers.
 *
 * <p><strong>The King's items.</strong> Taken into a {@link KingStashes} at the
 * crowning, written to the database right away, and handed back as soon as the
 * player is alive, online and no longer King: at once if they survive or staff
 * stops the event, at their respawn if they died, at their next login if they left
 * or the server went down. What they picked up during the reign stays theirs; only
 * the kit is taken back. What drops at the King's death is the kit, and it is
 * ordinary loot.
 *
 * <p><strong>Effects are short and refreshed</strong> rather than infinite: the
 * kit's effects and the Wither are re-applied every second with a few seconds to
 * run. A King who logs out, or a server that crashes, leaves at most that much
 * behind on the player - an infinite effect would stay forever.
 */
public final class KingEventController {

    /** Never drawn as King. Not granted by default, not even to operators. */
    public static final String EXEMPT_PERMISSION = "hcfcore.events.king.exempt";

    private static final int SPOT_ATTEMPTS = 16;
    private static final int EFFECT_TICKS = 200;
    private static final int REFRESH_BELOW_TICKS = 100;
    private static final long REFUSAL_COOLDOWN_MILLIS = 3_000L;
    private static final Set<Material> UNSAFE_GROUND = Set.of(
            Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE, Material.SOUL_CAMPFIRE);

    private final Plugin plugin;
    private final TeamModule teams;
    private final ClaimModule claims;
    private final LangManager lang;
    private final StartupGate startup;
    private final KingKitFactory kits;
    private final RandomGenerator random = RandomGenerator.getDefault();
    private volatile EffectCaps effectCaps = EffectCaps.NONE;

    private volatile KingSettings settings = KingSettings.defaults();
    private KingEventManager manager;
    private KingStashes stashes;
    private BukkitTask ruleTask;
    private BukkitTask coordinatesTask;
    /** True between the crowning and the end of the teleport: the King counts as inside meanwhile. */
    private boolean arriving;
    private long lastRefusal;

    /** The effect caps of {@code limiters.yml}, asked before giving an effect; filled after startup. */
    public void setEffectCaps(EffectCaps effectCaps) {
        this.effectCaps = Objects.requireNonNull(effectCaps, "effectCaps");
    }

    /** @param claims may be {@code null} if the claim module is not running; KTK then cannot start */
    public KingEventController(Plugin plugin, TeamModule teams, ClaimModule claims, LangManager lang,
                               StartupGate startup) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.claims = claims;
        this.lang = Objects.requireNonNull(lang, "lang");
        this.startup = Objects.requireNonNull(startup, "startup");
        this.kits = new KingKitFactory(new NamespacedKey(plugin, "king_kit"));
    }

    public KingEventManager getManager() {
        return manager;
    }

    public KingSettings getSettings() {
        return settings;
    }

    KingKitFactory getKits() {
        return kits;
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    public void enable(DataSource dataSource) {
        KingStashStore store = dataSource == null
                ? KingStashStore.NO_OP
                : new JdbcKingStashStore(dataSource, message -> plugin.getLogger().info(message));
        this.stashes = new KingStashes(store);
        this.manager = new KingEventManager(() -> settings, System::currentTimeMillis, random);

        // The stashes are items owed to players: nothing may crown or hand back
        // before they are loaded (StartupGate).
        StartupBarrier.Load load = startup.expect("king stashes");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                stashes.loadAll();
                if (stashes.size() > 0) {
                    plugin.getLogger().info(stashes.size() + " former King(s) are owed their items; "
                            + "they get them back at their next login.");
                }
                load.succeeded();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load king stashes.", e);
                load.failed();
            }
        });

        plugin.getServer().getPluginManager().registerEvents(new KingListener(this), plugin);
        this.ruleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /**
     * Applies new settings. A run whose definition was removed or changed, or any
     * run once events are disabled, is stopped - and its King gets their items back.
     */
    public void applySettings(KingSettings replacement) {
        Objects.requireNonNull(replacement, "replacement");
        if (manager != null) {
            // Stopped before the swap, so the ending still reads the definition the
            // King was equipped from - that is whose effects must be lifted.
            manager.getCurrent().ifPresent(run -> {
                Optional<KingEventDefinition> now = replacement.find(run.getDefinition().id());
                if (!replacement.enabled() || now.isEmpty() || !now.get().equals(run.getDefinition())) {
                    plugin.getLogger().info("Stopped '" + run.getDefinition().id()
                            + "': its definition changed or events were disabled.");
                    manager.stop().ifPresent(this::handle);
                }
            });
            manager.resetScheduleWindow();
        }
        this.settings = replacement;
    }

    /** Hands the King their items back if they are here, then writes everything still pending. */
    public void disable() {
        if (ruleTask != null) {
            ruleTask.cancel();
            ruleTask = null;
        }
        stopCoordinates();
        if (manager == null) {
            return;
        }
        Optional<KingRun> run = manager.getCurrent();
        manager.stopAll();
        run.map(KingRun::getKingId).map(Bukkit::getPlayer).ifPresent(king -> {
            liftEffects(king, run.get().getDefinition());
            restore(king);
        });
        // Deliberately synchronous: the scheduler no longer runs tasks at this
        // point (ARCHITECTURE.md section 3). A stash that could not be handed back
        // is written here and waits for the player's next login.
        try {
            stashes.flush();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save king stashes on shutdown", e);
        }
    }

    // ------------------------------------------------------------------
    // Starting and stopping
    // ------------------------------------------------------------------

    /**
     * Opens a run: finds a spot in the warzone, then draws the King. Both take a
     * few ticks, since the spot's chunk is loaded asynchronously.
     *
     * @return {@code false} when a run is already under way
     */
    public boolean start(KingEventDefinition definition) {
        if (!manager.open(definition)) {
            return false;
        }
        KingRun run = manager.getCurrent().orElseThrow();
        World world = Bukkit.getWorld(definition.world());
        Area area = warzoneOf(definition.world());
        if (world == null || area == null) {
            plugin.getLogger().warning("Kill the King '" + definition.id() + "' cannot run: world '"
                    + definition.world() + "' is not loaded or has no warzone in claims.yml.");
            manager.cancel(KingMessages.CANCELLED_NO_WARZONE).ifPresent(this::handle);
            return true;
        }
        if (world.hasCeiling()) {
            // The spot is found on the surface; under a bedrock ceiling the
            // "surface" is the roof.
            plugin.getLogger().warning("Kill the King '" + definition.id() + "' cannot run in '"
                    + definition.world() + "': it has a ceiling, and the King is sent to the surface.");
            manager.cancel(KingMessages.CANCELLED_NO_SPOT).ifPresent(this::handle);
            return true;
        }
        List<Candidate> candidates = candidates();
        if (candidates.size() < definition.minimumPlayers()) {
            // Before loading a single chunk. crown() is what refuses, so the message
            // is the same as when the players leave during the search.
            manager.crown(candidates).ifPresent(this::handle);
            return true;
        }
        findSpot(run, world, area, SPOT_ATTEMPTS);
        return true;
    }

    /** Stops the run, crowned or not. @return {@code false} when nothing was running */
    public boolean stop() {
        Optional<KingUpdate> update = manager.stop();
        update.ifPresent(this::handle);
        return update.isPresent();
    }

    private void findSpot(KingRun run, World world, Area area, int attemptsLeft) {
        if (manager.getCurrent().orElse(null) != run) {
            return; // stopped while searching
        }
        if (attemptsLeft == 0) {
            manager.cancel(KingMessages.CANCELLED_NO_SPOT).ifPresent(this::handle);
            return;
        }
        int[] column = WarzoneSpots.randomColumn(area, random);
        if (claims.getManager() == null
                || claims.getManager().getOwner(world.getName(), column[0], column[1]).isPresent()) {
            // Spawn, a road, a team's old claim: not the open warzone.
            findSpot(run, world, area, attemptsLeft - 1);
            return;
        }
        // The javadoc guarantees the future completes on the main thread, so the
        // world can be read in it; the load itself does not block the tick.
        world.getChunkAtAsync(ChunkPosition.toChunk(column[0]), ChunkPosition.toChunk(column[1])).whenComplete((loaded, error) -> {
            if (manager.getCurrent().orElse(null) != run) {
                return;
            }
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Kill the King could not load a chunk for its spot", error);
                findSpot(run, world, area, attemptsLeft - 1);
                return;
            }
            Location spot = standingSpot(world, column[0], column[1]);
            if (spot == null) {
                findSpot(run, world, area, attemptsLeft - 1);
            } else {
                guarded(run, () -> crownAt(run, spot));
            }
        });
    }

    /**
     * Runs a step of the crowning, calling the run off if it throws.
     *
     * <p>These steps run inside {@code CompletableFuture} callbacks, which swallow
     * exceptions: without this, a failure halfway through would leave a run
     * "reigning" with a King who was never equipped, and nothing in the log.
     * Calling it off hands back whatever was already taken from them.
     */
    private void guarded(KingRun run, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Kill the King failed while crowning; the event is called off.", e);
            if (manager.getCurrent().orElse(null) == run) {
                manager.cancel(KingMessages.CANCELLED_TELEPORT_FAILED).ifPresent(this::handle);
            }
        }
    }

    /** @return where a player can stand on the surface of this column, or {@code null} */
    private static Location standingSpot(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        // getMaxHeight is exclusive: two blocks of headroom must fit under it.
        if (y <= world.getMinHeight() || y + 2 >= world.getMaxHeight()) {
            return null;
        }
        Block ground = world.getBlockAt(x, y, z);
        Block feet = ground.getRelative(BlockFace.UP);
        Block head = feet.getRelative(BlockFace.UP);
        boolean firm = ground.isSolid() && !ground.isLiquid() && !UNSAFE_GROUND.contains(ground.getType());
        boolean clear = feet.isPassable() && head.isPassable() && !feet.isLiquid() && !head.isLiquid()
                && feet.getType() != Material.FIRE && feet.getType() != Material.SOUL_FIRE;
        return firm && clear ? new Location(world, x + 0.5, y + 1, z + 0.5) : null;
    }

    private void crownAt(KingRun run, Location spot) {
        Optional<KingUpdate> drawn = manager.crown(candidates());
        if (drawn.isEmpty()) {
            return;
        }
        KingUpdate update = drawn.get();
        if (update.type() != KingUpdate.Type.CROWNED) {
            handle(update);
            return;
        }
        Player king = Bukkit.getPlayer(update.kingId());
        if (king != null) {
            // An open inventory holds items outside getContents - on the cursor, in
            // the crafting grid. Closing it puts them back first.
            king.closeInventory();
        }
        byte[] contents = king == null ? null : ItemStack.serializeItemsAsBytes(king.getInventory().getContents());
        // Never happens - the King was drawn among online players with no stash
        // this very tick - but a crown that cannot take their items must not go on.
        if (contents == null || !stashes.put(king.getUniqueId(), contents)) {
            manager.cancel(KingMessages.CANCELLED_TELEPORT_FAILED).ifPresent(this::handle);
            return;
        }
        PlayerInventory inventory = king.getInventory();
        // The same array shape getContents gave, so every slot it covered - armour
        // and off-hand included - is emptied.
        inventory.setContents(new ItemStack[inventory.getContents().length]);
        flushStashesAsync();

        KingEventDefinition definition = run.getDefinition();
        kits.equip(king, definition.kit());
        refreshKitEffects(king, definition);
        if (king.isInsideVehicle()) {
            king.leaveVehicle(); // no vehicles for the King: see KingListener
        }

        // Announced before the teleport starts: its future may complete at once,
        // and a failure there calls the run off - which must come after the crowning,
        // not before it.
        handle(update);
        lang.send(king, KingMessages.YOU_ARE_KING,
                "event", definition.displayName(), "time", update.placeholders().getOrDefault("time", ""));
        startCoordinates(definition);

        arriving = true;
        king.teleportAsync(spot, PlayerTeleportEvent.TeleportCause.PLUGIN).whenComplete((moved, error) ->
                onMainThread(() -> guarded(run, () -> {
                    if (manager.getCurrent().orElse(null) != run) {
                        return;
                    }
                    arriving = false;
                    if (error != null || !Boolean.TRUE.equals(moved)) {
                        plugin.getLogger().log(Level.WARNING, "Kill the King could not send "
                                + king.getName() + " to the warzone; the event is called off.", error);
                        manager.cancel(KingMessages.CANCELLED_TELEPORT_FAILED).ifPresent(this::handle);
                    }
                })));
    }

    /** @return the players who may be drawn right now */
    private List<Candidate> candidates() {
        List<Candidate> candidates = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            GameMode mode = player.getGameMode();
            if (player.isDead()
                    || (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE)
                    || player.hasPermission(EXEMPT_PERMISSION)
                    // Still owed items from an earlier reign: a second stash would
                    // have nowhere to go without destroying the first.
                    || stashes.has(player.getUniqueId())) {
                continue;
            }
            candidates.add(new Candidate(player.getUniqueId(), teamOf(player.getUniqueId())));
        }
        return candidates;
    }

    // ------------------------------------------------------------------
    // The reign
    // ------------------------------------------------------------------

    private void tick() {
        try {
            // Nothing starts and nobody is judged before the stashes are loaded.
            if (!startup.isReady()) {
                return;
            }
            for (KingEventDefinition due : manager.dueDefinitions()) {
                if (!start(due)) {
                    plugin.getLogger().info("Kill the King '" + due.id()
                            + "' was due but another one is running; skipped.");
                }
            }
            Optional<KingRun> run = manager.getCurrent().filter(KingRun::isReigning);
            if (run.isEmpty()) {
                return;
            }
            Player king = Bukkit.getPlayer(run.get().getKingId());
            if (king == null) {
                // The quit listener ends the run first; this only covers a King who
                // vanished some other way.
                manager.kingQuit().ifPresent(this::handle);
                return;
            }
            boolean inside = arriving || isInsideZone(run.get().getDefinition(), king.getLocation());
            for (KingUpdate update : manager.tick(inside)) {
                handle(update);
                // The penalty's damage can kill the King, which ends the reign at once;
                // the rest of this tick is about a King who no longer reigns.
                if (!manager.isKing(king.getUniqueId())) {
                    break;
                }
            }
            if (manager.isKing(king.getUniqueId())) {
                refreshKitEffects(king, run.get().getDefinition());
            }
        } catch (Exception e) {
            // One bad tick must not kill the repeating task for the whole session.
            plugin.getLogger().log(Level.SEVERE, "A Kill the King tick failed", e);
        }
    }

    /**
     * @return whether the King stands in the event zone: the warzone square of the
     *         event's world. A safe zone inside it counts as outside - the King
     *         cannot win by waiting in spawn, even if they got in.
     */
    boolean isInsideZone(KingEventDefinition definition, Location location) {
        if (location.getWorld() == null || !location.getWorld().getName().equals(definition.world())) {
            return false;
        }
        return claims != null && claims.getSettings().warzone().covers(location.getWorld().getName(),
                location.getBlockX(), location.getBlockZ()) && !isSafeZone(location);
    }

    boolean isSafeZone(Location location) {
        return location.getWorld() != null && claims != null
                && claims.ownerAt(location).map(Team::isSafeZone).orElse(false);
    }

    private Area warzoneOf(String world) {
        return claims == null ? null : claims.getSettings().warzone().areas().get(world);
    }

    /** Does what an update says: talk, hurt, pay, give back. */
    void handle(KingUpdate update) {
        Map<String, String> placeholders = new LinkedHashMap<>(update.placeholders());
        placeholders.put("king", update.kingId() == null ? "" : nameOf(update.kingId()));
        placeholders.put("player", update.winnerId() == null ? "" : nameOf(update.winnerId()));

        switch (update.type().audience()) {
            case EVERYONE -> broadcast(update.messageKey(), placeholders);
            case KING -> {
                Player king = update.kingId() == null ? null : Bukkit.getPlayer(update.kingId());
                if (king != null) {
                    lang.send(king, update.messageKey(), placeholders);
                }
            }
            case NOBODY -> {
            }
        }

        Player king = update.kingId() == null ? null : Bukkit.getPlayer(update.kingId());
        switch (update.type()) {
            case PENALTY -> {
                if (king != null) {
                    ensureEffect(king, PotionEffectType.WITHER, update.witherLevel() - 1);
                    if (update.damage() > 0) {
                        king.damage(update.damage());
                    }
                }
            }
            case RETURNED -> {
                if (king != null) {
                    king.removePotionEffect(PotionEffectType.WITHER);
                }
            }
            default -> {
            }
        }

        if (update.type().isEnding()) {
            finish(update, king);
        }
    }

    private void finish(KingUpdate update, Player king) {
        arriving = false;
        stopCoordinates();
        if (update.winnerId() != null) {
            teams.getManager().getTeamOf(update.winnerId()).ifPresent(team -> teams.getManager().recordKingWin(team));
            reward(update);
        }
        if (king == null) {
            return; // offline: their items wait for their next login
        }
        settings.find(update.eventId()).ifPresent(definition -> liftEffects(king, definition));
        king.removePotionEffect(PotionEffectType.WITHER);
        // A King who died is handed their items at the respawn - never now, or
        // they would be dropped with the kit. One who is leaving keeps the kit on,
        // so that combat-logging drops the kit and not their own gear.
        if (update.type() != KingUpdate.Type.KILLED && update.type() != KingUpdate.Type.DIED
                && update.type() != KingUpdate.Type.FLED) {
            restore(king);
        }
    }

    private void reward(KingUpdate update) {
        String winner = nameOf(update.winnerId());
        String event = settings.find(update.eventId()).map(KingEventDefinition::displayName).orElse(update.eventId());
        settings.find(update.eventId()).ifPresent(definition -> RewardCommands.run(
                definition.rewardCommands(),
                Map.of("player", winner, "event", event),
                command -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command),
                (command, e) -> plugin.getLogger().log(Level.WARNING,
                        "Reward command failed for event " + update.eventId() + ": " + command, e)));
    }

    // ------------------------------------------------------------------
    // Giving the King's items back
    // ------------------------------------------------------------------

    /**
     * Hands a former King their items back, if they are owed any and can take them
     * now: alive, and not King again.
     *
     * <p>The kit goes; what they picked up during the reign stays, and whatever no
     * longer fits once their own items are back drops at their feet rather than
     * vanishing.
     *
     * @return whether the items were handed back
     */
    boolean restore(Player player) {
        UUID id = player.getUniqueId();
        Optional<byte[]> owed = stashes.get(id);
        if (owed.isEmpty() || player.isDead() || manager.isKing(id)) {
            return false;
        }
        ItemStack[] items;
        try {
            items = ItemStack.deserializeItemsFromBytes(owed.get());
        } catch (RuntimeException e) {
            // Kept, not dropped: a stash that cannot be read today can still be
            // recovered by hand from the database.
            plugin.getLogger().log(Level.SEVERE, "Could not read the items owed to " + player.getName()
                    + "; they stay in hcf_king_stashes.", e);
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        if (items.length > inventory.getSize()) {
            plugin.getLogger().severe("The items owed to " + player.getName() + " do not fit an inventory; "
                    + "they stay in hcf_king_stashes.");
            return false;
        }
        List<ItemStack> kept = new ArrayList<>();
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.isEmpty() && !kits.isKit(item)) {
                kept.add(item);
            }
        }
        inventory.setContents(items);
        for (ItemStack leftover : inventory.addItem(kept.toArray(new ItemStack[0])).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        stashes.remove(id);
        flushStashesAsync();
        lang.send(player, KingMessages.ITEMS_RETURNED);
        return true;
    }

    /** Whether this player is owed items, for the listener's quick checks. */
    boolean isOwed(UUID playerId) {
        return stashes != null && stashes.has(playerId);
    }

    private void flushStashesAsync() {
        // During shutdown the scheduler takes no new task from this plugin;
        // disable() flushes synchronously right after instead.
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                stashes.flush();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Writing king stashes failed; they stay queued for the next attempt.", e);
            }
        });
    }

    // ------------------------------------------------------------------
    // Effects, coordinates, messages
    // ------------------------------------------------------------------

    private void refreshKitEffects(Player king, KingEventDefinition definition) {
        for (Map.Entry<String, Integer> effect : definition.kit().effects().entrySet()) {
            PotionEffectType type = KingKitFactory.effect(effect.getKey());
            if (type != null) {
                ensureEffect(king, type, effect.getValue() - 1);
            }
        }
    }

    private void liftEffects(Player king, KingEventDefinition definition) {
        for (String name : definition.kit().effects().keySet()) {
            PotionEffectType type = KingKitFactory.effect(name);
            if (type != null) {
                king.removePotionEffect(type);
            }
        }
    }

    /**
     * Applies an effect at this amplifier, re-applying only when it is missing, at
     * another amplifier, or close to running out - so a periodic effect such as the
     * Wither keeps its own rhythm instead of being restarted every second. Within the
     * effect caps, asked first: the amplifier compared is then the one given.
     */
    private void ensureEffect(Player player, PotionEffectType type, int wanted) {
        int amplifier = effectCaps.allowed(type, wanted);
        if (amplifier < 0) {
            return;
        }
        PotionEffect current = player.getPotionEffect(type);
        if (current == null || current.getAmplifier() != amplifier
                || (!current.isInfinite() && current.getDuration() < REFRESH_BELOW_TICKS)) {
            player.addPotionEffect(new PotionEffect(type, EFFECT_TICKS, amplifier, true, true, true));
        }
    }

    /**
     * Where the King is and how much health they have left, in chat every
     * {@code announce-interval-seconds}. The position is on every scoreboard all
     * along ({@code %king_location_line%}) and, for Lunar Client players, a waypoint:
     * a chat line every second, as it used to be, flooded the chat for half an hour.
     */
    private void startCoordinates(KingEventDefinition definition) {
        stopCoordinates();
        long period = definition.announceIntervalSeconds() * 20L;
        if (period <= 0) {
            return;
        }
        this.coordinatesTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Optional<KingRun> run = manager.getCurrent().filter(KingRun::isReigning);
            Player king = run.map(KingRun::getKingId).map(Bukkit::getPlayer).orElse(null);
            if (king == null || arriving) {
                return;
            }
            Location at = king.getLocation();
            Map<String, String> placeholders = new LinkedHashMap<>();
            placeholders.put("event", run.get().getDefinition().displayName());
            placeholders.put("king", king.getName());
            placeholders.put("x", String.valueOf(at.getBlockX()));
            placeholders.put("y", String.valueOf(at.getBlockY()));
            placeholders.put("z", String.valueOf(at.getBlockZ()));
            placeholders.put("world", at.getWorld() == null ? "" : at.getWorld().getName());
            placeholders.put("time", Durations.format(manager.getRemainingSeconds()));
            AttributeInstance maximum = king.getAttribute(Attribute.MAX_HEALTH);
            placeholders.put("health", String.valueOf(KingHealth.percent(king.getHealth(),
                    maximum == null ? 0.0 : maximum.getValue())));
            String message = lang.get(KingMessages.COORDINATES, placeholders);
            if (!message.isEmpty()) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage(message);
                }
                Bukkit.getConsoleSender().sendMessage(message);
            }
        }, period, period);
    }

    private void stopCoordinates() {
        if (coordinatesTask != null) {
            coordinatesTask.cancel();
            coordinatesTask = null;
        }
    }

    /**
     * Answers {@code general/}'s spawn guard: the King may not enter spawn by any
     * means, {@code /spawn} included.
     *
     * @return {@code true} when this player is the King, having told them
     */
    public boolean refuseSpawn(Player player) {
        if (!manager.isKing(player.getUniqueId())) {
            return false;
        }
        lang.send(player, KingMessages.SAFE_ZONE_REFUSED);
        return true;
    }

    /**
     * Tells the King why something was refused - at most every few seconds, since
     * walking into the border of spawn is refused on every step, and an entity
     * click once per hand.
     */
    void refuse(Player king, String key) {
        long now = System.currentTimeMillis();
        if (now - lastRefusal >= REFUSAL_COOLDOWN_MILLIS) {
            lastRefusal = now;
            lang.send(king, key);
        }
    }

    /** @return the player's team, or {@code null} for no team or no player */
    UUID teamOf(UUID playerId) {
        return playerId == null
                ? null
                : teams.getManager().getTeamOf(playerId).map(Team::getId).orElse(null);
    }

    private void broadcast(String key, Map<String, String> placeholders) {
        String message = lang.get(key, placeholders);
        if (message.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
        plugin.getLogger().info(ColorCodes.strip(message));
    }

    private String nameOf(UUID player) {
        return teams.nameOf(player);
    }

    private void onMainThread(Runnable action) {
        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, action);
        }
    }

    Plugin getPlugin() {
        return plugin;
    }
}
