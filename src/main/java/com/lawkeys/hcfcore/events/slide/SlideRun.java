package com.lawkeys.hcfcore.events.slide;

import com.lawkeys.hcfcore.events.Standing;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Slide under way: every team's points and what has already been announced.
 *
 * <p>{@link #points} is read by the scoreboard (the live top 3) while the main
 * thread awards points, hence the concurrent map - like {@code ConquestRun}.
 */
public final class SlideRun {

    private final SlideDefinition definition;
    private final long startedAt;
    private long lastTickAt;
    /** Milliseconds towards the next interval that have not yet made a whole one. */
    private long leftoverMillis;
    private final Map<UUID, Integer> points = new ConcurrentHashMap<>();
    /** Points marks already announced, per team. */
    private final Map<UUID, Set<Integer>> announcedMarks = new ConcurrentHashMap<>();

    SlideRun(SlideDefinition definition, long now) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.startedAt = now;
        this.lastTickAt = now;
    }

    public SlideDefinition getDefinition() {
        return definition;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public int points(UUID teamId) {
        return points.getOrDefault(teamId, 0);
    }

    /** @return every team with points, the leader first; ties broken by id */
    public List<Standing> standings() {
        return Standing.rank(points);
    }

    /** @return the leading {@code n} teams (or fewer), for the live scoreboard */
    public List<Standing> top(int n) {
        List<Standing> all = standings();
        return all.size() > n ? List.copyOf(all.subList(0, n)) : all;
    }

    long lastTickAt() {
        return lastTickAt;
    }

    void setLastTickAt(long at) {
        this.lastTickAt = at;
    }

    /**
     * Adds {@code elapsedMillis} to the leftover from earlier ticks, and returns
     * how many whole intervals that makes - a late or irregular tick catches up
     * on more than one, and whatever does not make a whole interval is kept for
     * next time.
     */
    long consumeIntervals(long elapsedMillis, long intervalMillis) {
        long total = leftoverMillis + Math.max(0L, elapsedMillis);
        long intervals = total / intervalMillis;
        leftoverMillis = total % intervalMillis;
        return intervals;
    }

    int addPoints(UUID teamId, int amount) {
        return points.merge(teamId, amount, Integer::sum);
    }

    /** @return the points left, never below zero */
    int removePoints(UUID teamId, int amount) {
        return points.compute(teamId, (team, score) -> Math.max(0, (score == null ? 0 : score) - amount));
    }

    /** @return whether this points mark for this team had not yet been announced (and is now) */
    boolean announceMilestone(UUID teamId, int mark) {
        return announcedMarks.computeIfAbsent(teamId, id -> ConcurrentHashMap.newKeySet()).add(mark);
    }
}
