package com.lawkeys.hcfcore.pvpclass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Who is in which class, and the energy of those whose class has some. Pure Java,
 * driven by what the server layer sees players wear.
 *
 * <p>A class turns on once its whole armour set has been worn for the warmup, and
 * off the moment one piece comes off - or when the set stops being a class, after
 * a reload. Changing set means going through the warmup again. Nothing here is
 * stored: a restart, a death or a logout puts everybody back to no class, and the
 * next look at their armour starts the warmup over.
 */
public final class ClassManager {

    /** What an update changed, for the server layer to tell the player. */
    public enum Kind {
        /** A set is complete: its class will turn on after the warmup. */
        WARMUP_STARTED,
        /** The warmup is over: the class is on. */
        ACTIVATED,
        /** The class is off: a piece came off, or the set changed. */
        DEACTIVATED,
        /** The warmup is over but the team already holds as many of this class as allowed. */
        REFUSED_TEAM_LIMIT
    }

    /** One change, with the class it is about. */
    public record Change(Kind kind, PvpClass pvpClass) {
    }

    /** Whether a player's team has room for one more of a class - see {@link PvpClass#maxPerTeam()}. */
    @FunctionalInterface
    public interface TeamRoom {
        boolean hasRoomFor(PvpClass pvpClass);
    }

    private static final class State {
        /** The class the player is in, or {@code null}. */
        PvpClass active;
        /** The class being warmed up, or {@code null}. */
        PvpClass pending;
        long pendingSince;
        /** A set refused at the end of its warmup: not tried again until it comes off. */
        PvpClass refused;
        double energy;
        long energyAt;
    }

    private final Supplier<ClassSettings> settings;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public ClassManager(Supplier<ClassSettings> settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Brings a player up to date with the set they wear.
     *
     * @param wearing the class whose whole set the player wears - and may use - or
     *                {@code null} for none
     * @param room    asked once, when a warmup ends
     * @return what changed, in order: a switch from one class to another is a
     *         {@link Kind#DEACTIVATED} then a {@link Kind#WARMUP_STARTED}
     */
    public List<Change> update(UUID playerId, PvpClass wearing, long now, TeamRoom room) {
        Objects.requireNonNull(playerId, "playerId");
        State state = states.computeIfAbsent(playerId, id -> new State());
        List<Change> changes = new ArrayList<>();
        synchronized (state) {
            if (state.active != null && !sameClass(state.active, wearing)) {
                changes.add(new Change(Kind.DEACTIVATED, state.active));
                state.active = null;
                state.energy = 0;
            } else if (state.active != null) {
                // Still in it; a reload may have redefined it, so keep the latest definition.
                state.active = wearing;
                return changes;
            }
            if (!sameClass(state.refused, wearing)) {
                state.refused = null;
            }
            if (wearing == null || state.refused != null) {
                state.pending = null;
                return changes;
            }
            if (!sameClass(state.pending, wearing)) {
                state.pending = wearing;
                state.pendingSince = now;
                changes.add(new Change(Kind.WARMUP_STARTED, wearing));
            }
            if (now - state.pendingSince >= settings.get().warmupOf(wearing) * 1000L) {
                state.pending = null;
                if (!room.hasRoomFor(wearing)) {
                    state.refused = wearing;
                    changes.add(new Change(Kind.REFUSED_TEAM_LIMIT, wearing));
                } else {
                    state.active = wearing;
                    state.energy = 0;
                    state.energyAt = now;
                    changes.add(new Change(Kind.ACTIVATED, wearing));
                }
            }
        }
        return changes;
    }

    private static boolean sameClass(PvpClass a, PvpClass b) {
        return a != null && b != null && a.id().equals(b.id());
    }

    public Optional<PvpClass> active(UUID playerId) {
        State state = states.get(playerId);
        return state == null ? Optional.empty() : Optional.ofNullable(state.active);
    }

    /** @return the class being warmed up, if any */
    public Optional<PvpClass> pending(UUID playerId) {
        State state = states.get(playerId);
        return state == null ? Optional.empty() : Optional.ofNullable(state.pending);
    }

    /** @return whole seconds left of the warmup, rounded up, or {@code 0} when none runs */
    public long warmupRemaining(UUID playerId, long now) {
        State state = states.get(playerId);
        if (state == null || state.pending == null) {
            return 0;
        }
        long left = state.pendingSince + settings.get().warmupOf(state.pending) * 1000L - now;
        return left <= 0 ? 0 : (left + 999) / 1000;
    }

    /** @return how many of these players are in this class right now */
    public int countIn(Collection<UUID> players, String classId) {
        int count = 0;
        for (UUID player : players) {
            if (active(player).map(pvpClass -> pvpClass.id().equals(classId)).orElse(false)) {
                count++;
            }
        }
        return count;
    }

    /** @return the player's energy now, or {@code 0} outside a class with energy */
    public double energy(UUID playerId, long now) {
        State state = states.get(playerId);
        if (state == null) {
            return 0;
        }
        synchronized (state) {
            if (state.active == null || !state.active.hasEnergy()) {
                return 0;
            }
            state.energy = state.active.energy().after(state.energy, now - state.energyAt);
            state.energyAt = now;
            return state.energy;
        }
    }

    /**
     * Spends energy, if the player has that much.
     *
     * @return whether it was spent; nothing changes otherwise
     */
    public boolean spend(UUID playerId, int cost, long now) {
        if (cost <= 0) {
            return true;
        }
        State state = states.get(playerId);
        if (state == null) {
            return false;
        }
        synchronized (state) {
            double current = energy(playerId, now);
            if (current < cost) {
                return false;
            }
            state.energy = current - cost;
            return true;
        }
    }

    /** Back to no class: death, logout. */
    public Optional<PvpClass> forget(UUID playerId) {
        State state = states.remove(playerId);
        return state == null ? Optional.empty() : Optional.ofNullable(state.active);
    }

    public void clear() {
        states.clear();
    }
}
