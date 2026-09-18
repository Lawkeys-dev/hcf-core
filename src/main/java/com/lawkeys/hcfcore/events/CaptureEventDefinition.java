package com.lawkeys.hcfcore.events;

import com.lawkeys.hcfcore.util.Cuboid;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * One configured capture event, as read from {@code events.yml}.
 *
 * <p>Immutable, and deliberately <em>data</em> rather than behaviour. This is what
 * makes ARCHITECTURE.md section 9's requirement work: adding a KOTH variant means
 * adding a block of YAML, not a subclass and a recompile. A classic KOTH and a
 * Citadel differ only in {@link #captureSeconds} and {@link #contestPolicy} - the
 * 30-minute continuous hold FEATURES.md describes for Citadel is
 * {@code capture-seconds: 1800} with {@code contest-policy: RESET}, and it needs
 * no code of its own.
 *
 * @param id              stable key, used in commands, config and messages
 * @param displayName     what players see, colour codes included
 * @param zone            the cuboid to hold
 * @param captureSeconds  seconds of uncontested holding needed to win
 * @param contestPolicy   what losing the zone does to the countdown
 * @param maxDurationSeconds hard stop after which an uncaptured event ends with no
 *                        winner; {@code 0} means it runs until somebody wins
 * @param announceAtSeconds remaining-time marks to broadcast, any order
 * @param schedule        local times of day at which this event starts by itself
 * @param rewardCommands  console commands run on capture, with {@code %team%} and
 *                        {@code %event%} substituted. There is deliberately no
 *                        {@code %player%}: a capture is credited to a team, not to
 *                        whoever happened to be standing in the zone on the winning
 *                        tick, so no player context exists to substitute
 */
public record CaptureEventDefinition(String id,
                                     String displayName,
                                     Cuboid zone,
                                     long captureSeconds,
                                     ContestPolicy contestPolicy,
                                     long maxDurationSeconds,
                                     List<Long> announceAtSeconds,
                                     List<LocalTime> schedule,
                                     List<String> rewardCommands) {

    public CaptureEventDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(contestPolicy, "contestPolicy");
        announceAtSeconds = List.copyOf(Objects.requireNonNull(announceAtSeconds, "announceAtSeconds"));
        schedule = List.copyOf(Objects.requireNonNull(schedule, "schedule"));
        rewardCommands = List.copyOf(Objects.requireNonNull(rewardCommands, "rewardCommands"));
        if (captureSeconds <= 0) {
            throw new IllegalArgumentException("captureSeconds must be positive for event " + id);
        }
    }

    public long captureMillis() {
        return captureSeconds * 1000L;
    }
}
