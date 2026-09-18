package com.lawkeys.hcfcore.events.conquest;

import com.lawkeys.hcfcore.events.ContestPolicy;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * A Conquest, as {@code events.yml} describes it.
 *
 * <p>The classic HCF ruleset the project owner chose on 12/09/2026: several zones
 * captured in parallel, each on a short timer; every capture gives the holding team
 * points and the zone starts again; the first team to reach the target wins; a
 * member's death costs the team points.
 *
 * @param captureSeconds     how long a team must hold a zone to score from it
 * @param pointsPerCapture   what one capture of one zone is worth
 * @param pointsToWin        the target
 * @param deathPenalty       points a team loses when one of its members dies while
 *                           the Conquest runs; never below zero
 * @param contestPolicy      what losing a zone does to its countdown, as for a KOTH
 * @param maxDurationSeconds a hard stop with no winner; {@code 0} for none
 */
public record ConquestDefinition(String id,
                                 String displayName,
                                 List<ConquestZone> zones,
                                 long captureSeconds,
                                 int pointsPerCapture,
                                 int pointsToWin,
                                 int deathPenalty,
                                 ContestPolicy contestPolicy,
                                 List<LocalTime> schedule,
                                 long maxDurationSeconds,
                                 List<String> rewardCommands) {

    public ConquestDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        zones = List.copyOf(zones);
        Objects.requireNonNull(contestPolicy, "contestPolicy");
        schedule = List.copyOf(schedule);
        rewardCommands = List.copyOf(rewardCommands);
        if (zones.isEmpty()) {
            throw new IllegalArgumentException("a Conquest needs at least one zone");
        }
        if (captureSeconds <= 0 || pointsPerCapture <= 0 || pointsToWin <= 0 || deathPenalty < 0) {
            throw new IllegalArgumentException("capture-seconds, points-per-capture and points-to-win must be "
                    + "positive, and death-penalty not negative");
        }
    }

    public long captureMillis() {
        return captureSeconds * 1000L;
    }
}
