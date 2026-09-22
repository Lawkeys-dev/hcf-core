package com.lawkeys.hcfcore.events.core;

import com.lawkeys.hcfcore.events.DailySchedule;
import com.lawkeys.hcfcore.util.Cooldowns;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The rules of DTC and Last Break, without a server - one engine for both, like
 * {@code ConquestManager} is for Conquest.
 *
 * <p>A block cœur stands inside a zone; each team's break of it is recorded here
 * with {@link #recordBreak}, which the caller only invokes once a real,
 * uncancelled {@code BlockBreakEvent} on that exact block has happened -
 * {@link #check} answers, with no side effect, whether a break would currently be
 * allowed, so the server layer can refuse one before it happens (no team, in
 * cooldown) without spending it.
 *
 * <p>The delay between two breaks is per TEAM ({@code util/Cooldowns}, keyed by
 * team id): a team in cooldown never blocks another. One core event runs at a
 * time, like a Conquest.
 */
public final class CoreEventManager {

    private static final String COOLDOWN_KEY = "break";

    /** What {@link #check} answers about a prospective break, with no side effect. */
    public record BreakCheck(Status status, long secondsLeft) {

        public enum Status {
            ALLOWED,
            NOT_RUNNING,
            NO_TEAM,
            COOLDOWN
        }

        public boolean isAllowed() {
            return status == Status.ALLOWED;
        }
    }

    private final Supplier<CoreSettings> settings;
    private final LongSupplier clock;
    private final AtomicReference<CoreRun> current = new AtomicReference<>();
    private final Cooldowns cooldowns = new Cooldowns();
    /** 0 until the first tick: a start or a reload records the instant and fires nothing. */
    private long lastScheduleCheck;

    public CoreEventManager(Supplier<CoreSettings> settings, LongSupplier clock) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Optional<CoreRun> getCurrent() {
        return Optional.ofNullable(current.get());
    }

    public Optional<ZonedDateTime> getNextOccurrence(CoreEventDefinition definition) {
        return DailySchedule.next(definition.schedule(), settings.get().timeZone(), clock.getAsLong());
    }

    /** @return whether this exact block is the core of the running DTC or Last Break */
    public boolean isCore(String world, int x, int y, int z) {
        CoreRun run = current.get();
        return run != null && run.getDefinition().isCoreBlock(world, x, y, z);
    }

    /** @return the announcement, or empty when one is already running */
    public Optional<CoreUpdate> start(CoreEventDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (!current.compareAndSet(null, new CoreRun(definition, clock.getAsLong()))) {
            return Optional.empty();
        }
        cooldowns.clearAll();
        boolean perTeam = definition.winRule() == CoreWinRule.FIRST_TO_TARGET;
        String key = definition.kind() == CoreEventKind.DTC
                ? (perTeam ? CoreMessages.DTC_STARTED_PER_TEAM : CoreMessages.DTC_STARTED)
                : CoreMessages.LAST_BREAK_STARTED;
        return Optional.of(CoreUpdate.of(CoreUpdate.Type.STARTED, key, null,
                "event", definition.displayName(), "target", String.valueOf(definition.breaks()),
                "id", definition.id()));
    }

    /** @return the announcement, or empty when none was running */
    public Optional<CoreUpdate> stop() {
        CoreRun run = current.getAndSet(null);
        if (run == null) {
            return Optional.empty();
        }
        cooldowns.clearAll();
        String key = run.getDefinition().kind() == CoreEventKind.DTC ? CoreMessages.DTC_STOPPED
                : CoreMessages.LAST_BREAK_STOPPED;
        return Optional.of(CoreUpdate.of(CoreUpdate.Type.STOPPED, key, null,
                "event", run.getDefinition().displayName()));
    }

    public void stopAll() {
        current.set(null);
        cooldowns.clearAll();
    }

    /** Forgets the schedule window, so a reload does not fire a time that just passed. */
    public void resetScheduleWindow() {
        lastScheduleCheck = 0L;
    }

    /**
     * Whether a break by this team would be allowed right now. No side effect:
     * unlike {@link #recordBreak}, this never starts or consumes the cooldown, so
     * the server layer can ask it as many times as it likes (typically once, to
     * decide whether to cancel a {@code BlockBreakEvent}) before the one call to
     * {@link #recordBreak} that actually spends it.
     */
    public BreakCheck check(UUID teamId) {
        CoreRun run = current.get();
        if (run == null) {
            return new BreakCheck(BreakCheck.Status.NOT_RUNNING, 0L);
        }
        if (teamId == null) {
            return new BreakCheck(BreakCheck.Status.NO_TEAM, 0L);
        }
        long left = cooldowns.remaining(teamId, COOLDOWN_KEY, clock.getAsLong());
        return left > 0 ? new BreakCheck(BreakCheck.Status.COOLDOWN, left)
                : new BreakCheck(BreakCheck.Status.ALLOWED, 0L);
    }

    /**
     * Records one break of the core by this team, and starts its cooldown. Call
     * only after a real break has happened and {@link #check} allowed it - a
     * break that came in too late (the run just ended) or with no team recorded
     * counts for nothing, on purpose: nobody standing in an empty zone should
     * still be scoring.
     *
     * @return what happened - nothing, a milestone, or the win - empty when the
     *         run had already ended or the team is unknown
     */
    public List<CoreUpdate> recordBreak(UUID teamId) {
        CoreRun run = current.get();
        if (run == null || teamId == null) {
            return List.of();
        }
        long now = clock.getAsLong();
        if (cooldowns.isWaiting(teamId, COOLDOWN_KEY, now)) {
            return List.of();
        }
        CoreEventDefinition definition = run.getDefinition();
        cooldowns.start(teamId, COOLDOWN_KEY, definition.breakCooldownSeconds(), now);
        int teamCount = run.recordBreak(teamId);

        if (definition.winRule() == CoreWinRule.FIRST_TO_TARGET) {
            if (teamCount >= definition.breaks()) {
                current.compareAndSet(run, null);
                return List.of(wonUpdate(definition, teamId, teamCount));
            }
            List<CoreUpdate> updates = new ArrayList<>();
            int remaining = definition.breaks() - teamCount;
            for (int mark : definition.announceAt()) {
                if (remaining <= mark && run.announceRemainingForTeam(teamId, mark)) {
                    updates.add(milestoneUpdate(definition, teamId, remaining));
                }
            }
            return updates;
        }

        // MOST_BREAKS (DTC, SHARED) or LAST_BREAK: common health.
        int remainingHealth = run.health();
        if (remainingHealth <= 0) {
            current.compareAndSet(run, null);
            UUID winner = definition.winRule() == CoreWinRule.LAST_BREAK
                    ? run.lastBreakerTeamId()
                    : run.leaderBreakingTies().orElse(teamId);
            return List.of(wonUpdate(definition, winner, run.breaksOf(winner)));
        }
        List<CoreUpdate> updates = new ArrayList<>();
        for (int mark : definition.announceAt()) {
            if (remainingHealth <= mark && run.announceRemaining(mark)) {
                updates.add(milestoneUpdate(definition, null, remainingHealth));
            }
        }
        return updates;
    }

    /** Advances the schedule and the hard stop; opens a scheduled run. */
    public List<CoreUpdate> tick() {
        long now = clock.getAsLong();
        CoreSettings config = settings.get();
        List<CoreUpdate> updates = new ArrayList<>();

        if (!config.enabled()) {
            // Frozen, not deferred - see ConquestManager#tick for why.
            lastScheduleCheck = now;
            return updates;
        }

        long previous = lastScheduleCheck;
        lastScheduleCheck = now;
        if (previous != 0L && current.get() == null) {
            for (CoreEventDefinition definition : config.definitions()) {
                if (DailySchedule.occursWithin(definition.schedule(), config.timeZone(), previous, now)) {
                    start(definition).ifPresent(updates::add);
                    break;
                }
            }
        }

        CoreRun run = current.get();
        if (run == null) {
            return updates;
        }
        CoreEventDefinition definition = run.getDefinition();
        if (definition.maxDurationSeconds() > 0
                && now - run.getStartedAt() >= definition.maxDurationSeconds() * 1000L) {
            current.compareAndSet(run, null);
            String key = definition.kind() == CoreEventKind.DTC ? CoreMessages.DTC_EXPIRED
                    : CoreMessages.LAST_BREAK_EXPIRED;
            updates.add(CoreUpdate.of(CoreUpdate.Type.EXPIRED, key, null, "event", definition.displayName()));
        }
        return updates;
    }

    private static CoreUpdate milestoneUpdate(CoreEventDefinition definition, UUID teamId, int remaining) {
        boolean perTeam = definition.winRule() == CoreWinRule.FIRST_TO_TARGET;
        String key = definition.kind() == CoreEventKind.DTC
                ? (perTeam ? CoreMessages.DTC_MILESTONE_PER_TEAM : CoreMessages.DTC_MILESTONE)
                : CoreMessages.LAST_BREAK_MILESTONE;
        return CoreUpdate.of(CoreUpdate.Type.MILESTONE, key, teamId,
                "event", definition.displayName(),
                "remaining", String.valueOf(remaining),
                "total", String.valueOf(definition.breaks()));
    }

    private static CoreUpdate wonUpdate(CoreEventDefinition definition, UUID teamId, int breaksCount) {
        String key = definition.kind() == CoreEventKind.DTC ? CoreMessages.DTC_WON : CoreMessages.LAST_BREAK_WON;
        return CoreUpdate.of(CoreUpdate.Type.WON, key, teamId,
                "event", definition.displayName(),
                "breaks", String.valueOf(breaksCount),
                "id", definition.id());
    }
}
