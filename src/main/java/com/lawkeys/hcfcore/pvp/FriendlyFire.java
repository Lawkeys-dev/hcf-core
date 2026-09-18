package com.lawkeys.hcfcore.pvp;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Whether a hit between two players on the same side may land.
 *
 * <p>The project owner's rule, 13/09/2026: <strong>teammates never hurt each
 * other; allies only in an event area</strong> - a running KOTH, Citadel or
 * Conquest zone, or a hit on or by the King during Kill the King - because that is
 * where allied teams compete: captures and the King are strictly per team. Both
 * are settings ({@code pvp.yml}, {@code friendly-fire}), those being the defaults.
 */
public enum FriendlyFire {

    /** Not on the same side, or allowed to fight here. */
    ALLOW,
    /** Refused: the victim is a teammate. */
    TEAMMATE,
    /** Refused: the victim is an ally, and neither player is in an event area. */
    ALLY;

    /** Where allies may hurt each other. */
    public enum AllyRule {
        ALWAYS,
        EVENT_AREAS,
        NEVER
    }

    /**
     * @param attackerTeam the attacker's team, or {@code null} for none
     * @param victimTeam   the victim's team, or {@code null} for none
     * @param allied       whether the two teams are allies
     * @param inEventArea  whether either player is in an event area; asked only for
     *                     two allies under {@link AllyRule#EVENT_AREAS}, since
     *                     answering it means looking through the running events
     */
    public static FriendlyFire judge(UUID attackerTeam, UUID victimTeam, boolean allied,
                                     BooleanSupplier inEventArea, PvpSettings.FriendlyFireRules rules) {
        Objects.requireNonNull(inEventArea, "inEventArea");
        Objects.requireNonNull(rules, "rules");
        if (attackerTeam == null || victimTeam == null) {
            return ALLOW;
        }
        if (attackerTeam.equals(victimTeam)) {
            return rules.teammates() ? ALLOW : TEAMMATE;
        }
        if (!allied) {
            return ALLOW;
        }
        return switch (rules.allies()) {
            case ALWAYS -> ALLOW;
            case NEVER -> ALLY;
            case EVENT_AREAS -> inEventArea.getAsBoolean() ? ALLOW : ALLY;
        };
    }
}
