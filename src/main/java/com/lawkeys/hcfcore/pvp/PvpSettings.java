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
        FriendlyFireRules friendlyFire,
        EnderPearlRules enderPearl,
        ItemCooldownRules itemCooldowns) {

    public PvpSettings {
        Objects.requireNonNull(deathban, "deathban");
        Objects.requireNonNull(combatTag, "combatTag");
        Objects.requireNonNull(strength, "strength");
        Objects.requireNonNull(knockback, "knockback");
        Objects.requireNonNull(attackSpeed, "attackSpeed");
        Objects.requireNonNull(safeZones, "safeZones");
        Objects.requireNonNull(lootProtection, "lootProtection");
        Objects.requireNonNull(friendlyFire, "friendlyFire");
        Objects.requireNonNull(enderPearl, "enderPearl");
        Objects.requireNonNull(itemCooldowns, "itemCooldowns");
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
     * @param killOnLogout   kept for files written before {@code logout}: false meant
     *                       nothing happens to a tagged player who disconnects
     * @param blockTeleport  refuse plugin teleports (such as {@code /team hq}) while tagged
     * @param logout         what a tagged player leaving leaves behind: a stand-in to
     *                       kill ({@code NPC}, the owner's choice of 28/09/2026), their
     *                       death at once ({@code KILL}), or nothing ({@code NONE})
     * @param loggerEntity   the stand-in's kind, as the server spells it
     * @param loggerHealth   its health, in half hearts
     */
    public record CombatTagRules(boolean enabled, long durationSeconds, boolean tagAttacker,
                                 boolean killOnLogout, boolean blockTeleport, LogoutAction logout,
                                 String loggerEntity, double loggerHealth) {

        public CombatTagRules {
            Objects.requireNonNull(logout, "logout");
            loggerEntity = loggerEntity == null || loggerEntity.isBlank() ? "VILLAGER" : loggerEntity.trim();
            loggerHealth = Math.max(1.0, Math.min(1024.0, loggerHealth));
        }

        public CombatTagRules(boolean enabled, long durationSeconds, boolean tagAttacker, boolean killOnLogout,
                              boolean blockTeleport) {
            this(enabled, durationSeconds, tagAttacker, killOnLogout, blockTeleport,
                    killOnLogout ? LogoutAction.KILL : LogoutAction.NONE, "VILLAGER", 20.0);
        }
    }

    /** What a combat-tagged player who disconnects leaves behind. */
    public enum LogoutAction {
        /** A stand-in, with their health: killed, they die; it outlives the tag, they are safe. */
        NPC,
        /** Their death, at once. */
        KILL,
        /** Nothing. */
        NONE
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
     * @param enabled  disable PvP inside server-owned (system) team territory - the
     *                 spawn and warzone safe zones of FEATURES.md section 4
     * @param noDamage no damage of any kind on safe-zone land - fall, fire, drowning,
     *                 a mob - not only no PvP
     * @param keepFed  hunger never drops there, and is filled back up on arrival
     * @param heal     health is filled back up there too
     * @param blockCombatTagged a player in combat cannot enter until their tag runs
     *                 out - the classic HCF rule, so a fight is not ended by running
     *                 home to spawn
     */
    public record SafeZoneRules(boolean enabled, boolean noDamage, boolean keepFed, boolean heal,
                                boolean blockCombatTagged, WallRules wall) {

        public SafeZoneRules {
            Objects.requireNonNull(wall, "wall");
        }
    }

    /**
     * The wall a player in combat sees around a safe zone they may not enter.
     *
     * @param radiusBlocks the border is drawn within this distance of the player, all
     *                     round - a ball of wall where they stand, rather than a
     *                     sheet up to the sky (the owner's choice of 28/09/2026: far
     *                     fewer blocks read and sent)
     */
    public record WallRules(boolean enabled, String material, int radiusBlocks) {

        public WallRules {
            Objects.requireNonNull(material, "material");
            radiusBlocks = Math.max(1, Math.min(32, radiusBlocks));
        }
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
     * The HCF ender pearl cooldown: a wait between two pearls.
     *
     * @param showOnItem    the pearls in the hotbar show the wait as the game shows its own
     * @param clearOnDeath  a death ends it
     * @param blockTeleport refuse plugin teleports (such as {@code /team hq}) until it is over
     */
    public record EnderPearlRules(boolean enabled, long seconds, boolean showOnItem, boolean clearOnDeath,
                                  boolean blockTeleport) {
    }

    /**
     * A wait between two uses of an item - eating a golden apple, a totem saving its
     * holder. Kept on the player, so a long one survives logouts and restarts.
     *
     * @param id           the name in {@code pvp.yml}, and in the {@code %cooldown_<id>%} placeholders
     * @param material     the item, upper case
     * @param name         how the scoreboard and the messages call it; colour codes allowed
     * @param showOnItem   the items in the hotbar show the wait as the game shows its own
     * @param clearOnDeath a death ends it
     */
    public record ItemCooldown(String id, String material, long seconds, String name, boolean showOnItem,
                               boolean clearOnDeath) {
        public ItemCooldown {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(name, "name");
        }
    }

    public record ItemCooldownRules(boolean enabled, java.util.List<ItemCooldown> items) {
        public ItemCooldownRules {
            items = java.util.List.copyOf(Objects.requireNonNull(items, "items"));
        }

        /** @return the cooldown of this item, if it has one */
        public java.util.Optional<ItemCooldown> of(String material) {
            return items.stream().filter(item -> item.material().equals(material)).findFirst();
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
                new CombatTagRules(true, 30L, true, true, true, LogoutAction.NPC, "VILLAGER", 20.0),
                new StrengthRules(true, 3.0, 1.5),
                new KnockbackRules(false, 1.0, 1.0),
                new AttackSpeedRules(false, 4.0),
                new SafeZoneRules(true, true, true, true, true,
                        new WallRules(true, "RED_STAINED_GLASS", 15)),
                new LootProtectionRules(true, 10L, true),
                new FriendlyFireRules(false, FriendlyFire.AllyRule.EVENT_AREAS),
                new EnderPearlRules(true, 15L, true, true, true),
                new ItemCooldownRules(true, java.util.List.of(
                        new ItemCooldown("notch-apple", "ENCHANTED_GOLDEN_APPLE", 3600L, "{primary}Gapple", true, false),
                        new ItemCooldown("golden-apple", "GOLDEN_APPLE", 10L, "{primary}Crapple", true, true),
                        new ItemCooldown("chorus-fruit", "CHORUS_FRUIT", 15L, "{primary}Chorus", true, true),
                        new ItemCooldown("totem", "TOTEM_OF_UNDYING", 120L, "{primary}Totem", true, false))));
    }
}
