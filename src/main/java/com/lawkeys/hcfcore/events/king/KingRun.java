package com.lawkeys.hcfcore.events.king;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The state of a Kill the King run.
 *
 * <p>Package-private mutators, as for {@code RunningEvent}: only
 * {@link KingEventManager} drives a run.
 */
public final class KingRun {

    enum Phase {
        /** Opened: a spot and a King are being found. Nobody is King yet. */
        CROWNING,
        /** A King is out there. */
        REIGNING
    }

    private final KingEventDefinition definition;

    private Phase phase = Phase.CROWNING;
    private UUID kingId;
    private UUID kingTeamId;
    private long endsAt;
    private long lastTickAt;
    /** When the current excursion outside the warzone began, or {@link #INSIDE} while the King is inside. */
    private long outsideSince = INSIDE;
    static final long INSIDE = -1L;
    private final Set<Long> announcedMarks = new HashSet<>();

    KingRun(KingEventDefinition definition) {
        this.definition = Objects.requireNonNull(definition, "definition");
    }

    public KingEventDefinition getDefinition() {
        return definition;
    }

    /** @return the King, or {@code null} while one is still being found */
    public UUID getKingId() {
        return kingId;
    }

    /** @return the King's team at the crowning, or {@code null} if they had none */
    public UUID getKingTeamId() {
        return kingTeamId;
    }

    public boolean isReigning() {
        return phase == Phase.REIGNING;
    }

    public long getEndsAt() {
        return endsAt;
    }

    Phase getPhase() {
        return phase;
    }

    void crown(UUID kingId, UUID kingTeamId, long now) {
        this.phase = Phase.REIGNING;
        this.kingId = kingId;
        this.kingTeamId = kingTeamId;
        this.endsAt = now + definition.durationSeconds() * 1000L;
        this.lastTickAt = now;
    }

    long getLastTickAt() {
        return lastTickAt;
    }

    void setLastTickAt(long lastTickAt) {
        this.lastTickAt = lastTickAt;
    }

    long getOutsideSince() {
        return outsideSince;
    }

    void setOutsideSince(long outsideSince) {
        this.outsideSince = outsideSince;
    }

    /** @return true the first time this mark is claimed, false afterwards */
    boolean claimMark(long mark) {
        return announcedMarks.add(mark);
    }
}
