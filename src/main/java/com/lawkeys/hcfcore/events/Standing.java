package com.lawkeys.hcfcore.events;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One team's tally in a family-A event under way - points for Conquest and
 * Slide, breaks for the core events (DTC, Last Break).
 *
 * <p>Extracted from {@code ConquestRun.Standing} once the core and Slide engines
 * needed the exact same ranking: the leader first, ties broken by id so the
 * order is stable across ticks.
 */
public record Standing(UUID teamId, int points) {

    /** @return every team with a positive tally, the leader first; ties broken by id */
    public static List<Standing> rank(Map<UUID, Integer> tally) {
        List<Standing> all = new ArrayList<>();
        tally.forEach((team, score) -> {
            if (score != null && score > 0) {
                all.add(new Standing(team, score));
            }
        });
        all.sort(Comparator.comparingInt(Standing::points).reversed().thenComparing(Standing::teamId));
        return all;
    }
}
