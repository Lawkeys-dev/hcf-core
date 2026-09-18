package com.lawkeys.hcfcore.lives;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

/**
 * How many lives each player holds (FEATURES.md section 16).
 *
 * <p>Pure Java. A life is spent to come back from a deathban. Every change is a
 * read-modify-write, so it goes through {@link ConcurrentHashMap#compute}, the rule
 * {@code economy/} set for balances: two spends racing must not both succeed on one
 * life. A player never seen holds the starting amount without a row being written,
 * as a balance does. Writes use the dirty-flag order settled on this branch: the
 * mark is cleared before the row is read and put back if the save fails.
 */
public final class Lives {

    /** Why a transfer did or did not happen. */
    public enum Transfer {
        DONE,
        NOT_ENOUGH,
        INVALID_AMOUNT,
        SAME_PLAYER
    }

    private final LivesStore store;
    /** Read at each use, so /hcf reload changes it. */
    private final IntSupplier startingLives;
    private final Map<UUID, Integer> lives = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    public Lives(LivesStore store, IntSupplier startingLives) {
        this.store = Objects.requireNonNull(store, "store");
        this.startingLives = Objects.requireNonNull(startingLives, "startingLives");
    }

    public int get(UUID playerId) {
        Integer held = lives.get(playerId);
        return held != null ? held : Math.max(0, startingLives.getAsInt());
    }

    /** @return the lives held afterwards */
    public int add(UUID playerId, int amount) {
        if (amount <= 0) {
            return get(playerId);
        }
        int after = lives.merge(playerId, addCapped(get(playerId), amount),
                (current, ignored) -> addCapped(current, amount));
        dirty.add(playerId);
        return after;
    }

    public void set(UUID playerId, int amount) {
        lives.put(playerId, Math.max(0, amount));
        dirty.add(playerId);
    }

    /**
     * Takes lives, never below zero.
     *
     * @return whether the player held that many; nothing is taken otherwise
     */
    public boolean take(UUID playerId, int amount) {
        if (amount <= 0) {
            return false;
        }
        boolean[] taken = {false};
        int starting = get(playerId);
        lives.compute(playerId, (id, current) -> {
            int held = current != null ? current : starting;
            if (held < amount) {
                return current;
            }
            taken[0] = true;
            return held - amount;
        });
        if (taken[0]) {
            dirty.add(playerId);
        }
        return taken[0];
    }

    /** @return whether a life was spent */
    public boolean spendOne(UUID playerId) {
        return take(playerId, 1);
    }

    /**
     * Moves lives from one player to another. The debit is checked and done first;
     * the credit cannot fail, so there is nothing to roll back.
     */
    public Transfer transfer(UUID from, UUID to, int amount) {
        if (amount <= 0) {
            return Transfer.INVALID_AMOUNT;
        }
        if (from.equals(to)) {
            return Transfer.SAME_PLAYER;
        }
        if (!take(from, amount)) {
            return Transfer.NOT_ENOUGH;
        }
        add(to, amount);
        return Transfer.DONE;
    }

    private static int addCapped(int current, int amount) {
        long sum = (long) current + amount;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public void loadAll() throws Exception {
        store.initSchema();
        lives.clear();
        dirty.clear();
        lives.putAll(store.loadAll());
    }

    public int flush() throws Exception {
        int written = 0;
        for (UUID playerId : Set.copyOf(dirty)) {
            dirty.remove(playerId);
            try {
                store.save(playerId, get(playerId));
            } catch (Exception e) {
                dirty.add(playerId);
                throw e;
            }
            written++;
        }
        return written;
    }
}
