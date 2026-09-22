package com.lawkeys.hcfcore.events.slide;

import com.lawkeys.hcfcore.util.Cuboid;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * One Slide, as read from {@code events.yml}: a zone where every team member
 * present scores for their team every {@code interval-seconds}, and a death
 * anywhere costs the team {@code death-penalty} points.
 *
 * @param pointsPerPlayer points ONE member in the zone brings their team, per
 *                        interval - cumulative: three members is three times this
 * @param intervalSeconds how often points are awarded
 * @param deathPenalty    points a team loses when one of its members dies while
 *                        the Slide runs, anywhere on the server; never below zero
 * @param announceDeaths  whether a death's point loss is broadcast; the points
 *                        are always lost either way
 * @param pointsToWin     the target
 * @param announceAt      points marks to broadcast once per team, ascending
 */
public record SlideDefinition(String id,
                              String displayName,
                              Cuboid zone,
                              int pointsPerPlayer,
                              long intervalSeconds,
                              int deathPenalty,
                              boolean announceDeaths,
                              int pointsToWin,
                              List<Integer> announceAt,
                              List<LocalTime> schedule,
                              long maxDurationSeconds,
                              List<String> rewardCommands) {

    public SlideDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(zone, "zone");
        announceAt = List.copyOf(Objects.requireNonNull(announceAt, "announceAt"));
        schedule = List.copyOf(Objects.requireNonNull(schedule, "schedule"));
        rewardCommands = List.copyOf(Objects.requireNonNull(rewardCommands, "rewardCommands"));
        if (pointsPerPlayer <= 0 || intervalSeconds <= 0 || pointsToWin <= 0) {
            throw new IllegalArgumentException("points-per-player, interval-seconds and points-to-win must be "
                    + "positive for Slide " + id);
        }
        if (deathPenalty < 0) {
            throw new IllegalArgumentException("death-penalty must not be negative for Slide " + id);
        }
    }

    public long intervalMillis() {
        return intervalSeconds * 1000L;
    }
}
