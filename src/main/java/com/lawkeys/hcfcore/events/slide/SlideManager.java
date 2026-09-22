package com.lawkeys.hcfcore.events.slide;

import com.lawkeys.hcfcore.events.DailySchedule;
import com.lawkeys.hcfcore.events.Occupant;
import com.lawkeys.hcfcore.events.Standing;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The rules of Slide, without a server.
 *
 * <p>Every team member standing in the zone brings their team
 * {@code points-per-player} every {@code interval-seconds} - three members score
 * three times as fast, and a team with nobody in the zone scores nothing. A death
 * of any member, anywhere, costs the team {@code death-penalty} (never below
 * zero). The first team to {@code points-to-win} wins; several teams crossing it
 * on the same tick hand it to whichever is highest, and an exact tie changes
 * nothing - the Slide keeps running.
 *
 * <p>One Slide runs at a time, like a Conquest.
 */
public final class SlideManager {

    private final Supplier<SlideSettings> settings;
    private final LongSupplier clock;
    private final AtomicReference<SlideRun> current = new AtomicReference<>();
    /** 0 until the first tick: a start or a reload records the instant and fires nothing. */
    private long lastScheduleCheck;

    public SlideManager(Supplier<SlideSettings> settings, LongSupplier clock) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Optional<SlideRun> getCurrent() {
        return Optional.ofNullable(current.get());
    }

    public Optional<ZonedDateTime> getNextOccurrence(SlideDefinition definition) {
        return DailySchedule.next(definition.schedule(), settings.get().timeZone(), clock.getAsLong());
    }

    /** @return the announcement, or empty when a Slide is already running */
    public Optional<SlideUpdate> start(SlideDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (!current.compareAndSet(null, new SlideRun(definition, clock.getAsLong()))) {
            return Optional.empty();
        }
        return Optional.of(SlideUpdate.of(SlideUpdate.Type.STARTED, SlideMessages.STARTED, null,
                "event", definition.displayName(), "target", String.valueOf(definition.pointsToWin()),
                "id", definition.id()));
    }

    /** @return the announcement, or empty when none was running */
    public Optional<SlideUpdate> stop() {
        SlideRun run = current.getAndSet(null);
        if (run == null) {
            return Optional.empty();
        }
        return Optional.of(SlideUpdate.of(SlideUpdate.Type.STOPPED, SlideMessages.STOPPED, null,
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
     * A member of this team died, anywhere on the server, while the Slide runs.
     *
     * @return the announcement, or empty when nothing was lost - no Slide, no
     *         team, or a team with no points to lose
     */
    public Optional<SlideUpdate> recordDeath(UUID teamId) {
        SlideRun run = current.get();
        if (run == null || teamId == null) {
            return Optional.empty();
        }
        int before = run.points(teamId);
        int penalty = run.getDefinition().deathPenalty();
        if (before <= 0 || penalty <= 0) {
            return Optional.empty();
        }
        int after = run.removePoints(teamId, penalty);
        return Optional.of(SlideUpdate.of(SlideUpdate.Type.POINTS_LOST, SlideMessages.POINTS_LOST, teamId,
                "lost", String.valueOf(before - after),
                "points", String.valueOf(after),
                "target", String.valueOf(run.getDefinition().pointsToWin()),
                "event", run.getDefinition().displayName()));
    }

    /**
     * Advances the running Slide - and opens a scheduled one - by the time since
     * the last tick.
     *
     * @param inZone every team member currently standing in the zone; a player
     *               with no team, in creative or in spectator must not be in this
     *               list - that is decided by the caller, not this engine
     */
    public List<SlideUpdate> tick(List<Occupant> inZone) {
        Objects.requireNonNull(inZone, "inZone");
        long now = clock.getAsLong();
        SlideSettings config = settings.get();
        List<SlideUpdate> updates = new ArrayList<>();

        if (!config.enabled()) {
            // Frozen, not deferred - see ConquestManager#tick for why.
            lastScheduleCheck = now;
            getCurrent().ifPresent(run -> run.setLastTickAt(now));
            return updates;
        }

        long previous = lastScheduleCheck;
        lastScheduleCheck = now;
        if (previous != 0L && current.get() == null) {
            for (SlideDefinition definition : config.definitions()) {
                if (DailySchedule.occursWithin(definition.schedule(), config.timeZone(), previous, now)) {
                    start(definition).ifPresent(updates::add);
                    break;
                }
            }
        }

        SlideRun run = current.get();
        if (run == null) {
            return updates;
        }
        SlideDefinition definition = run.getDefinition();
        long elapsed = Math.max(0L, now - run.lastTickAt());
        run.setLastTickAt(now);

        if (definition.maxDurationSeconds() > 0 && now - run.getStartedAt() >= definition.maxDurationSeconds() * 1000L) {
            current.compareAndSet(run, null);
            updates.add(SlideUpdate.of(SlideUpdate.Type.EXPIRED, SlideMessages.EXPIRED, null,
                    "event", definition.displayName()));
            return updates;
        }

        long intervals = run.consumeIntervals(elapsed, definition.intervalMillis());
        if (intervals <= 0) {
            return updates;
        }

        Map<UUID, Integer> occupantsByTeam = new HashMap<>();
        for (Occupant occupant : inZone) {
            if (occupant.hasTeam()) {
                occupantsByTeam.merge(occupant.teamId(), 1, Integer::sum);
            }
        }
        for (Map.Entry<UUID, Integer> entry : occupantsByTeam.entrySet()) {
            long gained = (long) entry.getValue() * definition.pointsPerPlayer() * intervals;
            if (gained <= 0) {
                continue;
            }
            UUID teamId = entry.getKey();
            int after = run.addPoints(teamId, (int) Math.min(Integer.MAX_VALUE, gained));
            for (int mark : definition.announceAt()) {
                if (after >= mark && run.announceMilestone(teamId, mark)) {
                    updates.add(SlideUpdate.of(SlideUpdate.Type.MILESTONE, SlideMessages.MILESTONE, teamId,
                            "event", definition.displayName(),
                            "points", String.valueOf(after),
                            "target", String.valueOf(definition.pointsToWin())));
                }
            }
        }

        List<Standing> standings = run.standings();
        if (!standings.isEmpty() && standings.get(0).points() >= definition.pointsToWin()) {
            boolean tie = standings.size() > 1 && standings.get(1).points() == standings.get(0).points();
            if (!tie) {
                current.compareAndSet(run, null);
                updates.add(SlideUpdate.of(SlideUpdate.Type.WON, SlideMessages.WON, standings.get(0).teamId(),
                        "event", definition.displayName(),
                        "points", String.valueOf(standings.get(0).points()),
                        "id", definition.id()));
            }
        }
        return updates;
    }
}
