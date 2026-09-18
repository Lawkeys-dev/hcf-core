package com.lawkeys.hcfcore.hologram;

import com.lawkeys.hcfcore.util.ChunkPosition;
import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.database.dao.JdbcHologramStore;
import com.lawkeys.hcfcore.hologram.command.HologramCommand;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.startup.StartupBarrier;
import com.lawkeys.hcfcore.startup.StartupGate;
import com.lawkeys.hcfcore.stats.PlayerStats;
import com.lawkeys.hcfcore.stats.StatsManager;
import com.lawkeys.hcfcore.stats.StatsModule;
import com.lawkeys.hcfcore.util.Durations;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * Holograms (FEATURES.md section 14): floating text drawn by Paper's own
 * {@link TextDisplay}, with no plugin or library to install - the solution the
 * project settled on, visible to every client.
 *
 * <p><strong>The displays are never saved into the world.</strong> Each is spawned
 * non-persistent - "whether or not the entity gets persisted", the 26.2 javadoc -
 * and spawned again whenever its chunk's entities load. So the database is the only
 * copy: a crash, a plugin removed, a hologram deleted while its chunk was unloaded,
 * none of them can leave a stray text floating in somebody's base.
 *
 * <p>FEATURES.md lists the uses as still to define; the infrastructure is what was
 * validated. The one dynamic content built in is the leaderboard, the example the
 * spec gives and the one with data behind it (see {@link HologramText}).
 */
public final class HologramModule implements Listener {

    public static final String ADMIN_PERMISSION = "hcfcore.hologram.admin";

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final Plugin plugin;
    private final LangManager lang;
    private final StartupGate startup;
    private final StatsModule stats;

    private volatile boolean enabled = true;
    private volatile long refreshSeconds = 10L;
    private Holograms holograms;
    /** Main thread only: which entity currently draws which hologram. */
    private final Map<String, UUID> spawned = new HashMap<>();
    private BukkitTask refreshTask;
    private BukkitTask saveTask;

    /** Holograms drawn from code ({@link HologramSource}), redrawn every second. Main thread only. */
    private final List<HologramSource> sources = new CopyOnWriteArrayList<>();
    private final Map<String, UUID> sourced = new HashMap<>();
    private final Map<String, HologramSource.Placed> sourcedAs = new HashMap<>();
    private BukkitTask sourceTask;
    private boolean sourceWarned;

    /** @param stats may be {@code null}; leaderboard lines then show the empty-rank filler */
    public HologramModule(Plugin plugin, LangManager lang, StartupGate startup, StatsModule stats) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lang = Objects.requireNonNull(lang, "lang");
        this.startup = Objects.requireNonNull(startup, "startup");
        this.stats = stats;
    }

    public LangManager getLang() {
        return lang;
    }

    public StartupGate getStartup() {
        return startup;
    }

    public Holograms getHolograms() {
        return holograms;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void enable(DataSource dataSource, long saveIntervalSeconds) {
        reloadSettings();
        HologramStore store = dataSource == null
                ? HologramStore.NO_OP
                : new JdbcHologramStore(dataSource, message -> plugin.getLogger().info(message));
        this.holograms = new Holograms(store);

        StartupBarrier.Load load = startup.expect("holograms");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                holograms.loadAll();
                load.succeeded();
                // Spawned on the main thread, into whatever is loaded; the rest come
                // as their chunks do.
                Bukkit.getScheduler().runTask(plugin, this::spawnAllLoaded);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load holograms.", e);
                load.failed();
            }
        });
        if (saveIntervalSeconds > 0) {
            long ticks = saveIntervalSeconds * 20L;
            this.saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushQuietly, ticks, ticks);
        }
        scheduleRefresh();
        this.sourceTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshSources, 20L, 20L);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PluginCommand command = plugin.getServer().getPluginCommand("hologram");
        if (command == null) {
            plugin.getLogger().severe("The 'hologram' command is missing from plugin.yml.");
            return;
        }
        HologramCommand executor = new HologramCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    public void reloadSettings() {
        ConfigurationSection section = ConfigManager.loadFile(plugin, "holograms.yml");
        if (section != null) {
            this.enabled = section.getBoolean("enabled", true);
            this.refreshSeconds = Math.max(1L, Durations.capSeconds(section.getLong("refresh-seconds", 10L), "holograms.yml: refresh-seconds", plugin.getLogger()::warning));
        }
        // A source that failed before the reload is reported again if it still fails,
        // as Lunar's bridge does.
        sourceWarned = false;
        if (holograms != null) {
            // Switched off: nothing drawn. Switched on, or the filler text changed:
            // everything drawn again.
            scheduleRefresh();
            Bukkit.getScheduler().runTask(plugin, this::spawnAllLoaded);
        }
    }

    private void scheduleRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        long ticks = refreshSeconds * 20L;
        this.refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshDynamic, ticks, ticks);
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    private void spawnAllLoaded() {
        for (String id : List.copyOf(spawned.keySet())) {
            despawn(id);
        }
        if (!enabled) {
            return;
        }
        for (Hologram hologram : holograms.list()) {
            spawn(hologram);
        }
    }

    /** Draws a hologram again, after it changed - or takes it down, if it is gone. */
    public void redraw(String id) {
        despawn(id);
        if (enabled) {
            holograms.get(id).ifPresent(this::spawn);
        }
    }

    private void spawn(Hologram hologram) {
        World world = Bukkit.getWorld(hologram.world());
        if (world == null || !world.isChunkLoaded(hologram.chunkX(), hologram.chunkZ())) {
            return;
        }
        Location at = new Location(world, hologram.x(), hologram.y(), hologram.z());
        TextDisplay display = world.spawn(at, TextDisplay.class, text -> {
            text.setPersistent(false);
            text.setBillboard(Display.Billboard.CENTER);
            text.setLineWidth(1_000);
            text.text(LEGACY.deserialize(render(hologram)));
        });
        spawned.put(hologram.id(), display.getUniqueId());
    }

    private void despawn(String id) {
        UUID entityId = spawned.remove(id);
        Entity entity = entityId == null ? null : Bukkit.getEntity(entityId);
        if (entity != null) {
            entity.remove();
        }
    }

    private boolean isDrawn(String id) {
        UUID entityId = spawned.get(id);
        Entity entity = entityId == null ? null : Bukkit.getEntity(entityId);
        return entity != null && entity.isValid();
    }

    // ------------------------------------------------------------------
    // Holograms drawn from code
    // ------------------------------------------------------------------

    /** Registers a source; its holograms appear at the next redraw, within a second. */
    public void addSource(HologramSource source) {
        sources.add(Objects.requireNonNull(source, "source"));
    }

    /**
     * Brings the code-drawn holograms up to date: text changed in place, a hologram
     * moved or come into a loaded chunk drawn again, one no longer listed taken down.
     * Like the stored ones they are never saved into the world, so a chunk unloading
     * takes its displays with it and the next redraw puts them back once it loads.
     */
    private void refreshSources() {
        Map<String, HologramSource.Placed> wanted = new HashMap<>();
        if (enabled) {
            for (HologramSource source : sources) {
                try {
                    for (HologramSource.Placed placed : source.current()) {
                        wanted.putIfAbsent(placed.id(), placed);
                    }
                } catch (RuntimeException e) {
                    // One broken source must not take the others down; said once.
                    if (!sourceWarned) {
                        sourceWarned = true;
                        plugin.getLogger().log(Level.WARNING, "A hologram source failed (not logged again until /hcf reload).", e);
                    }
                }
            }
        }
        Set<String> gone = new HashSet<>(sourced.keySet());
        gone.removeAll(wanted.keySet());
        gone.forEach(this::despawnSourced);
        for (HologramSource.Placed placed : wanted.values()) {
            HologramSource.Placed before = sourcedAs.get(placed.id());
            UUID entityId = sourced.get(placed.id());
            if (entityId != null && placed.sameSpotAs(before)
                    && Bukkit.getEntity(entityId) instanceof TextDisplay display && display.isValid()) {
                if (!placed.lines().equals(before.lines())) {
                    display.text(LEGACY.deserialize(LangManager.colorize(String.join("\n", placed.lines()))));
                    sourcedAs.put(placed.id(), placed);
                }
                continue;
            }
            despawnSourced(placed.id());
            World world = Bukkit.getWorld(placed.world());
            if (world == null || !world.isChunkLoaded(ChunkPosition.toChunk((int) Math.floor(placed.x())),
                    ChunkPosition.toChunk((int) Math.floor(placed.z())))) {
                continue;
            }
            TextDisplay display = world.spawn(new Location(world, placed.x(), placed.y(), placed.z()), TextDisplay.class,
                    text -> {
                        text.setPersistent(false);
                        text.setBillboard(Display.Billboard.CENTER);
                        text.setLineWidth(1_000);
                        text.text(LEGACY.deserialize(LangManager.colorize(String.join("\n", placed.lines()))));
                    });
            sourced.put(placed.id(), display.getUniqueId());
            sourcedAs.put(placed.id(), placed);
        }
    }

    private void despawnSourced(String id) {
        UUID entityId = sourced.remove(id);
        sourcedAs.remove(id);
        Entity entity = entityId == null ? null : Bukkit.getEntity(entityId);
        if (entity != null) {
            entity.remove();
        }
    }

    /** Only holograms with a leaderboard line: a static text never needs redrawing. */
    private void refreshDynamic() {
        if (!enabled || holograms == null) {
            return;
        }
        for (Hologram hologram : holograms.list()) {
            if (!HologramText.isDynamic(hologram.lines())) {
                continue;
            }
            UUID entityId = spawned.get(hologram.id());
            if (entityId != null && Bukkit.getEntity(entityId) instanceof TextDisplay display) {
                display.text(LEGACY.deserialize(render(hologram)));
            }
        }
    }

    /** A chunk's entities have loaded: the holograms standing there come back. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!enabled || holograms == null) {
            return;
        }
        String world = event.getWorld().getName();
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        for (Hologram hologram : holograms.list()) {
            if (hologram.world().equals(world) && hologram.chunkX() == chunkX && hologram.chunkZ() == chunkZ
                    && !isDrawn(hologram.id())) {
                spawn(hologram);
            }
        }
    }

    /**
     * Each board is sorted once per drawing, however many of its ranks the hologram
     * shows: a top ten reads twenty placeholders, and sorting every player twenty
     * times over for one text would be the wrong cost.
     */
    private String render(Hologram hologram) {
        Map<StatsManager.Ranking, List<PlayerStats>> sorted = new java.util.EnumMap<>(StatsManager.Ranking.class);
        List<String> lines = HologramText.render(hologram.lines(), (name, rank) -> board(name, rank, sorted),
                lang.get(HologramMessages.EMPTY_RANK));
        return LangManager.colorize(String.join("\n", lines));
    }

    private Optional<HologramText.Entry> board(String name, int rank, Map<StatsManager.Ranking, List<PlayerStats>> sorted) {
        if (stats == null || stats.getManager() == null || rank < 1) {
            return Optional.empty();
        }
        StatsManager.Ranking ranking = switch (name.toLowerCase(Locale.ROOT)) {
            case "kills" -> StatsManager.Ranking.KILLS;
            case "deaths" -> StatsManager.Ranking.DEATHS;
            case "kdr" -> StatsManager.Ranking.KILL_DEATH_RATIO;
            case "killstreak" -> StatsManager.Ranking.HIGHEST_KILLSTREAK;
            case "playtime" -> StatsManager.Ranking.PLAYTIME;
            default -> null;
        };
        if (ranking == null) {
            return Optional.empty();
        }
        List<PlayerStats> top = sorted.computeIfAbsent(ranking,
                board -> stats.getManager().top(board, HologramText.MAX_RANK));
        if (top.size() < rank) {
            return Optional.empty();
        }
        PlayerStats row = top.get(rank - 1);
        long now = System.currentTimeMillis();
        String value = switch (ranking) {
            case KILLS -> String.valueOf(row.getKills());
            case DEATHS -> String.valueOf(row.getDeaths());
            case KILL_DEATH_RATIO -> String.format(Locale.ROOT, "%.2f", row.killDeathRatio());
            case HIGHEST_KILLSTREAK -> String.valueOf(row.getHighestKillstreak());
            case PLAYTIME -> Durations.format(row.playtimeSeconds(now));
        };
        return Optional.of(new HologramText.Entry(row.getName(), value));
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public void flushSoon() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::flushQuietly);
    }

    private void flushQuietly() {
        try {
            holograms.flush();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Saving holograms failed; the changes stay queued.", e);
        }
    }

    public void disable() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (sourceTask != null) {
            sourceTask.cancel();
            sourceTask = null;
        }
        for (String id : List.copyOf(spawned.keySet())) {
            despawn(id);
        }
        for (String id : new ArrayList<>(sourced.keySet())) {
            despawnSourced(id);
        }
        if (holograms == null) {
            return;
        }
        try {
            holograms.flush();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save holograms on shutdown", e);
        }
    }
}
