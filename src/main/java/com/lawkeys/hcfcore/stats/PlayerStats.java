package com.lawkeys.hcfcore.stats;

import java.util.Objects;
import java.util.UUID;

/**
 * One player's running totals.
 *
 * <p>Mutable, unlike most models here, and deliberately so: these change on every
 * kill and every tick of playtime, and a record copied on each change would churn
 * through allocations on the hottest path in the plugin. Every mutation goes
 * through {@link StatsManager}, which is what marks the row dirty.
 *
 * <p><strong>Playtime is stored, not derived.</strong> A session's length is added
 * when the session ends, so the stored figure is always whole sessions; the
 * current one is added on the way out by {@link #playtimeSeconds(long)}. Deriving
 * it from a first-join date would count the years somebody was not playing.
 */
public final class PlayerStats {

    private final UUID playerId;
    private String name;

    private int kills;
    private int deaths;
    private int killstreak;
    private int highestKillstreak;

    private long playtimeSeconds;
    private long firstSeen;
    private long lastSeen;

    /** The instant the current session began, or {@code 0} when they are offline. */
    private long sessionStart;

    public PlayerStats(UUID playerId, String name) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.name = name == null ? "" : name;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getName() {
        return name;
    }

    void setName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
    }

    public int getKills() {
        return kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public int getKillstreak() {
        return killstreak;
    }

    public int getHighestKillstreak() {
        return highestKillstreak;
    }

    public long getFirstSeen() {
        return firstSeen;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    /**
     * @param now the current instant
     * @return total playtime, the session in progress included
     */
    public long playtimeSeconds(long now) {
        if (sessionStart <= 0 || now <= sessionStart) {
            return playtimeSeconds;
        }
        return playtimeSeconds + (now - sessionStart) / 1000L;
    }

    /**
     * @return kills divided by deaths, counting a death-free player as their kill
     *         count rather than as infinity - which is what every scoreboard in the
     *         genre shows, and what a leaderboard can sort
     */
    public double killDeathRatio() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }

    // ------------------------------------------------------------------
    // Mutation - package-private, so StatsManager is the only way in
    // ------------------------------------------------------------------

    int addKill() {
        kills++;
        killstreak++;
        if (killstreak > highestKillstreak) {
            highestKillstreak = killstreak;
        }
        return killstreak;
    }

    void addDeath() {
        deaths++;
        killstreak = 0;
    }

    void beginSession(long now) {
        this.sessionStart = now;
        this.lastSeen = now;
        if (firstSeen == 0L) {
            this.firstSeen = now;
        }
    }

    /** @return the seconds this session added */
    long endSession(long now) {
        if (sessionStart <= 0) {
            return 0L;
        }
        long seconds = Math.max(0L, (now - sessionStart) / 1000L);
        playtimeSeconds += seconds;
        sessionStart = 0L;
        lastSeen = now;
        return seconds;
    }

    /** Rebuilds a stored row. Used by the store only. */
    public static PlayerStats restore(UUID playerId, String name, int kills, int deaths,
                                      int killstreak, int highestKillstreak,
                                      long playtimeSeconds, long firstSeen, long lastSeen) {
        PlayerStats stats = new PlayerStats(playerId, name);
        stats.kills = kills;
        stats.deaths = deaths;
        stats.killstreak = killstreak;
        stats.highestKillstreak = highestKillstreak;
        stats.playtimeSeconds = playtimeSeconds;
        stats.firstSeen = firstSeen;
        stats.lastSeen = lastSeen;
        return stats;
    }

    /** @return stored playtime without the session in progress, for the store */
    public long storedPlaytimeSeconds() {
        return playtimeSeconds;
    }
}
