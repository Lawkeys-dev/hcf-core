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
 * @param announceIntervalSeconds  how often the King's position and health go to
 *                                 chat; {@code 0} for never. The position is on the
 *                                 scoreboard all along ({@code %king_location_line%})
 * @param penalty                  what leaving the warzone costs the King
 * @param kit                      what the King is given for the reign
 * @param rewardCommands           console commands run for the winner, with
 *                                 {@code %player%} and {@code %event%} substituted.
 *                                 Unlike a capture, the winner here is a player:
 *                                 the killer, or the King
 * @param mode                     whether the King's team defends them, or everybody
 *                                 is against the King (see {@link KingMode})
 * @param reign                    what the crown asks of the King and what dying
 *                                 with it costs (see {@link ReignRules})
 */
public record KingEventDefinition(String id,
                                  String displayName,
                                  String world,
                                  long durationSeconds,
                                  int minimumPlayers,
                                  List<Long> announceAtSeconds,
                                  List<LocalTime> schedule,
                                  long announceIntervalSeconds,
                                  OutsidePenalty penalty,
                                  KingKit kit,
                                  List<String> rewardCommands,
                                  KingMode mode,
                                  ReignRules reign) {

    public KingEventDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(penalty, "penalty");
        Objects.requireNonNull(kit, "kit");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(reign, "reign");
        announceAtSeconds = List.copyOf(Objects.requireNonNull(announceAtSeconds, "announceAtSeconds"));
        schedule = List.copyOf(Objects.requireNonNull(schedule, "schedule"));
        rewardCommands = List.copyOf(Objects.requireNonNull(rewardCommands, "rewardCommands"));
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("durationSeconds must be positive for event " + id);
        }
        if (minimumPlayers < 1) {
            throw new IllegalArgumentException("minimumPlayers must be at least 1 for event " + id);
        }
        if (announceIntervalSeconds < 0) {
            throw new IllegalArgumentException("announceIntervalSeconds cannot be negative for event " + id);
        }
    }

    /** A team Kill the King: the mode every event had before the choice existed. */
    public KingEventDefinition(String id, String displayName, String world, long durationSeconds,
                               int minimumPlayers, List<Long> announceAtSeconds, List<LocalTime> schedule,
                               long announceIntervalSeconds, OutsidePenalty penalty, KingKit kit,
                               List<String> rewardCommands) {
        this(id, displayName, world, durationSeconds, minimumPlayers, announceAtSeconds, schedule,
                announceIntervalSeconds, penalty, kit, rewardCommands, KingMode.TEAM);
    }

    /** With the default reign: armour locked, kit gone at death, no DTR, no deathban. */
    public KingEventDefinition(String id, String displayName, String world, long durationSeconds,
                               int minimumPlayers, List<Long> announceAtSeconds, List<LocalTime> schedule,
                               long announceIntervalSeconds, OutsidePenalty penalty, KingKit kit,
                               List<String> rewardCommands, KingMode mode) {
        this(id, displayName, world, durationSeconds, minimumPlayers, announceAtSeconds, schedule,
                announceIntervalSeconds, penalty, kit, rewardCommands, mode, ReignRules.defaults());
    }
}
