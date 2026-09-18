package com.lawkeys.hcfcore.phase;

import com.lawkeys.hcfcore.claim.RaidabilityPolicy;
import com.lawkeys.hcfcore.events.DailySchedule;
import com.lawkeys.hcfcore.pvp.DeathbanPolicy;
import com.lawkeys.hcfcore.util.Durations;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The phase of the map: SOTW at its start, EOTW at its end (FEATURES.md
 * section 6), and the answers the other modules ask of it.
 *
 * <p>Pure Java, like every manager. It never reaches into another module: they
 * ask through seams they declared themselves - {@code pvp/CombatProtection} and
 * {@code pvp/DeathbanPolicy}, {@code dtr/DeathCostPolicy},
 * {@code claim/ClaimingPolicy} and {@code claim/RaidabilityPolicy} - and the
 * server layer plugs this class's answers into them (ARCHITECTURE.md section 14).
 *
 * <p><strong>The rules, as the project owner set them on 11/09/2026.</strong>
 * <ul>
 *     <li><em>SOTW</em>: no PvP anywhere, no deathban and no DTR lost. A player may
 *     enable PvP for themselves with {@code /sotw enable} - for the rest of that SOTW,
 *     and only against others who did the same.</li>
 *     <li><em>EOTW</em>: every team raidable, no new claims by players, and a death
 *     bans until the map ends. Spawn stays safe.</li>
 *     <li>Both start by staff command, or by themselves at a date written in
 *     {@code phases.yml}.</li>
 *     <li><em>Purge</em> (12/09/2026): a window of time during which every team is
 *     raidable - a temporary EOTW, without EOTW's closed claims or its bans until
 *     the map ends. Started by staff or at daily times. Never during SOTW, which
 *     protects everybody; a SOTW that begins ends a running Purge. Pointless and
 *     refused during EOTW, when everybody is raidable already.</li>
 * </ul>
 *
 * <p><strong>Time, not ticks.</strong> SOTW runs until an instant and EOTW since
 * one; a restart changes neither. A scheduled SOTW is a window - from its date to
 * its date plus its duration - so a server that was down at the start still gets
 * what is left of it, and a date left over from a previous map never fires. Each
 * scheduled date is acted on once.
 */
public final class PhaseManager {

    private final Supplier<PhaseSettings> settings;
    private final PhaseStore store;
    private final LongSupplier clock;

    private volatile PhaseState state = PhaseState.NONE;
    private final Set<UUID> sotwPvpEnabled = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean dirty = new AtomicBoolean();

    /** In memory only: what the last tick saw, so an end is announced once. */
    private volatile boolean sotwWasRunning;
    private volatile boolean purgeWasRunning;
    private final Set<Long> announcedMarks = ConcurrentHashMap.newKeySet();
    /** The last tick, so a scheduled Purge time is acted on once, and one passed while the server was off never. */
    private volatile long lastTick;

    public PhaseManager(Supplier<PhaseSettings> settings, PhaseStore store) {
        this(settings, store, System::currentTimeMillis);
    }

    /** @param clock epoch-millis source; injectable so a two-hour SOTW is tested without waiting */
    public PhaseManager(Supplier<PhaseSettings> settings, PhaseStore store, LongSupplier clock) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lastTick = clock.getAsLong();
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    public PhaseState getState() {
        return state;
    }

    public boolean isSotw() {
        return clock.getAsLong() < state.sotwEndsAt();
    }

    public boolean isEotw() {
        return state.eotwSince() > 0L;
    }

    public boolean isPurge() {
        return clock.getAsLong() < state.purgeEndsAt();
    }

    /** @return whole seconds of Purge left, rounded up; 0 when it is not running */
    public long getPurgeRemainingSeconds() {
        long remaining = state.purgeEndsAt() - clock.getAsLong();
        return Durations.secondsLeft(remaining);
    }

    /** @return whole seconds of SOTW left, rounded up; 0 when it is not running */
    public long getSotwRemainingSeconds() {
        long remaining = state.sotwEndsAt() - clock.getAsLong();
        return Durations.secondsLeft(remaining);
    }

    /** @return whether this player has enabled PvP for the current SOTW */
    public boolean hasEnabledPvp(UUID playerId) {
        return sotwPvpEnabled.contains(playerId);
    }

    /** @return whether a change is waiting to be written */
    public boolean hasPendingWrites() {
        return dirty.get();
    }

    // ------------------------------------------------------------------
    // What the other modules ask
    // ------------------------------------------------------------------

    /** For {@code pvp/CombatProtection}: during SOTW a hit lands only between two players who enabled PvP. */
    public Optional<String> combatRefusal(UUID attacker, UUID victim) {
        if (!isSotw()) {
            return Optional.empty();
        }
        if (!sotwPvpEnabled.contains(attacker)) {
            return Optional.of(PhaseMessages.SOTW_YOU_ARE_PROTECTED);
        }
        if (!sotwPvpEnabled.contains(victim)) {
            return Optional.of(PhaseMessages.SOTW_VICTIM_PROTECTED);
        }
        return Optional.empty();
    }

    /** For {@code pvp/DeathbanPolicy}: none during SOTW, until the map ends during EOTW. */
    public DeathbanPolicy.Rule deathbanRule() {
        if (isSotw()) {
            return DeathbanPolicy.Rule.NONE;
        }
        return isEotw() ? DeathbanPolicy.Rule.UNTIL_MAP_END : DeathbanPolicy.Rule.USUAL;
    }

    /** For {@code dtr/DeathCostPolicy}: no DTR is lost during SOTW. */
    public boolean deathsCostDtr() {
        return !isSotw();
    }

    /** For {@code claim/ClaimingPolicy}: players claim nothing new during EOTW. */
    public Optional<String> claimingRefusal() {
        return isEotw() ? Optional.of(PhaseMessages.EOTW_NO_CLAIMS) : Optional.empty();
    }

    /**
     * For {@code claim/RaidabilityPolicy}: every team is raidable during EOTW and
     * during a Purge; otherwise whatever {@code base} - the DTR - says.
     */
    public RaidabilityPolicy raidability(RaidabilityPolicy base) {
        Objects.requireNonNull(base, "base");
        return teamId -> isEotw() || isPurge() || base.isRaidable(teamId);
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    public PhaseResult startSotw(long seconds) {
        if (seconds <= 0) {
            return PhaseResult.fail(PhaseMessages.INVALID_DURATION, "input", String.valueOf(seconds));
        }
        if (isEotw()) {
            return PhaseResult.fail(PhaseMessages.SOTW_DURING_EOTW);
        }
        if (isSotw()) {
            return PhaseResult.fail(PhaseMessages.SOTW_ALREADY_ACTIVE,
                    "time", Durations.format(getSotwRemainingSeconds()));
        }
        return PhaseResult.announced(beginSotw(clock.getAsLong() + seconds * 1000L));
    }

    public PhaseResult stopSotw() {
        if (!isSotw()) {
            return PhaseResult.fail(PhaseMessages.SOTW_NOT_ACTIVE);
        }
        state = state.withSotwEndsAt(clock.getAsLong());
        sotwPvpEnabled.clear();
        sotwWasRunning = false; // announced here, not again by the next tick
        dirty.set(true);
        return PhaseResult.announced(PhaseUpdate.of(PhaseUpdate.Type.SOTW_STOPPED, PhaseMessages.SOTW_STOPPED));
    }

    /**
     * {@code /sotw enable}: drops this player's protection for the rest of the
     * SOTW. One way on purpose - fight, then take cover behind SOTW again, and the
     * protection is an exploit.
     */
    public PhaseResult enablePvp(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!isSotw()) {
            return PhaseResult.fail(PhaseMessages.SOTW_NOT_ACTIVE);
        }
        if (!sotwPvpEnabled.add(playerId)) {
            return PhaseResult.fail(PhaseMessages.SOTW_ALREADY_ENABLED);
        }
        dirty.set(true);
        return PhaseResult.told(PhaseMessages.SOTW_ENABLED);
    }

    public PhaseResult startEotw() {
        if (isSotw()) {
            return PhaseResult.fail(PhaseMessages.EOTW_DURING_SOTW);
        }
        if (isEotw()) {
            return PhaseResult.fail(PhaseMessages.EOTW_ALREADY_ACTIVE);
        }
        return PhaseResult.announced(beginEotw());
    }

    public PhaseResult startPurge(long seconds) {
        if (seconds <= 0) {
            return PhaseResult.fail(PhaseMessages.INVALID_DURATION, "input", String.valueOf(seconds));
        }
        if (isSotw()) {
            return PhaseResult.fail(PhaseMessages.PURGE_DURING_SOTW);
        }
        if (isEotw()) {
            return PhaseResult.fail(PhaseMessages.PURGE_DURING_EOTW);
        }
        if (isPurge()) {
            return PhaseResult.fail(PhaseMessages.PURGE_ALREADY_ACTIVE,
                    "time", Durations.format(getPurgeRemainingSeconds()));
        }
        return PhaseResult.announced(beginPurge(clock.getAsLong() + seconds * 1000L));
    }

    public PhaseResult stopPurge() {
        if (!isPurge()) {
            return PhaseResult.fail(PhaseMessages.PURGE_NOT_ACTIVE);
        }
        state = state.withPurgeEndsAt(clock.getAsLong());
        purgeWasRunning = false; // announced here, not again by the next tick
        dirty.set(true);
        return PhaseResult.announced(PhaseUpdate.of(PhaseUpdate.Type.PURGE_STOPPED, PhaseMessages.PURGE_STOPPED));
    }

    private PhaseUpdate beginPurge(long endsAt) {
        state = state.withPurgeEndsAt(endsAt);
        purgeWasRunning = true;
        dirty.set(true);
        return PhaseUpdate.of(PhaseUpdate.Type.PURGE_STARTED, PhaseMessages.PURGE_STARTED,
                "time", Durations.format(getPurgeRemainingSeconds()));
    }

    public PhaseResult stopEotw() {
        if (!isEotw()) {
            return PhaseResult.fail(PhaseMessages.EOTW_NOT_ACTIVE);
        }
        state = state.withEotwSince(0L);
        dirty.set(true);
        return PhaseResult.announced(PhaseUpdate.of(PhaseUpdate.Type.EOTW_STOPPED, PhaseMessages.EOTW_STOPPED));
    }

    private PhaseUpdate beginSotw(long endsAt) {
        // SOTW protects everybody: a Purge still running would make claims raidable
        // under a map where nobody can fight. SOTW wins, quietly - its own start is
        // what the server hears.
        if (isPurge()) {
            state = state.withPurgeEndsAt(clock.getAsLong());
            purgeWasRunning = false;
        }
        state = state.withSotwEndsAt(endsAt);
        sotwPvpEnabled.clear();
        sotwWasRunning = true;
        announcedMarks.clear();
        primeMarks();
        dirty.set(true);
        return PhaseUpdate.of(PhaseUpdate.Type.SOTW_STARTED, PhaseMessages.SOTW_STARTED,
                "time", Durations.format(getSotwRemainingSeconds()));
    }

    private PhaseUpdate beginEotw() {
        state = state.withEotwSince(clock.getAsLong());
        dirty.set(true);
        return PhaseUpdate.of(PhaseUpdate.Type.EOTW_STARTED, PhaseMessages.EOTW_STARTED);
    }

    /**
     * Marks as already announced every milestone at or above what is left, so a
     * SOTW started - or reloaded after a restart - with 25 minutes left does not
     * announce "an hour left" and "thirty minutes left" on its first tick.
     */
    private void primeMarks() {
        long remaining = getSotwRemainingSeconds();
        for (long mark : settings.get().sotwAnnounceAtSeconds()) {
            if (mark >= remaining) {
                announcedMarks.add(mark);
            }
        }
    }

    // ------------------------------------------------------------------
    // The tick
    // ------------------------------------------------------------------

    /**
     * Starts what the schedule says is due, notices a SOTW that ran out, and
     * announces SOTW milestones.
     *
     * @return what the whole server should hear, in order
     */
    public List<PhaseUpdate> tick() {
        long now = clock.getAsLong();
        PhaseSettings config = settings.get();
        List<PhaseUpdate> updates = new ArrayList<>();

        long sotwAt = config.sotwScheduledAt();
        if (sotwAt > 0 && state.sotwScheduleHandled() != sotwAt
                && now >= sotwAt && now < sotwAt + config.sotwDurationSeconds() * 1000L) {
            // Handled whether or not it starts: a SOTW already running, or EOTW,
            // stands in for it, and it must not start later in the same window.
            state = state.withSotwScheduleHandled(sotwAt);
            dirty.set(true);
            if (!isSotw() && !isEotw()) {
                updates.add(beginSotw(sotwAt + config.sotwDurationSeconds() * 1000L));
            }
        }

        // The end of a SOTW before the start of EOTW, so that falling in the same
        // tick they are announced in the order they happened.
        boolean running = isSotw();
        if (sotwWasRunning && !running) {
            sotwPvpEnabled.clear();
            dirty.set(true);
            updates.add(PhaseUpdate.of(PhaseUpdate.Type.SOTW_ENDED, PhaseMessages.SOTW_ENDED));
        }
        sotwWasRunning = running;

        long eotwAt = config.eotwScheduledAt();
        // Not handled while a SOTW runs: it waits for the end of it, within its window.
        if (eotwAt > 0 && state.eotwScheduleHandled() != eotwAt && !running
                && now >= eotwAt && now < eotwAt + config.eotwStartWindowSeconds() * 1000L) {
            state = state.withEotwScheduleHandled(eotwAt);
            dirty.set(true);
            if (!isEotw()) {
                updates.add(beginEotw());
            }
        }

        if (running) {
            long remaining = getSotwRemainingSeconds();
            for (long mark : config.sotwAnnounceAtSeconds()) {
                if (remaining <= mark && announcedMarks.add(mark)) {
                    updates.add(PhaseUpdate.of(PhaseUpdate.Type.SOTW_PROGRESS, PhaseMessages.SOTW_PROGRESS,
                            "time", Durations.format(remaining)));
                }
            }
        }

        // The Purge: its end first, then a scheduled start - so a Purge that ends
        // exactly when the next is due is announced as ending, then starting.
        boolean purging = isPurge();
        if (purgeWasRunning && !purging) {
            updates.add(PhaseUpdate.of(PhaseUpdate.Type.PURGE_ENDED, PhaseMessages.PURGE_ENDED));
        }
        purgeWasRunning = purging;
        long from = lastTick;
        lastTick = now;
        if (!purging && !isSotw() && !isEotw()
                && DailySchedule.occursWithin(config.purge().times(), config.timeZone(), from, now)) {
            updates.add(beginPurge(now + config.purge().durationSeconds() * 1000L));
        }
        return updates;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    /** Loads the stored phase. Blocking - async task only. */
    public void loadAll() throws Exception {
        store.initSchema();
        PhaseStore.Snapshot snapshot = store.load();
        state = snapshot.state();
        sotwPvpEnabled.clear();
        announcedMarks.clear();
        dirty.set(false);
        sotwWasRunning = isSotw();
        purgeWasRunning = isPurge();
        lastTick = clock.getAsLong();
        if (sotwWasRunning) {
            sotwPvpEnabled.addAll(snapshot.sotwPvpEnabled());
            primeMarks();
        } else if (!snapshot.sotwPvpEnabled().isEmpty()) {
            // A SOTW that ended while the server was down: its choices go with it.
            dirty.set(true);
        }
    }

    /**
     * Writes the phase if it changed. Blocking - async task only. A failed write
     * stays pending for the next attempt.
     *
     * @return whether anything was written
     */
    public boolean flush() throws Exception {
        if (!dirty.getAndSet(false)) {
            return false;
        }
        try {
            store.save(state, Set.copyOf(sotwPvpEnabled));
        } catch (Exception e) {
            dirty.set(true);
            throw e;
        }
        return true;
    }
}
