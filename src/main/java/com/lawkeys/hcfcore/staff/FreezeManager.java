package com.lawkeys.hcfcore.staff;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is currently held for a check, and who held them.
 *
 * <p><strong>Memory only, like a combat tag.</strong> A freeze is minutes of a
 * conversation, not a state worth restoring: a restart lets everybody go. What
 * does outlive a restart is the consequence of running away from one, which is a
 * {@link StaffBans} row rather than anything kept here.
 *
 * <p>Pure Java, no Bukkit, so the rules are testable.
 */
public final class FreezeManager {

    /**
     * A held player, and who is holding them.
     *
     * @param frozenBy the staff member's name, for messages - staff come and go, and
     *                 a name already rendered is what the announcement needs
     */
    public record Freeze(UUID playerId, String frozenBy, long since) {

        public Freeze {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(frozenBy, "frozenBy");
        }
    }

    private final Map<UUID, Freeze> frozen = new ConcurrentHashMap<>();

    public boolean isFrozen(UUID playerId) {
        return frozen.containsKey(playerId);
    }

    public Optional<Freeze> get(UUID playerId) {
        return Optional.ofNullable(frozen.get(playerId));
    }

    /**
     * Holds a player.
     *
     * @return {@code false}, changing nothing, if somebody else already holds them -
     *         so a second staff member cannot quietly take over a check in progress,
     *         and the first one's name stays on the announcement
     */
    public boolean freeze(UUID playerId, String staffName, long now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(staffName, "staffName");
        return frozen.putIfAbsent(playerId, new Freeze(playerId, staffName, now)) == null;
    }

    /** @return {@code true} if they were held and now are not */
    public boolean unfreeze(UUID playerId) {
        return frozen.remove(playerId) != null;
    }

    public Set<UUID> frozenPlayers() {
        return Set.copyOf(frozen.keySet());
    }

    public int size() {
        return frozen.size();
    }

    /** Lets everybody go. Used on shutdown, and when the module is reloaded off. */
    public void clearAll() {
        frozen.clear();
    }
}
