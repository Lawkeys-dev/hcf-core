package com.lawkeys.hcfcore.events.core;

/**
 * How a core run decides its winner, deduced from {@link CoreEventDefinition#kind()}
 * and, for a DTC, {@link CoreEventDefinition#counter()} - see
 * {@link CoreEventDefinition#winRule()}. Never read from configuration directly.
 */
public enum CoreWinRule {

    /** DTC + {@link CounterMode#SHARED}: common health; most breaks of its own wins the tie. */
    MOST_BREAKS,

    /** DTC + {@link CounterMode#PER_TEAM}: first team to {@code breaks} of its own wins at once. */
    FIRST_TO_TARGET,

    /** Last Break: common health; whoever lands the break that empties it wins. */
    LAST_BREAK
}
