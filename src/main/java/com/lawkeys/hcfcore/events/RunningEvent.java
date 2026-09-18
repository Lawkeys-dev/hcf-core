package com.lawkeys.hcfcore.events;

import com.lawkeys.hcfcore.util.Durations;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The mutable state of one event while it is running.
 *
 * <p>Package-private mutators: only {@link EventManager} drives a run, so the
 * rules cannot be bypassed by a command or a listener reaching in. Everything a
 * caller needs to display is exposed read-only.
 */
public final class RunningEvent {

    /** What the zone looked like at the last tick. Drives transition-only messages. */
    enum Phase {
        /** Nobody, or nobody who can hold it, is inside. */
        EMPTY,
        /** More than one party is inside, so the countdown is frozen. */
        CONTESTED,
        /** Exactly one team is inside and the countdown is running. */
        HELD
    }

    private final CaptureEventDefinition definition;
    private final long startedAt;

    private long remainingMillis;
    private long lastTickAt;
    private UUID holderTeamId;
    private Phase phase = Phase.EMPTY;

    /**
     * Milestones already broadcast for the current attempt.
     *
     * <p>Cleared whenever the countdown goes back to full, so a KOTH that is reset
     * at the last second announces its marks again on the next attempt instead of
     * silently counting down.
     */
    private final Set<Long> announcedMarks = new HashSet<>();

    RunningEvent(CaptureEventDefinition definition, long now) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.startedAt = now;
        this.lastTickAt = now;
        this.remainingMillis = definition.captureMillis();
    }

    public CaptureEventDefinition getDefinition() {
        return definition;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getRemainingMillis() {
        return remainingMillis;
    }

    /** @return remaining whole seconds, rounded up so "1s left" never displays as 0 */
    public long getRemainingSeconds() {
        return Durations.secondsLeft(remainingMillis);
    }

    /**
     * @return the team holding the zone, or {@code null} when nobody does
     *
     * <p>Survives a contest on purpose: while an enemy is in the zone the capture
     * is frozen rather than lost, so the holder is still the holder. Use
     * {@link #isContested()} to tell a frozen capture from a running one.
     */
    public UUID getHolderTeamId() {
        return holderTeamId;
    }

    public boolean isContested() {
        return phase == Phase.CONTESTED;
    }

    Phase getPhase() {
        return phase;
    }

    void setPhase(Phase phase) {
        this.phase = phase;
    }

    void setHolderTeamId(UUID holderTeamId) {
        this.holderTeamId = holderTeamId;
    }

    long getLastTickAt() {
        return lastTickAt;
    }

    void setLastTickAt(long lastTickAt) {
        this.lastTickAt = lastTickAt;
    }

    void decrease(long millis) {
        this.remainingMillis = Math.max(0L, this.remainingMillis - millis);
    }

    void resetCountdown() {
        this.remainingMillis = definition.captureMillis();
        this.announcedMarks.clear();
    }

    /** @return true the first time this mark is claimed, false afterwards */
    boolean claimMark(long mark) {
        return announcedMarks.add(mark);
    }
}
