package com.lawkeys.hcfcore.events.king;

import com.lawkeys.hcfcore.events.DailySchedule;
import com.lawkeys.hcfcore.util.Durations;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * The rule engine of Kill the King (FEATURES.md section 6).
 *
 * <p>Pure Java, like the capture engine next door, and built the same way: it
 * never asks the world anything. The server layer says who may be drawn, whether
 * the King is inside the warzone, and who killed them; this class answers with
 * {@link KingUpdate}s. That is what lets a whole reign - drawn, wandering out,
 * withering, killed by a teammate - be played in a unit test.
 *
 * <p><strong>Who wins.</strong> The King, if the time runs out with the King
 * alive. Otherwise the player the server credits with the King's death - but only
 * a player who is not on the King's team, neither at the crowning nor at the
 * death, so a teammate cannot kill a King drawn from their own team to take the
 * prize, and a King cannot leave their team to hand it to them. Any other death -
 * withered outside, a fall, lava, their own hand - and a logout end the run with
 * no winner (decided by the project owner on 11/09/2026).
 *
 * <p><strong>One run at a time.</strong> Two Kings at once would share one
 * warzone and one set of chat coordinates; nothing in FEATURES.md asks for it.
 */
public final class KingEventManager {

    private final Supplier<KingSettings> settings;
    private final LongSupplier clock;
    private final RandomGenerator random;

    private final AtomicReference<KingRun> current = new AtomicReference<>();

    /** Upper bound of the window already examined for scheduled starts; 0 before the first check. */
    private volatile long lastScheduleCheck;

    public KingEventManager(Supplier<KingSettings> settings, LongSupplier clock, RandomGenerator random) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    public Optional<KingRun> getCurrent() {
        return Optional.ofNullable(current.get());
    }

    /** @return whether this player is the reigning King */
    public boolean isKing(UUID playerId) {
        KingRun run = current.get();
        return run != null && run.isReigning() && run.getKingId().equals(playerId);
    }

    /** @return whole seconds the King has left, rounded up; 0 when nobody reigns */
    public long getRemainingSeconds() {
        KingRun run = current.get();
        if (run == null || !run.isReigning()) {
            return 0L;
        }
        return Durations.secondsLeft(run.getEndsAt() - clock.getAsLong());
    }

    public Optional<ZonedDateTime> getNextOccurrence(KingEventDefinition definition) {
        return DailySchedule.next(definition.schedule(), settings.get().timeZone(), clock.getAsLong());
    }

    // ------------------------------------------------------------------
    // Starting
    // ------------------------------------------------------------------

    /**
     * Opens a run. Nobody is King yet: the server still has to find a spot in the
     * warzone and draw one, and the run holds the slot meanwhile.
     *
     * @return {@code false} when a run is already under way
     */
    public boolean open(KingEventDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return current.compareAndSet(null, new KingRun(definition));
    }

    /**
     * Draws the King among the candidates, or calls the run off when there are
     * fewer of them than the definition requires.
     *
     * @return empty when there is no run waiting for a King
     */
    public Optional<KingUpdate> crown(List<Candidate> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        KingRun run = current.get();
        if (run == null || run.getPhase() != KingRun.Phase.CROWNING) {
            return Optional.empty();
        }
        KingEventDefinition definition = run.getDefinition();
        if (candidates.size() < definition.minimumPlayers()) {
            if (!current.compareAndSet(run, null)) {
                return Optional.empty();
            }
            return Optional.of(KingUpdate.of(KingUpdate.Type.CANCELLED, definition.id(), null, null,
                    KingMessages.CANCELLED_NOT_ENOUGH_PLAYERS,
                    "event", definition.displayName(),
                    "minimum", String.valueOf(definition.minimumPlayers())));
        }
        Candidate king = candidates.get(random.nextInt(candidates.size()));
        run.crown(king.playerId(), king.teamId(), clock.getAsLong());
        return Optional.of(KingUpdate.of(KingUpdate.Type.CROWNED, definition.id(), king.playerId(), null,
                KingMessages.CROWNED,
                "event", definition.displayName(),
                "time", Durations.format(definition.durationSeconds())));
    }

    /**
     * @return the definitions whose scheduled time has come since the last call.
     *         The first call records the instant and returns nothing, so a
     *         restart at 18:05 does not start the 18:00 event.
     */
    public List<KingEventDefinition> dueDefinitions() {
        long now = clock.getAsLong();
        long previous = lastScheduleCheck;
        lastScheduleCheck = now;
        KingSettings config = settings.get();
        if (previous == 0L || !config.enabled()) {
            return List.of();
        }
        List<KingEventDefinition> due = new ArrayList<>();
        for (KingEventDefinition definition : config.definitions()) {
            if (DailySchedule.occursWithin(definition.schedule(), config.timeZone(), previous, now)) {
                due.add(definition);
            }
        }
        return due;
    }

    /** Forgets the schedule window, so a reload does not fire times already passed. */
    public void resetScheduleWindow() {
        this.lastScheduleCheck = 0L;
    }

    // ------------------------------------------------------------------
    // The reign
    // ------------------------------------------------------------------

    /**
     * Advances the reign by however long it has been since the last tick.
     *
     * @param kingInsideZone whether the King is inside the warzone right now. A safe
     *                       zone counts as outside: the King cannot win by waiting in spawn
     * @return what changed; empty when nobody reigns
     */
    public List<KingUpdate> tick(boolean kingInsideZone) {
        KingRun run = current.get();
        if (run == null || !run.isReigning()) {
            return List.of();
        }
        KingEventDefinition definition = run.getDefinition();
        UUID king = run.getKingId();
        long now = clock.getAsLong();
        long previousTick = run.getLastTickAt();
        run.setLastTickAt(now);
        List<KingUpdate> updates = new ArrayList<>();

        if (now >= run.getEndsAt()) {
            if (current.compareAndSet(run, null)) {
                updates.add(KingUpdate.of(KingUpdate.Type.SURVIVED, definition.id(), king, king,
                        KingMessages.SURVIVED, "event", definition.displayName()));
            }
            return updates;
        }

        OutsidePenalty penalty = definition.penalty();
        if (kingInsideZone) {
            if (run.getOutsideSince() != KingRun.INSIDE) {
                run.setOutsideSince(KingRun.INSIDE);
                updates.add(KingUpdate.of(KingUpdate.Type.RETURNED, definition.id(), king, null,
                        KingMessages.RETURNED, "event", definition.displayName()));
            }
        } else {
            if (run.getOutsideSince() == KingRun.INSIDE) {
                run.setOutsideSince(now);
                updates.add(KingUpdate.of(KingUpdate.Type.LEFT_ZONE, definition.id(), king, null,
                        KingMessages.LEFT_ZONE,
                        "event", definition.displayName(),
                        "grace", String.valueOf(penalty.graceSeconds())));
            }
            long outsideSince = run.getOutsideSince();
            long secondsOutside = (now - outsideSince) / 1000L;
            if (penalty.appliesAfter(secondsOutside)) {
                // Damage only for the part of this tick spent outside past the
                // grace: the King is not charged for the second they were still inside,
                // nor for the grace itself.
                long punishedSince = Math.max(previousTick, outsideSince + penalty.graceSeconds() * 1000L);
                double damage = penalty.damagePerSecond() * Math.max(0L, now - punishedSince) / 1000.0;
                updates.add(KingUpdate.penalty(definition.id(), king,
                        penalty.witherLevelAfter(secondsOutside), damage));
            }
        }

        long remaining = Durations.secondsLeft(run.getEndsAt() - now);
        for (long mark : definition.announceAtSeconds()) {
            // A mark at or above the full reign would fire the moment the King is crowned.
            if (mark >= definition.durationSeconds() || remaining > mark) {
                continue;
            }
            if (run.claimMark(mark)) {
                updates.add(KingUpdate.of(KingUpdate.Type.PROGRESS, definition.id(), king, null,
                        KingMessages.PROGRESS,
                        "event", definition.displayName(),
                        "time", Durations.format(remaining)));
            }
        }
        return updates;
    }

    /**
     * The King died.
     *
     * @param killerId     who the server credits with the kill, or {@code null}
     * @param killerTeamId the killer's team, or {@code null}
     * @param kingTeamNow  the King's team at the moment of the death, or {@code null}
     * @return empty when nobody reigns
     */
    public Optional<KingUpdate> kingDied(UUID killerId, UUID killerTeamId, UUID kingTeamNow) {
        KingRun run = current.get();
        if (run == null || !run.isReigning() || !current.compareAndSet(run, null)) {
            return Optional.empty();
        }
        KingEventDefinition definition = run.getDefinition();
        UUID king = run.getKingId();
        // Team mode keeps the prize from the King's own team; in solo mode
        // everybody hunts the King, teammates included, and the prize is personal.
        boolean teamRule = definition.mode() == KingMode.TEAM;
        boolean earned = killerId != null
                && !killerId.equals(king)
                && (!teamRule || (!sameTeam(killerTeamId, run.getKingTeamId())
                && !sameTeam(killerTeamId, kingTeamNow)));
        return Optional.of(earned
                ? KingUpdate.of(KingUpdate.Type.KILLED, definition.id(), king, killerId,
                        KingMessages.KILLED, "event", definition.displayName())
                : KingUpdate.of(KingUpdate.Type.DIED, definition.id(), king, null,
                        KingMessages.DIED, "event", definition.displayName()));
    }

    /** The King logged out. @return empty when nobody reigns */
    public Optional<KingUpdate> kingQuit() {
        KingRun run = current.get();
        if (run == null || !run.isReigning() || !current.compareAndSet(run, null)) {
            return Optional.empty();
        }
        return Optional.of(KingUpdate.of(KingUpdate.Type.FLED, run.getDefinition().id(), run.getKingId(), null,
                KingMessages.FLED, "event", run.getDefinition().displayName()));
    }

    /** Stopped by staff, crowned or not. @return empty when nothing is running */
    public Optional<KingUpdate> stop() {
        return end(KingUpdate.Type.STOPPED, KingMessages.STOPPED);
    }

    /**
     * Called off by the server for a reason only it can see - no warzone in that
     * world, no safe spot found, a teleport that failed.
     *
     * @return empty when nothing is running
     */
    public Optional<KingUpdate> cancel(String reasonKey) {
        return end(KingUpdate.Type.CANCELLED, Objects.requireNonNull(reasonKey, "reasonKey"));
    }

    /** Drops any run without a word, for a shutdown. */
    public void stopAll() {
        current.set(null);
    }

    private Optional<KingUpdate> end(KingUpdate.Type type, String key) {
        KingRun run = current.get();
        if (run == null || !current.compareAndSet(run, null)) {
            return Optional.empty();
        }
        return Optional.of(KingUpdate.of(type, run.getDefinition().id(), run.getKingId(), null,
                key, "event", run.getDefinition().displayName()));
    }

    private static boolean sameTeam(UUID a, UUID b) {
        return a != null && a.equals(b);
    }
}
