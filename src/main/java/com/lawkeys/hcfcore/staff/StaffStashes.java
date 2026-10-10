package com.lawkeys.hcfcore.staff;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The survival inventory each staff member is owed back, from the moment they
 * enter staff mode until it is in their hands again.
 *
 * <p><strong>Why the database, and not memory.</strong> Entering staff mode takes
 * a player's whole inventory away to make room for the toolbar. Held only in
 * memory, a crash or a {@code /stop} would destroy it - somebody's whole set,
 * gone, because they opened a moderation tool. So the stash is written as soon as
 * it is taken, survives a restart, and is handed back at their next login if it
 * could not be handed back before. Exactly the reasoning, and the shape, of
 * {@code KingStashes}: that module already paid for this lesson.
 *
 * <p>Same shape as the other managers: the cache is the source of truth, writes
 * are queued and flushed off the main thread, and a failed write stays queued for
 * the next attempt.
 */
public final class StaffStashes {

    private final StaffStashStore store;

    private final Map<UUID, byte[]> stashes = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final Set<UUID> deleted = ConcurrentHashMap.newKeySet();

    public StaffStashes(StaffStashStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /** @return whether this player is owed their items back */
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
     *         refuses to put such a player into staff mode for exactly that reason
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

    /** Loads every inventory still owed. Blocking - async task only. */
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
        Set<UUID> toDelete = Set.copyOf(deleted);
        if (!toDelete.isEmpty()) {
            Set<UUID> actuallyDelete = ConcurrentHashMap.newKeySet();
            for (UUID playerId : toDelete) {
                deleted.remove(playerId);
                if (!stashes.containsKey(playerId)) {
                    actuallyDelete.add(playerId);
                }
            }
            if (!actuallyDelete.isEmpty()) {
                try {
                    store.deleteAll(actuallyDelete);
                } catch (Exception e) {
                    deleted.addAll(actuallyDelete);
                    throw e;
                }
                written += actuallyDelete.size();
            }
        }

        Set<UUID> toSave = Set.copyOf(dirty);
        if (!toSave.isEmpty()) {
            Map<UUID, byte[]> batch = new java.util.LinkedHashMap<>();
            for (UUID playerId : toSave) {
                dirty.remove(playerId);
                byte[] contents = stashes.get(playerId);
                if (contents != null) {
                    batch.put(playerId, contents);
                }
            }
            if (!batch.isEmpty()) {
                try {
                    store.saveAll(batch);
                } catch (Exception e) {
                    dirty.addAll(batch.keySet());
                    throw e;
                }
                written += batch.size();
            }
        }
        return written;
    }
}
