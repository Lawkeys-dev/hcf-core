package com.lawkeys.hcfcore.stats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Kills, deaths, killstreaks and playtime for every player the server has seen.
 *
 * <p>Pure Java, no Bukkit. The cache is the source of truth and writes are queued
 * behind a dirty flag, as everywhere else - and unlike the death archive, this one
 * <em>is</em> cached: it is live state read on the hot path (every chat line, every
 * scoreboard tick, every leaderboard) and a row is a handful of numbers rather
 * than an inventory.
 *
 * <p><strong>A killstreak is stored rather than kept in memory.</strong> That is a
 * choice, not an oversight: a streak that reset on every restart would be farmed by
 * waiting for one, and the rewards hung off it (see {@code killstreak/}) would be
 * worth less the more reliable the server was.
 */
public final class StatsManager {

    /** What a leaderboard can be sorted by. */
    public enum Ranking {
        KILLS,
        DEATHS,
        KILL_DEATH_RATIO,
        HIGHEST_KILLSTREAK,
        PLAYTIME
    }

    private final StatsStore store;
    private final LongSupplier clock;

    private final Map<UUID, PlayerStats> stats = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    public StatsManager(StatsStore store) {
        this(store, System::currentTimeMillis);
    }

    /** @param clock injectable so the tests can move time instead of waiting */
    public StatsManager(StatsStore store, LongSupplier clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * @return this player's stats, creating an empty row if they have none
     *
     * <p>Creating on read is what lets every caller treat a new player like any
     * other. The row is only written once something actually changes it.
     */
    public PlayerStats get(UUID playerId, String name) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerStats existing = stats.computeIfAbsent(playerId, id -> new PlayerStats(id, name));
        existing.setName(name);
        return existing;
    }

    /** @return this player's stats if the server has ever seen them */
    public Optional<PlayerStats> find(UUID playerId) {
        return Optional.ofNullable(stats.get(playerId));
    }

    /** @return the stats of the player with this name, if the server has seen them */
    public Optional<PlayerStats> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        for (PlayerStats candidate : stats.values()) {
            if (candidate.getName().equalsIgnoreCase(name)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public int size() {
        return stats.size();
    }

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    /**
     * Records a kill.
     *
     * @return the killer's new streak, so the caller can hang rewards off it
     */
    public int recordKill(UUID killerId, String killerName) {
        PlayerStats killer = get(killerId, killerName);
        int streak = killer.addKill();
        dirty.add(killerId);
        return streak;
    }

    /** Records a death, which ends any streak. */
    public void recordDeath(UUID victimId, String victimName) {
        get(victimId, victimName).addDeath();
        dirty.add(victimId);
    }

    public void beginSession(UUID playerId, String name) {
        get(playerId, name).beginSession(clock.getAsLong());
        dirty.add(playerId);
    }

    public void endSession(UUID playerId) {
        PlayerStats existing = stats.get(playerId);
        if (existing != null && existing.endSession(clock.getAsLong()) >= 0) {
            dirty.add(playerId);
        }
    }

    /**
     * Ends every session in progress.
     *
     * <p>Called on shutdown, before the last flush: without it the time everybody
     * online had played this session would be lost, since it is only added to the
     * stored figure when a session ends.
     */
    public void endAllSessions() {
        for (UUID playerId : Set.copyOf(stats.keySet())) {
            endSession(playerId);
        }
    }

    // ------------------------------------------------------------------
    // Leaderboards
    // ------------------------------------------------------------------

    /**
     * @param limit how many rows to return
     * @return the top players by this ranking, best first
     *
     * <p>Sorted on demand from the cache rather than kept in a sorted structure:
     * a leaderboard is read far less often than a kill is recorded, so the cost
     * belongs on the read.
     */
    public List<PlayerStats> top(Ranking ranking, int limit) {
        long now = clock.getAsLong();
        Comparator<PlayerStats> comparator = switch (ranking) {
            case KILLS -> Comparator.comparingInt(PlayerStats::getKills);
            case DEATHS -> Comparator.comparingInt(PlayerStats::getDeaths);
            case KILL_DEATH_RATIO -> Comparator.comparingDouble(PlayerStats::killDeathRatio);
            case HIGHEST_KILLSTREAK -> Comparator.comparingInt(PlayerStats::getHighestKillstreak);
            case PLAYTIME -> Comparator.comparingLong(s -> s.playtimeSeconds(now));
        };
        List<PlayerStats> ranked = new ArrayList<>(stats.values());
        // Name as the tie-break, so equal scores come back in a stable order rather
        // than in whatever order the map happened to hand them over.
        ranked.sort(comparator.reversed()
                .thenComparing(PlayerStats::getName, String.CASE_INSENSITIVE_ORDER));
        return ranked.size() <= limit ? List.copyOf(ranked) : List.copyOf(ranked.subList(0, Math.max(0, limit)));
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    /** Loads every player's stats. Blocking - async task only. */
    public void loadAll() throws Exception {
        store.initSchema();
        stats.clear();
        dirty.clear();
        for (PlayerStats loaded : store.loadAll()) {
            stats.put(loaded.getPlayerId(), loaded);
        }
    }

    /**
     * Writes changed rows. Blocking - async task only.
     *
     * @return the number of rows written
     */
    public int flush() throws Exception {
        int written = 0;
        for (UUID playerId : Set.copyOf(dirty)) {
            // Off before the row is read, back on if the write fails: a kill recorded
            // on the main thread during the write marks the row again.
            dirty.remove(playerId);
            PlayerStats row = stats.get(playerId);
            if (row == null) {
                continue;
            }
            try {
                store.save(row);
            } catch (Exception e) {
                dirty.add(playerId);
                throw e;
            }
            written++;
        }
        return written;
    }

    /** Marks a player's row as needing a write, for callers that mutate through {@link #get}. */
    public void markDirty(UUID playerId) {
        dirty.add(playerId);
    }
}
