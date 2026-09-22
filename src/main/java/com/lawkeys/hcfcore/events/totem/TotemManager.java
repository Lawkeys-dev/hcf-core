package com.lawkeys.hcfcore.events.totem;

import com.lawkeys.hcfcore.events.DailySchedule;

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
 * The rules of the Totem, without a server - the project owner's event of
 * 22/09/2026, the classic of faction and HCF servers.
 *
 * <p>A column of blocks; a team breaks them one by one, and each broken block stays
 * broken. The first team to break the whole column wins. <strong>A block broken by
 * any other team starts the totem over</strong> - every block whole again - so a
 * team must hold the ground long enough to finish. {@link RivalBreak} says whether
 * that break then counts as the other team's first.
 *
 * <p>The caller records a break with {@link #recordBreak} only once a real,
 * uncancelled {@code BlockBreakEvent} happened on a block {@link #activeLevel} named;
 * {@link #check} answers first, with no side effect. One Totem runs at a time.
 */
public final class TotemManager {

    /** What {@link #check} answers about a prospective break. */
    public enum BreakCheck {
        ALLOWED,
        NOT_RUNNING,
        NO_TEAM
    }

    private final Supplier<TotemSettings> settings;
    private final LongSupplier clock;
    private final AtomicReference<TotemRun> current = new AtomicReference<>();
    /** 0 until the first tick: a start or a reload records the instant and fires nothing. */
    private long lastScheduleCheck;

    public TotemManager(Supplier<TotemSettings> settings, LongSupplier clock) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Optional<TotemRun> getCurrent() {
        return Optional.ofNullable(current.get());
    }

    public Optional<ZonedDateTime> getNextOccurrence(TotemDefinition definition) {
        return DailySchedule.next(definition.schedule(), settings.get().timeZone(), clock.getAsLong());
    }

    /**
     * @return which block of the running Totem's column that is, while it still stands
     *         to be broken; {@code -1} for any other block, a broken one included
     */
    public int activeLevel(String world, int x, int y, int z) {
        TotemRun run = current.get();
        if (run == null) {
            return -1;
        }
        int level = run.getDefinition().levelOf(world, x, y, z);
        return level >= 0 && !run.isBroken(level) ? level : -1;
    }

    /** @return the announcement, or empty when one is already running */
    public Optional<TotemUpdate> start(TotemDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (!current.compareAndSet(null, new TotemRun(definition, clock.getAsLong()))) {
            return Optional.empty();
        }
        return Optional.of(TotemUpdate.of(TotemUpdate.Type.STARTED, TotemMessages.STARTED, null,
                "event", definition.displayName(), "height", String.valueOf(definition.height()),
                "id", definition.id()));
    }

    /** @return the announcement, or empty when none was running */
    public Optional<TotemUpdate> stop() {
        TotemRun run = current.getAndSet(null);
        return run == null ? Optional.empty()
                : Optional.of(TotemUpdate.of(TotemUpdate.Type.STOPPED, TotemMessages.STOPPED, null,
                        "event", run.getDefinition().displayName(), "id", run.getDefinition().id()));
    }

    public void stopAll() {
        current.set(null);
    }

    /** Forgets the schedule window, so a reload does not fire a time that just passed. */
    public void resetScheduleWindow() {
        lastScheduleCheck = 0L;
    }

    /** Whether a break by this team would count right now. No side effect. */
    public BreakCheck check(UUID teamId) {
        if (current.get() == null) {
            return BreakCheck.NOT_RUNNING;
        }
        return teamId == null ? BreakCheck.NO_TEAM : BreakCheck.ALLOWED;
    }

    /**
     * Records one block of the column broken by this team.
     *
     * @param level the block, as {@link #activeLevel} named it
     * @return what happened: a block broken, the totem started over, or the win;
     *         empty when the run had ended, the team is unknown or the block was
     *         already broken
     */
    public List<TotemUpdate> recordBreak(UUID teamId, int level) {
        TotemRun run = current.get();
        if (run == null || teamId == null || level < 0 || level >= run.getDefinition().height()
                || run.isBroken(level)) {
            return List.of();
        }
        TotemDefinition definition = run.getDefinition();
        List<TotemUpdate> updates = new ArrayList<>();
        UUID holder = run.holder();
        if (holder != null && !holder.equals(teamId)) {
            run.reset();
            updates.add(TotemUpdate.of(TotemUpdate.Type.RESET, TotemMessages.RESET, teamId,
                    "event", definition.displayName(), "previous", holder.toString(), "id", definition.id()));
            if (definition.rivalBreak() == RivalBreak.RESET) {
                return updates;
            }
        }
        run.breakLevel(teamId, level);
        if (run.brokenCount() >= definition.height()) {
            current.compareAndSet(run, null);
            updates.add(TotemUpdate.of(TotemUpdate.Type.WON, TotemMessages.WON, teamId,
                    "event", definition.displayName(), "height", String.valueOf(definition.height()),
                    "id", definition.id()));
            return updates;
        }
        updates.add(TotemUpdate.of(TotemUpdate.Type.BROKEN, TotemMessages.BROKEN, teamId,
                "event", definition.displayName(), "broken", String.valueOf(run.brokenCount()),
                "height", String.valueOf(definition.height()), "id", definition.id()));
        return updates;
    }

    /** Advances the schedule and the hard stop; opens a scheduled run. */
    public List<TotemUpdate> tick() {
        long now = clock.getAsLong();
        TotemSettings config = settings.get();
        List<TotemUpdate> updates = new ArrayList<>();
        if (!config.enabled()) {
            lastScheduleCheck = now;
            return updates;
        }
        long previous = lastScheduleCheck;
        lastScheduleCheck = now;
        if (previous != 0L && current.get() == null) {
            for (TotemDefinition definition : config.definitions()) {
                if (DailySchedule.occursWithin(definition.schedule(), config.timeZone(), previous, now)) {
                    start(definition).ifPresent(updates::add);
                    break;
                }
            }
        }
        TotemRun run = current.get();
        if (run != null && run.getDefinition().maxDurationSeconds() > 0
                && now - run.getStartedAt() >= run.getDefinition().maxDurationSeconds() * 1000L) {
            current.compareAndSet(run, null);
            updates.add(TotemUpdate.of(TotemUpdate.Type.EXPIRED, TotemMessages.EXPIRED, null,
                    "event", run.getDefinition().displayName(), "id", run.getDefinition().id()));
        }
        return updates;
    }
}
