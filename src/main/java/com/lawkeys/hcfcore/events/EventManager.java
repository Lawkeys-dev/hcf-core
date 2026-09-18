package com.lawkeys.hcfcore.events;

import com.lawkeys.hcfcore.util.Durations;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The events module's entry point and rule engine - family A of ARCHITECTURE.md
 * section 9 (capture/objective events: KOTH, Citadel, and the variants to come).
 *
 * <p>Pure Java with no server API, like every other manager in this plugin.
 *
 * <p><strong>The design point worth knowing: the rule engine never asks the world
 * anything.</strong> It cannot - it has no server. Each tick, the caller says who
 * is standing in each zone, and this class answers with a list of
 * {@link EventUpdate}s describing what changed. That inversion is what makes the
 * capture rules testable by writing down an occupancy instead of standing in a
 * region, and it is why a whole KOTH can be played out in a unit test in
 * microseconds.
 *
 * <p><strong>Messages are emitted on transitions, never per tick.</strong> Two
 * teams brawling in a zone would otherwise broadcast "contested" every second.
 * The last phase is remembered per run, and only a change produces an update.
 *
 * <p><strong>Variants are configuration, not subclasses.</strong> A classic KOTH
 * and the Citadel of FEATURES.md section 6 differ only in how long the zone must
 * be held; both are {@link CaptureEventDefinition}s. Nothing here knows the word
 * "Citadel", which is precisely the extensibility ARCHITECTURE.md section 9 asks
 * for.
 */
public final class EventManager {

    private final Supplier<EventSettings> settings;
    private final LongSupplier clock;

    private final Map<String, RunningEvent> active = new ConcurrentHashMap<>();

    /**
     * Upper bound of the window already examined for scheduled starts.
     *
     * <p>Zero until the first tick: the first tick only records "now" and starts
     * nothing, so a restart at 18:05 does not immediately fire the 18:00 event.
     */
    private volatile long lastScheduleCheck;

    public EventManager(Supplier<EventSettings> settings) {
        this(settings, System::currentTimeMillis);
    }

    /** @param clock epoch-millis source; injectable so captures can be tested without waiting */
    public EventManager(Supplier<EventSettings> settings, LongSupplier clock) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private EventSettings config() {
        return settings.get();
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    /** @return the runs in progress, in no particular order */
    public Collection<RunningEvent> getActiveEvents() {
        return List.copyOf(active.values());
    }

    public Optional<RunningEvent> getActiveEvent(String eventId) {
        return eventId == null ? Optional.empty() : Optional.ofNullable(active.get(key(eventId)));
    }

    public boolean isActive(String eventId) {
        return getActiveEvent(eventId).isPresent();
    }

    public int getActiveCount() {
        return active.size();
    }

    /**
     * @return the next time this event starts by itself, or empty when it has no
     *         schedule and only staff can open it
     */
    public Optional<ZonedDateTime> getNextOccurrence(CaptureEventDefinition definition) {
        return getNextOccurrence(definition, clock.getAsLong());
    }

    Optional<ZonedDateTime> getNextOccurrence(CaptureEventDefinition definition, long now) {
        return DailySchedule.next(definition.schedule(), config().timeZone(), now);
    }

    // ------------------------------------------------------------------
    // Staff control
    // ------------------------------------------------------------------

    /**
     * Opens an event now, whatever its schedule says.
     *
     * @return the broadcast to make, or empty when the event is already running
     */
    public Optional<EventUpdate> start(CaptureEventDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        long now = clock.getAsLong();
        String id = key(definition.id());
        // putIfAbsent rather than containsKey-then-put: the pair has to be one
        // atomic step. Only the main thread starts events today, but that is a
        // calling convention, not something the type enforces - and the map is a
        // ConcurrentHashMap precisely so it does not have to be. Split in two, two
        // callers could each see the event as absent and each announce a start.
        if (active.putIfAbsent(id, new RunningEvent(definition, now)) != null) {
            return Optional.empty();
        }
        return Optional.of(EventUpdate.of(EventUpdate.Type.STARTED, definition.id(), null,
                EventMessages.STARTED,
                "event", definition.displayName(),
                "zone", definition.zone().toString(),
                "time", Durations.format(definition.captureSeconds())));
    }

    /**
     * Closes a running event with no winner.
     *
     * @return the broadcast to make, or empty when it was not running
     */
    public Optional<EventUpdate> stop(String eventId) {
        RunningEvent removed = eventId == null ? null : active.remove(key(eventId));
        if (removed == null) {
            return Optional.empty();
        }
        return Optional.of(EventUpdate.of(EventUpdate.Type.STOPPED, removed.getDefinition().id(), null,
                EventMessages.STOPPED, "event", removed.getDefinition().displayName()));
    }

    /** Closes every running event, for a shutdown or a reload that removed definitions. */
    public void stopAll() {
        active.clear();
    }

    // ------------------------------------------------------------------
    // The tick
    // ------------------------------------------------------------------

    /**
     * Advances every running event and opens any that the schedule is due to open.
     *
     * @param occupantsByEvent for each running event id, who is standing in its
     *                         zone right now; a missing or empty entry means nobody
     * @return what changed, in the order it should be announced
     */
    public List<EventUpdate> tick(Map<String, List<Occupant>> occupantsByEvent) {
        Objects.requireNonNull(occupantsByEvent, "occupantsByEvent");
        long now = clock.getAsLong();
        List<EventUpdate> updates = new ArrayList<>();

        EventSettings config = config();
        if (!config.enabled()) {
            // Nothing happens while the module is off - but the clocks still have to
            // move. Left untouched, the whole disabled period lands as one elapsed
            // chunk on the first tick after re-enabling: a KOTH disabled for two
            // minutes would be won instantly by whoever happens to be standing in the
            // zone. "Disabled" has to mean frozen, not merely deferred.
            lastScheduleCheck = now;
            for (RunningEvent running : active.values()) {
                running.setLastTickAt(now);
            }
            return updates;
        }

        updates.addAll(openScheduledEvents(config, now));

        for (RunningEvent running : List.copyOf(active.values())) {
            List<Occupant> occupants =
                    occupantsByEvent.getOrDefault(running.getDefinition().id(), List.of());
            advance(config, running, occupants, now, updates);
        }
        return updates;
    }

    private List<EventUpdate> openScheduledEvents(EventSettings config, long now) {
        long previous = lastScheduleCheck;
        lastScheduleCheck = now;
        if (previous == 0L) {
            // First tick after a start or a reload: record the instant, fire nothing.
            return List.of();
        }

        List<EventUpdate> started = new ArrayList<>();
        ZoneId zone = config.timeZone();
        for (CaptureEventDefinition definition : config.definitions()) {
            if (definition.schedule().isEmpty() || active.containsKey(key(definition.id()))) {
                continue;
            }
            if (DailySchedule.occursWithin(definition.schedule(), zone, previous, now)) {
                start(definition).ifPresent(started::add);
            }
        }
        return started;
    }

    private void advance(EventSettings config, RunningEvent running, List<Occupant> occupants,
                         long now, List<EventUpdate> updates) {
        CaptureEventDefinition definition = running.getDefinition();
        long elapsed = Math.max(0L, now - running.getLastTickAt());
        running.setLastTickAt(now);

        if (definition.maxDurationSeconds() > 0
                && now - running.getStartedAt() >= definition.maxDurationSeconds() * 1000L) {
            active.remove(key(definition.id()));
            updates.add(EventUpdate.of(EventUpdate.Type.EXPIRED, definition.id(), null,
                    EventMessages.EXPIRED, "event", definition.displayName()));
            return;
        }

        UUID holder = soleHolder(config, occupants);
        RunningEvent.Phase phase = phaseOf(config, occupants, holder);
        RunningEvent.Phase previousPhase = running.getPhase();
        // The team that last held the zone and has not yet lost it. It deliberately
        // survives a contest: an enemy walking in freezes the capture, it does not
        // end it.
        UUID lastHolder = running.getHolderTeamId();

        running.setPhase(phase);

        switch (phase) {
            case HELD -> {
                boolean newHolder = !Objects.equals(holder, lastHolder);
                // Only a *different* team taking over wipes the progress. Coming back
                // to a zone you were already holding, after an enemy was pushed out,
                // resumes where you were.
                if (newHolder && lastHolder != null
                        && definition.contestPolicy() == ContestPolicy.RESET) {
                    running.resetCountdown();
                }
                running.setHolderTeamId(holder);
                if (newHolder) {
                    updates.add(EventUpdate.of(EventUpdate.Type.CAPTURE_BEGAN, definition.id(), holder,
                            EventMessages.CAPTURE_BEGAN,
                            "event", definition.displayName(),
                            "time", Durations.format(running.getRemainingSeconds())));
                }
                running.decrease(elapsed);
                if (running.getRemainingMillis() <= 0L) {
                    active.remove(key(definition.id()));
                    updates.add(EventUpdate.of(EventUpdate.Type.CAPTURED, definition.id(), holder,
                            EventMessages.CAPTURED, "event", definition.displayName()));
                    return;
                }
                announceMilestones(definition, running, holder, updates);
            }
            case CONTESTED -> {
                // Frozen, and only frozen: no decrement and no reset. The holder is
                // remembered, so the same team retaking the zone resumes silently
                // instead of being announced as a fresh capture every time an enemy
                // steps in and out.
                if (previousPhase != RunningEvent.Phase.CONTESTED && config.announceContests()) {
                    updates.add(EventUpdate.of(EventUpdate.Type.CONTESTED, definition.id(), lastHolder,
                            EventMessages.CONTESTED, "event", definition.displayName()));
                }
            }
            case EMPTY -> {
                // Losing the zone outright is what costs the progress under RESET -
                // being pushed off it, not merely being challenged on it.
                if (lastHolder != null) {
                    if (definition.contestPolicy() == ContestPolicy.RESET) {
                        running.resetCountdown();
                    }
                    running.setHolderTeamId(null);
                    updates.add(EventUpdate.of(EventUpdate.Type.CONTROL_LOST, definition.id(), lastHolder,
                            EventMessages.CONTROL_LOST,
                            "event", definition.displayName(),
                            "time", Durations.format(running.getRemainingSeconds())));
                }
            }
        }
    }

    private void announceMilestones(CaptureEventDefinition definition, RunningEvent running,
                                    UUID holder, List<EventUpdate> updates) {
        long remaining = running.getRemainingSeconds();
        for (long mark : definition.announceAtSeconds()) {
            // A mark at or above the full duration would fire the instant the event
            // opens, which is noise rather than information.
            if (mark >= definition.captureSeconds() || remaining > mark) {
                continue;
            }
            if (running.claimMark(mark)) {
                updates.add(EventUpdate.of(EventUpdate.Type.PROGRESS, definition.id(), holder,
                        EventMessages.PROGRESS,
                        "event", definition.displayName(),
                        "time", Durations.format(remaining)));
            }
        }
    }

    /**
     * @return the team holding the zone alone, or {@code null} when the zone is
     *         empty or contested
     */
    private static UUID soleHolder(EventSettings config, List<Occupant> occupants) {
        Set<UUID> teams = new HashSet<>();
        for (Occupant occupant : occupants) {
            if (occupant.hasTeam()) {
                teams.add(occupant.teamId());
            } else if (config.teamlessPlayersContest()) {
                return null;
            }
        }
        return teams.size() == 1 ? teams.iterator().next() : null;
    }

    private static RunningEvent.Phase phaseOf(EventSettings config, List<Occupant> occupants,
                                              UUID holder) {
        if (holder != null) {
            return RunningEvent.Phase.HELD;
        }
        boolean anyoneWhoCounts = false;
        for (Occupant occupant : occupants) {
            if (occupant.hasTeam() || config.teamlessPlayersContest()) {
                anyoneWhoCounts = true;
                break;
            }
        }
        return anyoneWhoCounts ? RunningEvent.Phase.CONTESTED : RunningEvent.Phase.EMPTY;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String key(String eventId) {
        return eventId.toLowerCase(Locale.ROOT);
    }

    /** Forgets the schedule window, so a reload does not fire times already passed. */
    public void resetScheduleWindow() {
        this.lastScheduleCheck = 0L;
    }
}
