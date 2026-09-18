package com.lawkeys.hcfcore.staff.strike;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Strikes against teams, and how many of them still count.
 *
 * <p>Pure Java. An expired strike is kept rather than deleted - the record of what a
 * team did is the point of a strike system. It simply stops counting towards the
 * ladder. A <em>pardoned</em> strike is deleted: pardoning says it should never have
 * been issued. A strike outlives its team: a team disbanded by its third strike keeps
 * its record, found by the name it had.
 *
 * <p>Every strike is held in memory. They are issued by hand, one at a time, so even
 * a busy server accumulates a few hundred over a map.
 */
public final class StrikeManager {

    private final StrikeStore store;
    private final LongSupplier clock;

    private final Map<UUID, List<Strike>> strikes = new ConcurrentHashMap<>();
    private final Map<Long, Strike> byId = new ConcurrentHashMap<>();
    private final Set<Long> dirty = ConcurrentHashMap.newKeySet();
    private final Set<Long> pardoned = ConcurrentHashMap.newKeySet();
    private final AtomicLong nextId = new AtomicLong(1L);

    public StrikeManager(StrikeStore store) {
        this(store, System::currentTimeMillis);
    }

    public StrikeManager(StrikeStore store, LongSupplier clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Records a strike.
     *
     * @param subject      the member it was for, or blank
     * @param validSeconds how long it counts for; {@code 0} means for ever, and so does
     *                     a duration too long to add to the clock
     */
    public Strike issue(UUID teamId, String teamName, String subject, String reason, String issuedBy,
                        long validSeconds) {
        long now = clock.getAsLong();
        long expiry = validSeconds <= 0 || validSeconds > (Strike.NEVER - now) / 1000L
                ? Strike.NEVER
                : now + validSeconds * 1000L;
        Strike strike = new Strike(nextId.getAndIncrement(), teamId, teamName, subject, reason,
                issuedBy, now, expiry);
        strikes.computeIfAbsent(teamId, id -> new CopyOnWriteArrayList<>()).add(strike);
        byId.put(strike.id(), strike);
        dirty.add(strike.id());
        return strike;
    }

    /** @return how many of this team's strikes still count - what the ladder reads */
    public int activeCount(UUID teamId) {
        long now = clock.getAsLong();
        return (int) strikes.getOrDefault(teamId, List.of()).stream()
                .filter(strike -> strike.isActiveAt(now))
                .count();
    }

    /** @return every strike against this team, newest first, expired ones included */
    public List<Strike> history(UUID teamId) {
        List<Strike> all = new ArrayList<>(strikes.getOrDefault(teamId, List.of()));
        all.sort(Comparator.comparingLong(Strike::issuedAt).thenComparingLong(Strike::id).reversed());
        return all;
    }

    /**
     * Finds a team by the name a strike recorded - for a team since disbanded or
     * renamed. When several teams have carried the name, the most recent strike wins.
     */
    public Optional<Strike> latestUnderTeamName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String wanted = name.toLowerCase(Locale.ROOT);
        return byId.values().stream()
                .filter(strike -> strike.teamName().toLowerCase(Locale.ROOT).equals(wanted))
                .max(Comparator.comparingLong(Strike::issuedAt).thenComparingLong(Strike::id));
    }

    /** @return whether a strike of that id existed and was removed */
    public boolean pardon(long id) {
        Strike strike = byId.remove(id);
        if (strike == null) {
            return false;
        }
        strikes.computeIfPresent(strike.teamId(), (teamId, theirs) -> {
            theirs.remove(strike);
            return theirs.isEmpty() ? null : theirs;
        });
        dirty.remove(id);
        pardoned.add(id);
        return true;
    }

    public Optional<Strike> get(long id) {
        return Optional.ofNullable(byId.get(id));
    }

    public int size() {
        return byId.size();
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public void loadAll() throws Exception {
        store.initSchema();
        strikes.clear();
        byId.clear();
        dirty.clear();
        pardoned.clear();
        long highest = 0L;
        for (Strike strike : store.loadAll()) {
            strikes.computeIfAbsent(strike.teamId(), id -> new CopyOnWriteArrayList<>()).add(strike);
            byId.put(strike.id(), strike);
            highest = Math.max(highest, strike.id());
        }
        nextId.set(highest + 1);
    }

    /**
     * Deletes pardoned strikes, then writes new ones.
     *
     * <p>Pardons go first: a strike issued and pardoned between two saves is then
     * deleted before it is written, and is simply never seen again. Each mark is
     * cleared before its write and put back if the write fails, so the next flush
     * retries it.
     */
    public int flush() throws Exception {
        int written = 0;
        for (Long id : Set.copyOf(pardoned)) {
            pardoned.remove(id);
            try {
                store.delete(id);
                written++;
            } catch (Exception e) {
                pardoned.add(id);
                throw e;
            }
        }
        for (Long id : Set.copyOf(dirty)) {
            dirty.remove(id);
            Strike strike = byId.get(id);
            if (strike == null) {
                continue;
            }
            try {
                store.save(strike);
                written++;
            } catch (Exception e) {
                dirty.add(id);
                throw e;
            }
        }
        return written;
    }
}
