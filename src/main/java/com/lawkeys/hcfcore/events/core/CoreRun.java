package com.lawkeys.hcfcore.events.core;

import com.lawkeys.hcfcore.events.Standing;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A DTC or Last Break under way: the core's remaining health, every team's own
 * break count, and what has already been announced.
 *
 * <p>{@link #breaks} is read by the scoreboard from another thread while the
 * main thread records breaks, hence the concurrent map - like
 * {@code ConquestRun#points}.
 */
public final class CoreRun {

    private final CoreEventDefinition definition;
    private final long startedAt;
    /** The core's common health - meaningful for {@link CoreWinRule#MOST_BREAKS} and {@link CoreWinRule#LAST_BREAK}. */
    private volatile int health;
    /** Each team's own count, read by the scoreboard. */
    private final Map<UUID, Integer> breaks = new ConcurrentHashMap<>();
    /**
     * The sequence number of the break that brought a team to its current
     * count - the smaller, the earlier. Used only to break a SHARED tie: the
     * team that reached the top count first.
     */
    private final Map<UUID, Long> attainedAt = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private volatile UUID lastBreakerTeamId;
    /** Remaining-health marks already announced (SHARED / Last Break). */
    private final Set<Integer> announcedRemaining = ConcurrentHashMap.newKeySet();
    /** Remaining-to-target marks already announced, per team (PER_TEAM). */
    private final Map<UUID, Set<Integer>> announcedRemainingPerTeam = new ConcurrentHashMap<>();

    CoreRun(CoreEventDefinition definition, long now) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.startedAt = now;
        this.health = definition.breaks();
    }

    public CoreEventDefinition getDefinition() {
        return definition;
    }

    public long getStartedAt() {
        return startedAt;
    }

    /** @return the core's remaining common health; only meaningful outside PER_TEAM */
    public int health() {
        return health;
    }

    public int breaksOf(UUID teamId) {
        return breaks.getOrDefault(teamId, 0);
    }

    public UUID lastBreakerTeamId() {
        return lastBreakerTeamId;
    }

    /** @return every team with at least one break, the leader first */
    public List<Standing> standings() {
        return Standing.rank(breaks);
    }

    /**
     * Records one break by this team: its own count goes up, the common health
     * goes down by one (never below zero), and this team becomes the last one to
     * have broken the core.
     *
     * @return the team's own count after this break
     */
    int recordBreak(UUID teamId) {
        long seq = sequence.incrementAndGet();
        int after = breaks.merge(teamId, 1, Integer::sum);
        attainedAt.put(teamId, seq);
        lastBreakerTeamId = teamId;
        health = Math.max(0, health - 1);
        return after;
    }

    /**
     * @return among the teams tied for the most breaks, whichever reached that
     *         count first - empty if nobody has broken the core yet
     */
    Optional<UUID> leaderBreakingTies() {
        int max = 0;
        for (int value : breaks.values()) {
            max = Math.max(max, value);
        }
        if (max <= 0) {
            return Optional.empty();
        }
        UUID winner = null;
        long best = Long.MAX_VALUE;
        for (Map.Entry<UUID, Integer> entry : breaks.entrySet()) {
            if (entry.getValue() == max) {
                long seq = attainedAt.getOrDefault(entry.getKey(), Long.MAX_VALUE);
                if (seq < best) {
                    best = seq;
                    winner = entry.getKey();
                }
            }
        }
        return Optional.ofNullable(winner);
    }

    /** @return whether this remaining-health mark had not yet been announced (and is now) */
    boolean announceRemaining(int mark) {
        return announcedRemaining.add(mark);
    }

    /** @return whether this remaining-to-target mark for this team had not yet been announced (and is now) */
    boolean announceRemainingForTeam(UUID teamId, int mark) {
        return announcedRemainingPerTeam.computeIfAbsent(teamId, id -> ConcurrentHashMap.newKeySet()).add(mark);
    }
}
