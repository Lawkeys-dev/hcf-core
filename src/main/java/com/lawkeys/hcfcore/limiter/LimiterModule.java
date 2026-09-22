package com.lawkeys.hcfcore.limiter;

import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.database.dao.JdbcClaimBlockCountStore;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.limiter.command.LimitsSubCommand;
import com.lawkeys.hcfcore.limiter.listener.ClaimBlockListener;
import com.lawkeys.hcfcore.limiter.listener.LimiterListener;
import com.lawkeys.hcfcore.startup.StartupBarrier;
import com.lawkeys.hcfcore.startup.StartupGate;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.util.ChunkPosition;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import javax.sql.DataSource;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Enchantment and potion limits, and blocks per claim (FEATURES.md sections 11 and 12).
 *
 * <p>The caps ship empty: which levels a map allows - Protection I or II, Sharpness I
 * or II, no Strength II - is the defining choice of an HCF map, and inventing one
 * would be deciding the operator's gameplay for them. With nothing listed, nothing is
 * touched. The same goes for blocks per claim ({@link ClaimBlockLimits}), the one part
 * that reads territory and keeps data: counts per claimed chunk
 * ({@link ClaimBlockCounts}), stored, recounted exactly off the main thread.
 */
public final class LimiterModule {

    /** Staff who place limited blocks freely. */
    public static final String BYPASS_PERMISSION = "hcfcore.limiter.bypass";
    /** Chunks recounted per tick: a snapshot is a copy, and a hundred at once would be a spike. */
    private static final int RECOUNTS_PER_TICK = 4;

    private final Plugin plugin;
    private final LangManager lang;
    private final StartupGate startup;
    private final ClaimModule claims;

    private volatile LimiterSettings settings = LimiterSettings.defaults();
    private volatile ClaimBlockLimits claimBlocks = ClaimBlockLimits.none();
    private LimiterListener listener;
    private ClaimBlockCounts counts;
    private volatile boolean countsLoaded;
    /** Main thread only: chunks recounted since the start, and those waiting their turn. */
    private final Set<ChunkPosition> recounted = new HashSet<>();
    private final Set<ChunkPosition> recountQueue = new LinkedHashSet<>();
    private BukkitTask recountTask;
    private BukkitTask saveTask;

    /** @param claims may be {@code null}; there is then no territory to limit */
    public LimiterModule(Plugin plugin, LangManager lang, StartupGate startup, ClaimModule claims) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lang = Objects.requireNonNull(lang, "lang");
        this.startup = Objects.requireNonNull(startup, "startup");
        this.claims = claims;
    }

    public LangManager getLang() {
        return lang;
    }

    public ClaimBlockLimits getClaimBlocks() {
        return claimBlocks;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public LimiterSettings getSettings() {
        return settings;
    }

    /**
     * The effect caps ({@code effects.caps}), for the modules that give effects
     * themselves: {@link com.lawkeys.hcfcore.util.EffectCaps}.
     */
    public int allowedAmplifier(org.bukkit.potion.PotionEffectType type, int amplifier) {
        return settings.allowedLevel(key(type), amplifier + 1, false) - 1;
    }

    /** @return the key {@link LevelCaps} expects for an enchantment or an effect */
    public static String key(Keyed keyed) {
        return keyed.getKey().toString();
    }

    /**
     * @param dataSource          where the block counts are kept, or {@code null} for memory only
     * @param saveIntervalSeconds how often they are written; {@code 0} leaves only the shutdown save
     */
    public void enable(DataSource dataSource, long saveIntervalSeconds) {
        this.listener = new LimiterListener(this);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        this.counts = new ClaimBlockCounts(dataSource == null ? ClaimBlockCountStore.NO_OP
                : new JdbcClaimBlockCountStore(dataSource, message -> plugin.getLogger().info(message)));
        reloadSettings();
        if (claims == null) {
            return;
        }
        StartupBarrier.Load load = startup.expect("claim block counts");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                counts.loadAll();
                load.succeeded();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    countsLoaded = true;
                    queueLoadedClaims();
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load the block counts of claims.", e);
                load.failed();
            }
        });
        if (saveIntervalSeconds > 0) {
            long ticks = saveIntervalSeconds * 20L;
            this.saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushQuietly, ticks, ticks);
        }
        this.recountTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processRecounts, 1L, 1L);
        plugin.getServer().getPluginManager().registerEvents(new ClaimBlockListener(this), plugin);
        claims.getTeams().registerSubCommand(new LimitsSubCommand(this));
    }

    public void disable() {
        if (recountTask != null) {
            recountTask.cancel();
            recountTask = null;
        }
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (counts == null || !countsLoaded) {
            return;
        }
        try {
            counts.flush();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save the block counts of claims on shutdown.", e);
        }
    }

    public void reloadSettings() {
        var section = ConfigManager.loadFile(plugin, "limiters.yml");
        LimiterSettings loaded = LimiterSettingsLoader.load(section,
                warning -> plugin.getLogger().warning("limiters.yml: " + warning));
        checkNames("enchantments", loaded.enchantments(), key ->
                RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key) != null);
        checkNames("potions", loaded.potions(), key -> Registry.MOB_EFFECT.get(key) != null);
        checkNames("effects", loaded.effects(), key -> Registry.MOB_EFFECT.get(key) != null);
        this.settings = loaded;
        if (listener != null) {
            // A cap set by /hcf reload holds at once on the effects players already have.
            Bukkit.getOnlinePlayers().forEach(listener::enforceEffectCaps);
        }
        ClaimBlockLimits blocks = loadClaimBlocks(section == null ? null : section.getConfigurationSection("claim-blocks"));
        boolean changed = !blocks.equals(claimBlocks);
        this.claimBlocks = blocks;
        if (changed && countsLoaded) {
            // A block newly limited has never been counted: everything is counted again.
            recounted.clear();
            queueLoadedClaims();
        }
        // A cap lowered by /hcf reload applies to what people already carry, not only
        // to what they make next.
        if (listener != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                listener.fixInventory(player);
            }
        }
    }

    private ClaimBlockLimits loadClaimBlocks(ConfigurationSection section) {
        if (section == null) {
            return ClaimBlockLimits.none();
        }
        Map<String, Integer> limits = new LinkedHashMap<>();
        ConfigurationSection configured = section.getConfigurationSection("limits");
        if (configured != null) {
            for (String name : configured.getKeys(false)) {
                Material material = Material.matchMaterial(name);
                int limit = configured.getInt(name, -1);
                if (material == null || !material.isBlock()) {
                    plugin.getLogger().warning("limiters.yml: claim-blocks.limits: '" + name + "' is not a block; ignored.");
                } else if (limit < 0) {
                    plugin.getLogger().warning("limiters.yml: claim-blocks.limits." + name
                            + " must be 0 or more; ignored.");
                } else {
                    limits.put(material.name(), limit);
                }
            }
        }
        return new ClaimBlockLimits(section.getBoolean("enabled", true), limits);
    }

    // ------------------------------------------------------------------
    // Blocks per claim
    // ------------------------------------------------------------------

    /** @return the player team whose territory that block is - the only land that is counted */
    public Optional<Team> countingTeam(Block block) {
        if (claims == null || claims.getManager() == null) {
            return Optional.empty();
        }
        return claims.ownerAt(block.getLocation()).filter(team -> !team.getType().isSystem());
    }

    /** @return whether any player team's claim covers part of that chunk: a chunk worth counting */
    private boolean isCounted(ChunkPosition chunk) {
        if (claims == null || claims.getManager() == null) {
            return false;
        }
        for (com.lawkeys.hcfcore.claim.ClaimArea area : claims.getManager().getClaimsIn(chunk)) {
            if (isPlayerTeam(area.teamId())) {
                return true;
            }
        }
        return false;
    }

    private boolean isPlayerTeam(java.util.UUID teamId) {
        return claims.getTeams().getManager().getTeam(teamId).map(team -> !team.getType().isSystem()).orElse(false);
    }

    /** @return every cell a team's claims cover: what its total is summed over */
    private Set<ClaimCell> cellsOf(Team team) {
        Set<ClaimCell> cells = new java.util.LinkedHashSet<>();
        for (ChunkPosition chunk : claims.getManager().getClaimChunks(team.getId())) {
            cells.add(new ClaimCell(chunk, team.getId()));
        }
        return cells;
    }

    /** What stops a placement: the block, how many stand, and the limit. */
    public record Refusal(String block, int count, int limit) {
    }

    /**
     * @return why this block may not be placed here - the territory already holds its
     *         limit - or empty when it may
     */
    public Optional<Refusal> refusal(Player player, Block block) {
        ClaimBlockLimits limits = claimBlocks;
        if (!countsLoaded || !limits.isActive() || bypasses(player)) {
            return Optional.empty();
        }
        String material = block.getType().name();
        OptionalInt limit = limits.limitFor(material);
        ChunkPosition chunk = ClaimModule.toChunk(block.getLocation());
        Optional<Team> team = limit.isPresent() ? countingTeam(block) : Optional.empty();
        if (team.isEmpty()) {
            return Optional.empty();
        }
        if (!recounted.contains(chunk)) {
            // Claimed while loaded, so never counted since: counted now, off the main thread.
            recountQueue.add(chunk);
        }
        int count = counts.total(cellsOf(team.get()), material);
        return ClaimBlockLimits.mayPlace(count, limit.getAsInt())
                ? Optional.empty()
                : Optional.of(new Refusal(material, count, limit.getAsInt()));
    }

    /**
     * The permission says a rank may ignore the limits; {@code /staffbuild} says they
     * are choosing to right now - the same split as claim protection's
     * {@code BuildOverride}. Either alone is not enough: an operator placing hoppers in
     * their own base in normal play is counted like anybody else (the first in-game
     * test found the limit silently waved through for the owner, who is op).
     */
    private boolean bypasses(Player player) {
        return player.hasPermission(BYPASS_PERMISSION) && claims != null
                && claims.getBuildOverride().allows(player.getUniqueId());
    }

    /** @return how many of each limited block this team's territory holds, in the order of the file */
    public Map<String, Integer> usage(Team team) {
        Map<String, Integer> usage = new LinkedHashMap<>();
        if (claims == null || counts == null) {
            return usage;
        }
        Set<ClaimCell> territory = cellsOf(team);
        claimBlocks.limits().keySet().forEach(material -> usage.put(material, counts.total(territory, material)));
        return usage;
    }

    /** A limited block placed ({@code +1}) or gone ({@code -1}) in a claimed chunk. */
    public void changed(Block block, Material type, int delta) {
        if (!countsLoaded || !claimBlocks.isLimited(type.name())) {
            return;
        }
        Optional<Team> team = countingTeam(block);
        if (team.isPresent()) {
            counts.adjust(new ClaimCell(ClaimModule.toChunk(block.getLocation()), team.get().getId()),
                    type.name(), delta);
        }
    }

    public boolean isLimited(Material type) {
        return claimBlocks.isLimited(type.name());
    }

    /** Counted again soon - after a piston has moved limited blocks, which the events cannot follow. */
    public void recountSoon(ChunkPosition chunk) {
        if (countsLoaded && isCounted(chunk)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> recountQueue.add(chunk), 5L);
        }
    }

    /** A chunk has loaded: counted exactly, once per start, if it is claimed. */
    public void chunkLoaded(Chunk chunk) {
        if (!countsLoaded || !claimBlocks.isActive()) {
            return;
        }
        ChunkPosition position = new ChunkPosition(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        if (!recounted.contains(position) && isCounted(position)) {
            recountQueue.add(position);
        }
    }

    private void queueLoadedClaims() {
        if (!claimBlocks.isActive()) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                chunkLoaded(chunk);
            }
        }
    }

    /**
     * A few queued recounts per tick: the snapshot is taken here, on the main thread,
     * and read off it - {@code ChunkSnapshot} is "a static, thread-safe snapshot ...
     * handed off for processing in another thread" (26.2 javadoc). A chunk unloaded
     * while queued waits for its next load; nothing is loaded here.
     */
    private void processRecounts() {
        Iterator<ChunkPosition> queued = recountQueue.iterator();
        for (int done = 0; done < RECOUNTS_PER_TICK && queued.hasNext(); done++) {
            ChunkPosition position = queued.next();
            queued.remove();
            World world = Bukkit.getWorld(position.world());
            if (world == null || !world.isChunkLoaded(position.x(), position.z())
                    || !counts.beginRecount(position)) {
                continue;
            }
            ChunkSnapshot snapshot = world.getChunkAt(position.x(), position.z()).getChunkSnapshot(false, false, false);
            int minY = world.getMinHeight();
            int maxY = world.getMaxHeight();
            Set<Material> wanted = EnumSet.noneOf(Material.class);
            claimBlocks.limits().keySet().forEach(name -> {
                Material material = Material.matchMaterial(name);
                if (material != null) {
                    wanted.add(material);
                }
            });
            recounted.add(position);
            // Who owns each column, read here on the main thread: the scan attributes
            // every block to the team whose claim it stands in, a chunk being shareable.
            java.util.UUID[] owners = columnOwners(position);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                Map<java.util.UUID, Map<String, Integer>> found = scan(snapshot, minY, maxY, wanted, owners);
                try {
                    Bukkit.getScheduler().runTask(plugin, () -> counts.finishRecount(position, found));
                } catch (IllegalPluginAccessException stopping) {
                    counts.abandonRecount(position);
                }
            });
        }
    }

    /** @return the player team owning each of the chunk's 256 columns, {@code x * 16 + z}; {@code null} for none */
    private java.util.UUID[] columnOwners(ChunkPosition chunk) {
        java.util.UUID[] owners = new java.util.UUID[256];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int bx = chunk.minBlockX() + x;
                int bz = chunk.minBlockZ() + z;
                owners[x * 16 + z] = claims.getManager().getOwnerId(chunk.world(), bx, bz)
                        .filter(this::isPlayerTeam).orElse(null);
            }
        }
        return owners;
    }

    /**
     * @return how many of each wanted block the snapshot holds, by the team owning its
     *         column; {@code maxY} is exclusive (26.2 javadoc)
     */
    private static Map<java.util.UUID, Map<String, Integer>> scan(ChunkSnapshot snapshot, int minY, int maxY,
                                                                  Set<Material> wanted, java.util.UUID[] owners) {
        Map<java.util.UUID, Map<String, Integer>> found = new HashMap<>();
        if (wanted.isEmpty()) {
            return found;
        }
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                java.util.UUID owner = owners[x * 16 + z];
                if (owner == null) {
                    continue;
                }
                for (int y = minY; y < maxY; y++) {
                    Material type = snapshot.getBlockType(x, y, z);
                    if (wanted.contains(type)) {
                        found.computeIfAbsent(owner, ignored -> new HashMap<>()).merge(type.name(), 1, Integer::sum);
                    }
                }
            }
        }
        return found;
    }

    private void flushQuietly() {
        if (!countsLoaded) {
            return;
        }
        try {
            // Land unclaimed since is no longer counted, nor stored. Its blocks are
            // still there, so a chunk claimed again must be counted again - were it
            // left marked as counted this start, it would read 0 until the next one,
            // and unclaiming then reclaiming across a save would reset a team's limit.
            java.util.Set<ChunkPosition> forgotten = counts.prune(cell -> isPlayerTeam(cell.teamId())
                    && claims.getManager().getClaimsIn(cell.chunk()).stream()
                    .anyMatch(area -> area.teamId().equals(cell.teamId())));
            if (!forgotten.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> recounted.removeAll(forgotten));
            }
            counts.flush();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Saving the block counts of claims failed; they stay queued.", e);
        }
    }

    /** Human spelling of a block: {@code REDSTONE_WIRE} is "redstone wire". */
    public static String display(String material) {
        return material.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /**
     * Warns about names the server does not know. They are harmless - a key the
     * server does not have simply never matches - but a misspelt cap that silently
     * does nothing is exactly the mistake an operator would not notice until the map
     * had started.
     */
    private void checkNames(String section, LevelCaps caps, Predicate<NamespacedKey> exists) {
        for (String name : caps.keys()) {
            NamespacedKey key = NamespacedKey.fromString(name);
            if (key == null || !exists.test(key)) {
                plugin.getLogger().warning("limiters.yml: " + section + ".caps: this server has no '"
                        + name + "'; that cap will never apply.");
            }
        }
    }
}
