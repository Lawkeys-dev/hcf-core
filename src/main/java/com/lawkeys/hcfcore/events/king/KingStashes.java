package com.lawkeys.hcfcore.events.king;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The items each King is owed back, from the crowning until they are in the
 * player's inventory again.
 *
 * <p><strong>Why the database, and not memory.</strong> A King's own inventory is
 * taken from them for the length of the reign. If the server went down with those
 * items only in memory, they would be gone - for a player who never asked to be
 * King, since they were drawn at random. So a stash is written as soon as it is taken
 * (the server layer flushes right away rather than waiting for the periodic
 * save), survives a crash, and is handed back at their next login if it could not
 * be handed back before.
 *
 * <p>Same shape as the other managers: the cache is the source of truth, writes
 * are queued and flushed off the main thread, and a failed write stays queued for
 * the next attempt.
 */
public final class KingStashes {

    private final KingStashStore store;

    private final Map<UUID, byte[]> stashes = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final Set<UUID> deleted = ConcurrentHashMap.newKeySet();

    public KingStashes(KingStashStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /** @return whether this player is owed a stash */
    public boolean has(UUID playerId) {
        return stashes.containsKey(playerId);
    }

    public Optional<byte[]> get(UUID playerId) {
        return Optional.ofNullable(stashes.get(playerId));
    }

    public int size() {
        return stashes.size();
    }

    /**
     * Keeps a player's items until they are handed back.
     *
     * @return {@code false}, changing nothing, if the player is already owed a
     *         stash: overwriting it would destroy the first one. The server layer
     *         never draws such a player as King for exactly that reason
     */
    public boolean put(UUID playerId, byte[] contents) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(contents, "contents");
        if (stashes.putIfAbsent(playerId, contents.clone()) != null) {
            return false;
        }
        deleted.remove(playerId);
        dirty.add(playerId);
        return true;
    }

    /** Forgets a stash once its items are back where they belong. */
    public void remove(UUID playerId) {
        if (stashes.remove(playerId) != null) {
            dirty.remove(playerId);
            deleted.add(playerId);
        }
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    /** Loads every stash still owed. Blocking - async task only. */
    public void loadAll() throws Exception {
        store.initSchema();
        stashes.clear();
        dirty.clear();
        deleted.clear();
        stashes.putAll(store.loadAll());
    }

    /**
     * Writes new stashes and deletes handed-back ones. Blocking - async task only.
     * A write that fails stays queued, so the next flush tries again.
     *
     * @return the number of rows written or deleted
     */
    public int flush() throws Exception {
        int written = 0;
        // Each mark is taken off before its row is read, and put back if the write
        // fails: a change made on the main thread during the write marks the row
        // again, and the next flush writes it.
        for (UUID playerId : Set.copyOf(deleted)) {
            deleted.remove(playerId);
            // Stashed again since it was handed back: the save below replaces the row.
            if (stashes.containsKey(playerId)) {
                continue;
            }
            try {
                store.delete(playerId);
            } catch (Exception e) {
                deleted.add(playerId);
                throw e;
            }
            written++;
        }
        for (UUID playerId : Set.copyOf(dirty)) {
            dirty.remove(playerId);
            byte[] contents = stashes.get(playerId);
            if (contents == null) {
                continue;
            }
            try {
                store.save(playerId, contents);
            } catch (Exception e) {
                dirty.add(playerId);
                throw e;
            }
            written++;
        }
        return written;
    }
}
