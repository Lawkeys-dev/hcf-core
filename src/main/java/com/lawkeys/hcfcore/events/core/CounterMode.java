package com.lawkeys.hcfcore.events.core;

/**
 * How a DTC counts breaks, {@code counter} in {@code events.yml}. Last Break has
 * no such key: it always plays {@link CoreWinRule#LAST_BREAK} instead.
 */
public enum CounterMode {

    /**
     * The core has {@code breaks} health points shared by every team. When it
     * reaches zero, the team with the most breaks of its own wins - ties broken
     * by whichever of them reached that count first ({@link CoreWinRule#MOST_BREAKS}).
     */
    SHARED,

    /**
     * Each team has its own count to {@code breaks}; the first to get there wins
     * at once ({@link CoreWinRule#FIRST_TO_TARGET}).
     */
    PER_TEAM
}
