package com.lawkeys.hcfcore.pvp;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot of {@code pvp.yml}.
 *
 * <p>Held through a {@code Supplier} so {@code /hcf reload} applies at once
 * (ARCHITECTURE.md section 2).
 */
public record PvpSettings(
        boolean enabled,
        DeathbanRules deathban,
        CombatTagRules combatTag,
        StrengthRules strength,
        KnockbackRules knockback,
        AttackSpeedRules attackSpeed,
        SafeZoneRules safeZones,
        LootProtectionRules lootProtection,
        FriendlyFireRules friendlyFire) {

    public PvpSettings {
        Objects.requireNonNull(deathban, "deathban");
        Objects.requireNonNull(combatTag, "combatTag");
        Objects.requireNonNull(strength, "strength");
        Objects.requireNonNull(knockback, "knockback");
        Objects.requireNonNull(attackSpeed, "attackSpeed");
        Objects.requireNonNull(safeZones, "safeZones");
        Objects.requireNonNull(lootProtection, "lootProtection");
        Objects.requireNonNull(friendlyFire, "friendlyFire");
    }

    /**
     * @param defaultSeconds  ban length for a player with no matching tier
     * @param permissionTiers permission node to ban length in seconds; the
     *                        <em>shortest</em> matching tier wins, so granting a rank
     *                        is always a reduction and never accidentally a penalty
     */
    public record DeathbanRules(boolean enabled, long defaultSeconds,
                                Map<String, Long> permissionTiers) {

        public DeathbanRules {
            permissionTiers = Map.copyOf(Objects.requireNonNull(permissionTiers, "permissionTiers"));
        }
    }

    /**
     * @param tagAttacker    tag the attacker as well as the victim
     * @param killOnLogout   kill a tagged player who disconnects - the classic HCF
     *                       answer to combat logging
     * @param blockTeleport  refuse plugin teleports (such as {@code /team hq}) while tagged
     */
    public record CombatTagRules(boolean enabled, long durationSeconds, boolean tagAttacker,
                                 boolean killOnLogout, boolean blockTeleport) {
    }

    /**
     * The historic HCF strength nerf.
     *
     * @param vanillaBonusPerLevel  damage vanilla adds per Strength level, which this
     *                              module subtracts before adding its own. <strong>This is
     *                              a config value on purpose</strong>: Mojang has changed
     *                              the formula before (a multiplier pre-1.9, a flat bonus
     *                              after), and it could not be confirmed against the
     *                              official documentation for the targeted version at the
     *                              time of writing - CONTRIBUTING.md section 6 forbids presenting
     *                              a guess as fact. Verify it for your server version and
     *                              correct it here; no recompilation needed.
     * @param nerfedBonusPerLevel   damage this module adds per level instead
     */
    public record StrengthRules(boolean enabled, double vanillaBonusPerLevel,
                                double nerfedBonusPerLevel) {
    }

    /** @param horizontal multiplier on the horizontal knockback; {@code 1.0} is vanilla */
    public record KnockbackRules(boolean enabled, double horizontal, double vertical) {
    }

    /**
     * @param value the attack-speed attribute every player gets, before the modifier
     *              their weapon adds; it replaces the base value rather than adding to it
     */
    public record AttackSpeedRules(boolean enabled, double value) {
    }

    /**
     * @param enabled disable PvP inside server-owned (system) team territory - the
     *                spawn and warzone safe zones of FEATURES.md section 4
     */
    public record SafeZoneRules(boolean enabled) {
    }

    /**
     * Anticlean: a dead player's loot is the killer's for a while (see {@link LootClaim}).
     *
     * @param seconds    how long; {@code 0} is the same as off
     * @param teamShares the killer's team may pick it up too
     */
    public record LootProtectionRules(boolean enabled, long seconds, boolean teamShares) {
    }

    /**
     * @param teammates whether teammates may hurt each other
     * @param allies    where allies may - see {@link FriendlyFire}
     */
    public record FriendlyFireRules(boolean teammates, FriendlyFire.AllyRule allies) {
        public FriendlyFireRules {
            Objects.requireNonNull(allies, "allies");
        }
    }

    /**
     * Resolves a deathban length from the permission nodes a player holds.
     *
     * @param heldPermissions the tier nodes the player has
     * @return the shortest matching tier, or the default when none match
     */
    public long deathbanSecondsFor(Iterable<String> heldPermissions) {
        long shortest = Long.MAX_VALUE;
        for (String permission : heldPermissions) {
            Long tier = deathban.permissionTiers().get(permission);
            if (tier != null && tier < shortest) {
                shortest = tier;
            }
        }
        return shortest == Long.MAX_VALUE ? deathban.defaultSeconds() : shortest;
    }

    /** Built-in fallback, mirroring {@code resources/pvp.yml}. */
    public static PvpSettings defaults() {
        Map<String, Long> tiers = new LinkedHashMap<>();
        tiers.put("hcfcore.deathban.tier.short", 900L);

        return new PvpSettings(
                true,
                new DeathbanRules(true, 3600L, tiers),
                new CombatTagRules(true, 30L, true, true, true),
                new StrengthRules(true, 3.0, 1.5),
                new KnockbackRules(false, 1.0, 1.0),
                new AttackSpeedRules(false, 4.0),
                new SafeZoneRules(true),
                new LootProtectionRules(true, 10L, true),
                new FriendlyFireRules(false, FriendlyFire.AllyRule.EVENT_AREAS));
    }
}
