package com.lawkeys.hcfcore.claim;

import java.util.Optional;

/**
 * Whether players may claim new land right now.
 *
 * <p>Asked for player claims only: staff claiming server land - a road during
 * the last days of a map - is never refused by it. Declared here and answered by a
 * module written later (EOTW closes claiming), in the manner of
 * {@link RaidabilityPolicy}; until then {@link #OPEN} changes nothing.
 */
@FunctionalInterface
public interface ClaimingPolicy {

    /** @return the language key explaining why players may not claim, or empty when they may */
    Optional<String> refusal();

    ClaimingPolicy OPEN = Optional::empty;
}
