package com.lawkeys.hcfcore.events.conquest;

import com.lawkeys.hcfcore.util.Durations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Conquest under way: each zone's countdown and holder, and every team's points.
 *
 * <p>Memory only, like a running KOTH: a restart ends it, since resuming it would
 * mean handing points back for zones nobody is standing in any more.
 */
public final class ConquestRun {

    /** One zone's countdown. */
    public static final class ZoneState {

        private final ConquestZone zone;
        private UUID holder;
        private long remainingMillis;
        private boolean contested;

        ZoneState(ConquestZone zone, long captureMillis) {
            this.zone = zone;
            this.remainingMillis = captureMillis;
        }

        public ConquestZone zone() {
            return zone;
        }

        /** @return the team holding it - kept through a contest, as for a KOTH - or {@code null} */
        public UUID holder() {
            return holder;
        }

        public boolean isContested() {
            return contested;
        }

        /** @return whole seconds left on its countdown, rounded up */
        public long remainingSeconds() {
            return Durations.secondsLeft(remainingMillis);
        }
    }

    /** One line of the standings. */
    public record Standing(UUID teamId, int points) {
    }

    private final ConquestDefinition definition;
    private final long startedAt;
    private long lastTickAt;
    private final Map<String, ZoneState> zones = new LinkedHashMap<>();
    private final Map<UUID, Integer> points = new ConcurrentHashMap<>();

    ConquestRun(ConquestDefinition definition, long now) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.startedAt = now;
        this.lastTickAt = now;
        for (ConquestZone zone : definition.zones()) {
            zones.put(zone.id(), new ZoneState(zone, definition.captureMillis()));
        }
    }

    public ConquestDefinition getDefinition() {
        return definition;
    }

    public long getStartedAt() {
        return startedAt;
    }

    /** @return the zones, in the order the definition lists them */
    public List<ZoneState> zones() {
        return List.copyOf(zones.values());
    }

    ZoneState zone(String id) {
        return zones.get(id);
    }

    public int points(UUID teamId) {
        return points.getOrDefault(teamId, 0);
    }

    /** @return every team with points, the leader first; equal points by id, so the order is stable */
    public List<Standing> standings() {
        List<Standing> all = new ArrayList<>();
        points.forEach((team, score) -> {
            if (score > 0) {
                all.add(new Standing(team, score));
            }
        });
        all.sort(Comparator.comparingInt(Standing::points).reversed().thenComparing(Standing::teamId));
        return all;
    }

    long lastTickAt() {
        return lastTickAt;
    }

    void setLastTickAt(long at) {
        this.lastTickAt = at;
    }

    int addPoints(UUID teamId, int amount) {
        return points.merge(teamId, amount, Integer::sum);
    }

    /** @return the points left, never below zero */
    int removePoints(UUID teamId, int amount) {
        return points.compute(teamId, (team, score) -> Math.max(0, (score == null ? 0 : score) - amount));
    }

    void hold(ZoneState state, UUID holder, boolean contested) {
        state.holder = holder;
        state.contested = contested;
    }

    void countDown(ZoneState state, long elapsed) {
        state.remainingMillis -= elapsed;
    }

    void resetCountdown(ZoneState state) {
        state.remainingMillis = definition.captureMillis();
    }

    boolean isCaptured(ZoneState state) {
        return state.remainingMillis <= 0L;
    }
}
