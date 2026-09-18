package com.lawkeys.hcfcore.staff;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The moderation bans in force, kept in memory and written behind.
 *
 * <p>Same shape as the other managers: the cache is the source of truth, writes are
 * queued and flushed off the main thread, and a failed write stays queued. Small
 * enough to hold entirely - one row per banned player, and a server with more
 * banned players than it can cache has other problems.
 *
 * <p>Unlike {@link StaffStashes}, nothing here is ever silently dropped: a ban
 * leaves only when staff lift it.
 */
public final class StaffBans {

    private final StaffBanStore store;

    private final Map<UUID, StaffBan> bans = new ConcurrentHashMap<>();
    private final Map<UUID, StaffBan> dirty = new ConcurrentHashMap<>();
    private final Set<UUID> deleted = ConcurrentHashMap.newKeySet();

    public StaffBans(StaffBanStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public boolean isBanned(UUID playerId) {
        return bans.containsKey(playerId);
    }

    public Optional<StaffBan> get(UUID playerId) {
        return Optional.ofNullable(bans.get(playerId));
    }

    public int size() {
        return bans.size();
    }

    /**
     * Records a ban.
     *
     * @return {@code false}, changing nothing, if they are already banned: the first
     *         reason is the one that explains why they are out, and overwriting it
     *         with a later one would lose it
     */
    public boolean ban(UUID playerId, String reason, String bannedBy, long now) {
        Objects.requireNonNull(playerId, "playerId");
        StaffBan ban = new StaffBan(playerId, reason, bannedBy, now);
        if (bans.putIfAbsent(playerId, ban) != null) {
            return false;
        }
        deleted.remove(playerId);
        dirty.put(playerId, ban);
        return true;
    }

    /** @return {@code true} if they were banned and now are not */
    public boolean lift(UUID playerId) {
        if (bans.remove(playerId) == null) {
            return false;
        }
        dirty.remove(playerId);
        deleted.add(playerId);
        return true;
    }

    public Set<UUID> bannedPlayers() {
        return Set.copyOf(bans.keySet());
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    /** Loads every ban in force. Blocking - async task only. */
    public void loadAll() throws Exception {
        store.initSchema();
        bans.clear();
        dirty.clear();
        deleted.clear();
        bans.putAll(store.loadAll());
    }

    /**
     * Writes new bans and deletes lifted ones. Blocking - async task only.
     *
     * @return the number of rows written or deleted
     */
    public int flush() throws Exception {
        int written = 0;
        // Each mark is taken off before its row is written, and put back if the write
        // fails: a ban changed on the main thread during the write marks the row
        // again, and the next flush writes it.
        for (UUID playerId : Set.copyOf(deleted)) {
            deleted.remove(playerId);
            // Banned again since it was lifted: the save below replaces the row.
            if (bans.containsKey(playerId)) {
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
        for (Map.Entry<UUID, StaffBan> entry : Map.copyOf(dirty).entrySet()) {
            if (!dirty.remove(entry.getKey(), entry.getValue())) {
                continue;
            }
            try {
                store.save(entry.getValue());
            } catch (Exception e) {
                // Only if it is still the ban in force: one lifted during the write
                // is already queued for deletion.
                if (bans.get(entry.getKey()) == entry.getValue()) {
                    dirty.putIfAbsent(entry.getKey(), entry.getValue());
                }
                throw e;
            }
            written++;
        }
        return written;
    }
}
