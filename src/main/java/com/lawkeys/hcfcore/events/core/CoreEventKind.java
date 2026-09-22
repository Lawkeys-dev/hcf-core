package com.lawkeys.hcfcore.events.core;

/**
 * The two capture-a-core events, one engine ({@link CoreEventManager}).
 *
 * <p>DTC reads {@code counter} to choose between {@link CounterMode#SHARED} and
 * {@link CounterMode#PER_TEAM}; Last Break has no {@code counter} key at all and
 * always plays {@link CoreWinRule#LAST_BREAK} - see
 * {@link CoreEventDefinition#winRule()}.
 */
public enum CoreEventKind {
    DTC,
    LAST_BREAK
}
