package com.lawkeys.hcfcore.warmup;

import com.lawkeys.hcfcore.util.Durations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Countdowns that any damage or movement cancels: {@code /spawn}, {@code /logout},
 * {@code /team hq}, {@code /team base} and {@code /team stuck}.
 *
 * <p>They exist for the same reason - none may be a way out of a fight, or out of a
 * base somebody has trapped you in - so they work the same way and share one of
 * these, which is also what stops two from running at once. A warmup is remembered
 * with the block the player stood on when it started, since "did they move" means
 * "did they leave that block", not "did they turn their head".
 *
 * <p>Pure Java; the caller supplies the clock and the position.
 */
public final class Warmups {

    /** @param kind what the countdown is for, so two cannot be confused */
    public record Warmup(UUID playerId, String kind, long finishesAt,
                         String world, int x, int y, int z) {

        public Warmup {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(world, "world");
        }

        public boolean hasMoved(String otherWorld, int otherX, int otherY, int otherZ) {
            return !world.equals(otherWorld) || x != otherX || y != otherY || z != otherZ;
        }

        public boolean isDue(long now) {
            return now >= finishesAt;
        }

        public long remainingSeconds(long now) {
            long remaining = finishesAt - now;
            return Durations.secondsLeft(remaining);
        }
    }

    private final Map<UUID, Warmup> active = new ConcurrentHashMap<>();

    /**
     * Starts a countdown.
     *
     * @return {@code false}, changing nothing, if one of any kind is already running -
     *         so a player cannot stack a /spawn on top of a /logout and arrive
     *         somewhere while disconnecting, or a /team hq on top of a /team stuck
     */
    public boolean start(UUID playerId, String kind, long finishesAt,
                         String world, int x, int y, int z) {
        return active.putIfAbsent(playerId,
                new Warmup(playerId, kind, finishesAt, world, x, y, z)) == null;
    }

    public Optional<Warmup> of(UUID playerId) {
        return Optional.ofNullable(active.get(playerId));
    }

    public boolean isWarmingUp(UUID playerId) {
        return active.containsKey(playerId);
    }

    /** @return the cancelled countdown, if there was one */
    public Optional<Warmup> cancel(UUID playerId) {
        return Optional.ofNullable(active.remove(playerId));
    }

    /** @return every countdown that is now due, removing them */
    public List<Warmup> pollDue(long now) {
        List<Warmup> due = new ArrayList<>();
        for (Warmup warmup : Map.copyOf(active).values()) {
            if (warmup.isDue(now) && active.remove(warmup.playerId()) != null) {
                due.add(warmup);
            }
        }
        return due;
    }

    public int size() {
        return active.size();
    }

    public void clearAll() {
        active.clear();
    }
}
