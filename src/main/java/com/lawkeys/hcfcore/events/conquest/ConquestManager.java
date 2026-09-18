package com.lawkeys.hcfcore.events.conquest;

import com.lawkeys.hcfcore.events.ContestPolicy;
import com.lawkeys.hcfcore.events.DailySchedule;
import com.lawkeys.hcfcore.events.Occupant;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The rules of Conquest (FEATURES.md section 6), without a server.
 *
 * <p>The classic HCF ruleset, chosen by the project owner on 12/09/2026: every zone
 * is captured the way a KOTH is - held alone by one team, frozen while contested,
 * reset or paused when lost - but on a short timer, and a capture does not end
 * anything: it gives the team points, the zone's countdown starts again, and the
 * same team can keep scoring from it. The first team to the target wins. A member
 * dying costs the team points, never below zero.
 *
 * <p>Like the capture engine, this never asks the world anything: the caller says
 * who stands in each zone, and gets back what changed. Only what matters is
 * announced - a start, a capture, a loss of points, the end - since four zones'
 * worth of "contested" and "lost control" would bury the chat.
 *
 * <p>One Conquest runs at a time.
 */
public final class ConquestManager {

    private final Supplier<ConquestSettings> settings;
    private final LongSupplier clock;
    private final AtomicReference<ConquestRun> current = new AtomicReference<>();
    /** 0 until the first tick: a start or a reload records the instant and fires nothing. */
    private long lastScheduleCheck;

    public ConquestManager(Supplier<ConquestSettings> settings, LongSupplier clock) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Optional<ConquestRun> getCurrent() {
        return Optional.ofNullable(current.get());
    }

    public Optional<ZonedDateTime> getNextOccurrence(ConquestDefinition definition) {
        return DailySchedule.next(definition.schedule(), settings.get().timeZone(), clock.getAsLong());
    }

    /** @return the announcement, or empty when a Conquest is already running */
    public Optional<ConquestUpdate> start(ConquestDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (!current.compareAndSet(null, new ConquestRun(definition, clock.getAsLong()))) {
            return Optional.empty();
        }
        return Optional.of(ConquestUpdate.of(ConquestUpdate.Type.STARTED, ConquestMessages.STARTED, null,
                "event", definition.displayName(),
                "zones", String.valueOf(definition.zones().size()),
                "points", String.valueOf(definition.pointsToWin())));
    }

    /** @return the announcement, or empty when none was running */
    public Optional<ConquestUpdate> stop() {
        ConquestRun run = current.getAndSet(null);
        if (run == null) {
            return Optional.empty();
        }
        return Optional.of(ConquestUpdate.of(ConquestUpdate.Type.STOPPED, ConquestMessages.STOPPED, null,
                "event", run.getDefinition().displayName()));
    }

    public void stopAll() {
        current.set(null);
    }

    /** Forgets the schedule window, so a reload does not fire a time that just passed. */
    public void resetScheduleWindow() {
        lastScheduleCheck = 0L;
    }

    /**
     * A member of this team died.
     *
     * @return the announcement, or empty when nothing was lost - no Conquest, no
     *         team, or a team with no points to lose
     */
    public Optional<ConquestUpdate> recordDeath(UUID teamId) {
        ConquestRun run = current.get();
        if (run == null || teamId == null || !settings.get().enabled()) {
            return Optional.empty();
        }
        int before = run.points(teamId);
        int penalty = run.getDefinition().deathPenalty();
        if (before <= 0 || penalty <= 0) {
            return Optional.empty();
        }
        int after = run.removePoints(teamId, penalty);
        return Optional.of(ConquestUpdate.of(ConquestUpdate.Type.POINTS_LOST, ConquestMessages.POINTS_LOST, teamId,
                "lost", String.valueOf(before - after),
                "points", String.valueOf(after),
                "target", String.valueOf(run.getDefinition().pointsToWin()),
                "event", run.getDefinition().displayName()));
    }

    /**
     * Advances the running Conquest - and opens a scheduled one - by the time since
     * the last tick.
     *
     * @param occupantsByZone who stands in each zone of the running Conquest, by zone id
     */
    public List<ConquestUpdate> tick(Map<String, List<Occupant>> occupantsByZone) {
        Objects.requireNonNull(occupantsByZone, "occupantsByZone");
        long now = clock.getAsLong();
        ConquestSettings config = settings.get();
        List<ConquestUpdate> updates = new ArrayList<>();

        if (!config.enabled()) {
            // Frozen, not deferred: the time spent disabled must not land as one
            // elapsed chunk on the first tick back - that would hand out captures.
            lastScheduleCheck = now;
            getCurrent().ifPresent(run -> run.setLastTickAt(now));
            return updates;
        }

        long previous = lastScheduleCheck;
        lastScheduleCheck = now;
        if (previous != 0L && current.get() == null) {
            for (ConquestDefinition definition : config.definitions()) {
                if (DailySchedule.occursWithin(definition.schedule(), config.timeZone(), previous, now)) {
                    start(definition).ifPresent(updates::add);
                    break;
                }
            }
        }

        ConquestRun run = current.get();
        if (run == null) {
            return updates;
        }
        ConquestDefinition definition = run.getDefinition();
        long elapsed = Math.max(0L, now - run.lastTickAt());
        run.setLastTickAt(now);

        if (definition.maxDurationSeconds() > 0 && now - run.getStartedAt() >= definition.maxDurationSeconds() * 1000L) {
            current.compareAndSet(run, null);
            updates.add(ConquestUpdate.of(ConquestUpdate.Type.EXPIRED, ConquestMessages.EXPIRED, null,
                    "event", definition.displayName()));
            return updates;
        }

        for (ConquestRun.ZoneState state : run.zones()) {
            List<Occupant> occupants = occupantsByZone.getOrDefault(state.zone().id(), List.of());
            Optional<ConquestUpdate> won = advance(run, state, occupants, elapsed, config.teamlessPlayersContest(),
                    updates);
            if (won.isPresent()) {
                current.compareAndSet(run, null);
                updates.add(won.get());
                return updates;
            }
        }
        return updates;
    }

    /** @return the win, if this zone's capture took its team to the target */
    private Optional<ConquestUpdate> advance(ConquestRun run, ConquestRun.ZoneState state, List<Occupant> occupants,
                                             long elapsed, boolean teamlessContest, List<ConquestUpdate> updates) {
        ConquestDefinition definition = run.getDefinition();
        UUID holder = soleHolder(occupants, teamlessContest);
        UUID lastHolder = state.holder();

        if (holder != null) {
            // Only a different team taking over costs the progress, and only under
            // RESET: the same team coming back resumes, as in a KOTH.
            if (lastHolder != null && !lastHolder.equals(holder) && definition.contestPolicy() == ContestPolicy.RESET) {
                run.resetCountdown(state);
            }
            run.hold(state, holder, false);
            run.countDown(state, elapsed);
            if (!run.isCaptured(state)) {
                return Optional.empty();
            }
            int points = run.addPoints(holder, definition.pointsPerCapture());
            run.resetCountdown(state);
            if (points >= definition.pointsToWin()) {
                return Optional.of(ConquestUpdate.of(ConquestUpdate.Type.WON, ConquestMessages.WON, holder,
                        "event", definition.displayName(),
                        "points", String.valueOf(points),
                        "id", definition.id()));
            }
            updates.add(ConquestUpdate.of(ConquestUpdate.Type.ZONE_CAPTURED, ConquestMessages.ZONE_CAPTURED, holder,
                    "zone", state.zone().displayName(),
                    "gained", String.valueOf(definition.pointsPerCapture()),
                    "points", String.valueOf(points),
                    "target", String.valueOf(definition.pointsToWin()),
                    "event", definition.displayName()));
            return Optional.empty();
        }

        if (isContested(occupants, teamlessContest)) {
            // Frozen: no countdown, no reset, the holder kept.
            run.hold(state, lastHolder, true);
            return Optional.empty();
        }
        // Empty: losing the zone outright is what costs the progress under RESET.
        if (lastHolder != null && definition.contestPolicy() == ContestPolicy.RESET) {
            run.resetCountdown(state);
        }
        run.hold(state, null, false);
        return Optional.empty();
    }

    /** @return the one team standing in the zone, or {@code null} when it is empty or contested */
    private static UUID soleHolder(List<Occupant> occupants, boolean teamlessContest) {
        Set<UUID> teams = new HashSet<>();
        for (Occupant occupant : occupants) {
            if (occupant.hasTeam()) {
                teams.add(occupant.teamId());
            } else if (teamlessContest) {
                return null;
            }
        }
        return teams.size() == 1 ? teams.iterator().next() : null;
    }

    private static boolean isContested(List<Occupant> occupants, boolean teamlessContest) {
        for (Occupant occupant : occupants) {
            if (occupant.hasTeam() || teamlessContest) {
                return true;
            }
        }
        return false;
    }
}
