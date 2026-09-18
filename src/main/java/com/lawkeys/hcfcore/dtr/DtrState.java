package com.lawkeys.hcfcore.dtr;

import java.util.Objects;
import java.util.UUID;

/**
 * A team's persisted DTR state.
 *
 * <p>Deliberately <strong>not</strong> the team's current DTR: that is a function
 * of time, computed by {@link DtrManager#getDtr}. What is stored is the pair
 * needed to compute it - the value at the last mutation, and the instant from
 * which regeneration resumes.
 *
 * <p>Storing a derived value instead would mean a scheduled task writing to every
 * team forever, and a value that drifts whenever a tick is missed.
 *
 * @param stored  the DTR at the moment of the last death or staff override
 * @param regenAt epoch millis from which regeneration resumes; while
 *                {@code now < regenAt} the team is frozen at {@code stored}
 */
public record DtrState(UUID teamId, double stored, long regenAt) {

    public DtrState {
        Objects.requireNonNull(teamId, "teamId");
    }

    public DtrState withStored(double stored) {
        return new DtrState(teamId, stored, regenAt);
    }

    public DtrState withRegenAt(long regenAt) {
        return new DtrState(teamId, stored, regenAt);
    }
}
