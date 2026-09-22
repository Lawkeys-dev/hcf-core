package com.lawkeys.hcfcore.events.core;

import com.lawkeys.hcfcore.util.Cuboid;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * One DTC or Last Break, as read from {@code events.yml}.
 *
 * <p>Both play the same engine ({@link CoreEventManager}): a block cœur inside a
 * zone, broken over and over by teams standing in the zone, until a common or a
 * per-team count runs out. {@link #winRule()} is the one thing that actually
 * changes between the two - see {@link CoreWinRule}.
 *
 * @param kind                 DTC or LAST_BREAK
 * @param zone                 the zone the core stands in; a break outside a run
 *                             is not this engine's business
 * @param coreX/Y/Z            the core's block, which must be inside {@link #zone()}
 * @param coreMaterial         the block the core is made of while a run is on, and
 *                             reappears as after every break
 * @param coreIdleMaterial     what it stands as between runs - bedrock, as a Totem's
 *                             column does (the project owner's choice, 22/09/2026):
 *                             the core is scenery outside its event, and nothing but
 *                             the event should mark it as breakable
 * @param counter              SHARED or PER_TEAM, for a DTC; ignored for Last
 *                             Break, which is always common-health
 * @param breaks               the core's health (SHARED / Last Break) or the
 *                             per-team target (PER_TEAM)
 * @param breakCooldownSeconds delay between two breaks of the SAME team; other
 *                             teams are never blocked by it
 * @param announceAt           remaining-count marks to broadcast - remaining
 *                             health for SHARED/Last Break, remaining-to-target
 *                             for PER_TEAM (per team)
 */
public record CoreEventDefinition(String id,
                                  CoreEventKind kind,
                                  String displayName,
                                  Cuboid zone,
                                  int coreX,
                                  int coreY,
                                  int coreZ,
                                  String coreMaterial,
                                  String coreIdleMaterial,
                                  CounterMode counter,
                                  int breaks,
                                  long breakCooldownSeconds,
                                  List<Integer> announceAt,
                                  List<LocalTime> schedule,
                                  long maxDurationSeconds,
                                  List<String> rewardCommands) {

    public CoreEventDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(coreMaterial, "coreMaterial");
        Objects.requireNonNull(coreIdleMaterial, "coreIdleMaterial");
        Objects.requireNonNull(counter, "counter");
        announceAt = List.copyOf(Objects.requireNonNull(announceAt, "announceAt"));
        schedule = List.copyOf(Objects.requireNonNull(schedule, "schedule"));
        rewardCommands = List.copyOf(Objects.requireNonNull(rewardCommands, "rewardCommands"));
        if (breaks <= 0) {
            throw new IllegalArgumentException("breaks must be positive for core event " + id);
        }
        if (breakCooldownSeconds < 0) {
            throw new IllegalArgumentException("break-cooldown-seconds must not be negative for core event " + id);
        }
        if (!zone.containsBlock(zone.world(), coreX, coreY, coreZ)) {
            throw new IllegalArgumentException("the core of '" + id + "' must stand inside its own zone");
        }
    }

    /** @return the world and block of the core, as {@link Cuboid#containsBlock} takes it */
    public boolean isCoreBlock(String world, int x, int y, int z) {
        return zone.world().equalsIgnoreCase(world) && x == coreX && y == coreY && z == coreZ;
    }

    /**
     * @return how this run decides its winner - DTC+SHARED counts the most
     *         breaks, DTC+PER_TEAM the first to the target, Last Break the last
     *         team to land a break
     */
    public CoreWinRule winRule() {
        if (kind == CoreEventKind.LAST_BREAK) {
            return CoreWinRule.LAST_BREAK;
        }
        return counter == CounterMode.PER_TEAM ? CoreWinRule.FIRST_TO_TARGET : CoreWinRule.MOST_BREAKS;
    }
}
