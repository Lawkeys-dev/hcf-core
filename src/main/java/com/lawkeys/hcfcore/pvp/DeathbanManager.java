package com.lawkeys.hcfcore.pvp;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Keeps players out of the server for a while after they die (FEATURES.md
 * section 4).
 *
 * <p>The cache is the source of truth at runtime, as everywhere else. It holds
 * every <em>active</em> ban, loaded at startup and kept up to date as players die,
 * which is what lets a login check be answered from memory - logins cannot wait on
 * a database round trip.
 *
 * <p>Pure Java, no server API.
 */
public final class DeathbanManager {

    private final Supplier<PvpSettings> settings;
    private final DeathbanStore store;
    private final LongSupplier clock;

    private final Map<UUID, Deathban> bans = new ConcurrentHashMap<>();
    private final Map<UUID, Deathban> dirty = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> pendingDeletions = ConcurrentHashMap.newKeySet();

    public DeathbanManager(Supplier<PvpSettings> settings, DeathbanStore store) {
        this(settings, store, System::currentTimeMillis);
    }

    public DeathbanManager(Supplier<PvpSettings> settings, DeathbanStore store, LongSupplier clock) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private PvpSettings config() {
        return settings.get();
    }

    /**
     * Bans a player for {@code seconds}.
     *
     * @return the ban, or empty when deathbans are switched off
     */
    public Optional<Deathban> apply(UUID player, long seconds, String reason) {
        Objects.requireNonNull(player, "player");
        PvpSettings config = config();
        if (!config.enabled() || !config.deathban().enabled() || seconds <= 0) {
            return Optional.empty();
        }
        return Optional.of(store(new Deathban(player, clock.getAsLong() + seconds * 1000L, reason)));
    }

    /**
     * Bans until the map ends (EOTW): no duration, so no expiry is ever reached,
     * and only staff lift it. Its own method rather than a very large duration,
     * which would overflow the expiry instant.
     *
     * @return the ban, or empty when deathbans are switched off
     */
    public Optional<Deathban> applyUntilMapEnd(UUID player, String reason) {
        Objects.requireNonNull(player, "player");
        PvpSettings config = config();
        if (!config.enabled() || !config.deathban().enabled()) {
            return Optional.empty();
        }
        return Optional.of(store(new Deathban(player, Deathban.UNTIL_MAP_END, reason)));
    }

    private Deathban store(Deathban ban) {
        bans.put(ban.playerId(), ban);
        dirty.put(ban.playerId(), ban);
        pendingDeletions.remove(ban.playerId());
        return ban;
    }

    /** @return the player's active ban, or empty; an expired ban is dropped on the way out. */
    public Optional<Deathban> getActiveBan(UUID player) {
        if (player == null || !config().enabled() || !config().deathban().enabled()) {
            return Optional.empty();
        }
        Deathban ban = bans.get(player);
        if (ban == null) {
            return Optional.empty();
        }
        if (!ban.isActiveAt(clock.getAsLong())) {
            lift(player);
            return Optional.empty();
        }
        return Optional.of(ban);
    }

    public boolean isBanned(UUID player) {
        return getActiveBan(player).isPresent();
    }

    /** @return {@code true} if there was a ban to lift. */
    public boolean lift(UUID player) {
        Deathban removed = bans.remove(player);
        dirty.remove(player);
        if (removed != null) {
            pendingDeletions.add(player);
            return true;
        }
        return false;
    }

    /**
     * @return who is banned now for a time - what a life can lift, and so what
     *         {@code /revive} offers to complete. A ban until the map ends is left out
     */
    public java.util.List<UUID> getRevivable() {
        long now = clock.getAsLong();
        if (!config().enabled() || !config().deathban().enabled()) {
            return java.util.List.of();
        }
        return bans.values().stream()
                .filter(ban -> ban.isActiveAt(now) && !ban.isUntilMapEnd())
                .map(Deathban::playerId)
                .toList();
    }

    public int getActiveBanCount() {
        long now = clock.getAsLong();
        int count = 0;
        for (Deathban ban : bans.values()) {
            if (ban.isActiveAt(now)) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    /** Loads the active bans. Blocking - async task only. */
    public void loadAll() throws Exception {
        store.initSchema();
        long now = clock.getAsLong();
        store.purgeExpired(now);
        bans.clear();
        dirty.clear();
        pendingDeletions.clear();
        for (Deathban ban : store.loadActive(now)) {
            bans.put(ban.playerId(), ban);
        }
    }

    /**
     * Writes new and lifted bans. Blocking - async task only.
     *
     * @return the number of rows touched
     */
    public int flush() throws Exception {
        int written = 0;
        for (UUID lifted : java.util.Set.copyOf(pendingDeletions)) {
            store.delete(lifted);
            pendingDeletions.remove(lifted);
            written++;
        }
        for (Map.Entry<UUID, Deathban> entry : Map.copyOf(dirty).entrySet()) {
            dirty.remove(entry.getKey());
            try {
                store.save(entry.getValue());
                written++;
            } catch (Exception e) {
                dirty.put(entry.getKey(), entry.getValue());
                throw e;
            }
        }
        return written;
    }

    /** Drops expired rows from the database. Blocking - async task only. */
    public int purgeExpired() throws Exception {
        long now = clock.getAsLong();
        bans.values().removeIf(ban -> !ban.isActiveAt(now));
        return store.purgeExpired(now);
    }
}
