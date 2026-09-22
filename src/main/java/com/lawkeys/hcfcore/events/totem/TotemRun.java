package com.lawkeys.hcfcore.events.totem;

import java.util.BitSet;
import java.util.Objects;
import java.util.UUID;

/**
 * A Totem under way: which blocks of the column are broken, and by which team. Only
 * one team is ever on its way - a block broken by another starts the totem over.
 *
 * <p>Changed on the main thread only (block events and the event tick); read by the
 * scoreboard on the same thread.
 */
public final class TotemRun {

    private final TotemDefinition definition;
    private final long startedAt;
    private final BitSet broken = new BitSet();
    private UUID holder;
    private int resets;

    TotemRun(TotemDefinition definition, long startedAt) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.startedAt = startedAt;
    }

    public TotemDefinition getDefinition() {
        return definition;
    }

    public long getStartedAt() {
        return startedAt;
    }

    /** @return the team on its way to winning, or {@code null} while the totem is whole */
    public UUID holder() {
        return holder;
    }

    /** @return how many blocks of the column are broken */
    public int brokenCount() {
        return broken.cardinality();
    }

    public boolean isBroken(int level) {
        return broken.get(level);
    }

    /** @return how many times the totem has been started over */
    public int resets() {
        return resets;
    }

    void breakLevel(UUID team, int level) {
        holder = team;
        broken.set(level);
    }

    void reset() {
        broken.clear();
        holder = null;
        resets++;
    }
}
