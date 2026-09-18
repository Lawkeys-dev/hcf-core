package com.lawkeys.hcfcore.events.king;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * One configured Kill the King event, as read from {@code events.yml}.
 *
 * <p>Not a {@code CaptureEventDefinition}, and deliberately so (ARCHITECTURE.md
 * section 9): KTK has no zone to hold and no team that holds it. It has one
 * player, a win condition that is a death or its absence, and a boundary that
 * punishes instead of counting. Forcing it into the capture shape would drag a
 * holder team and a capture countdown through code that can have neither.
 *
 * <p>There is no zone here: the event zone is the warzone of {@link #world},
 * as configured in {@code claims.yml}, so the two can never disagree.
 *
 * @param id                       stable key, shared with the capture events for
 *                                 {@code /events start|stop} and so unique among them
 * @param displayName              what players see, colour codes included
 * @param world                    the world whose warzone is the event zone
 * @param durationSeconds          how long the King must survive
 * @param minimumPlayers           eligible players needed online for a King to be
 *                                 drawn; below that the event is called off
 * @param announceAtSeconds        remaining-time marks to broadcast
 * @param schedule                 local times of day at which it starts by itself
 * @param coordinatesIntervalTicks how often the King's position goes to chat
 * @param penalty                  what leaving the warzone costs the King
 * @param kit                      what the King is given for the reign
 * @param rewardCommands           console commands run for the winner, with
 *                                 {@code %player%} and {@code %event%} substituted.
 *                                 Unlike a capture, the winner here is a player:
 *                                 the killer, or the King
 */
public record KingEventDefinition(String id,
                                  String displayName,
                                  String world,
                                  long durationSeconds,
                                  int minimumPlayers,
                                  List<Long> announceAtSeconds,
                                  List<LocalTime> schedule,
                                  long coordinatesIntervalTicks,
                                  OutsidePenalty penalty,
                                  KingKit kit,
                                  List<String> rewardCommands) {

    public KingEventDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(penalty, "penalty");
        Objects.requireNonNull(kit, "kit");
        announceAtSeconds = List.copyOf(Objects.requireNonNull(announceAtSeconds, "announceAtSeconds"));
        schedule = List.copyOf(Objects.requireNonNull(schedule, "schedule"));
        rewardCommands = List.copyOf(Objects.requireNonNull(rewardCommands, "rewardCommands"));
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("durationSeconds must be positive for event " + id);
        }
        if (minimumPlayers < 1) {
            throw new IllegalArgumentException("minimumPlayers must be at least 1 for event " + id);
        }
        if (coordinatesIntervalTicks < 0) {
            throw new IllegalArgumentException("coordinatesIntervalTicks cannot be negative for event " + id);
        }
    }
}
