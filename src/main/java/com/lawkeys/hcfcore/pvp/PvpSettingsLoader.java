package com.lawkeys.hcfcore.pvp;

import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Turns {@code pvp.yml} into an immutable {@link PvpSettings}.
 *
 * <p>Invalid values are reported and replaced by the built-in default rather than
 * taking the server down (ARCHITECTURE.md section 6).
 */
public final class PvpSettingsLoader {

    private PvpSettingsLoader() {
    }

    public static PvpSettings load(ConfigurationSection section, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        PvpSettings defaults = PvpSettings.defaults();
        if (section == null) {
            warn.accept("pvp.yml is missing or empty - using built-in defaults for the PvP module.");
            return defaults;
        }

        return new PvpSettings(
                section.getBoolean("enabled", defaults.enabled()),
                loadDeathban(section.getConfigurationSection("deathban"), defaults.deathban(), warn),
                loadCombatTag(section.getConfigurationSection("combat-tag"), defaults.combatTag(), warn),
                loadStrength(section.getConfigurationSection("strength-nerf"), defaults.strength(), warn),
                loadKnockback(section.getConfigurationSection("knockback"), defaults.knockback()),
                loadAttackSpeed(section.getConfigurationSection("attack-speed"), defaults.attackSpeed(), warn),
                loadSafeZones(section.getConfigurationSection("safe-zones"), defaults.safeZones()),
                loadLootProtection(section.getConfigurationSection("loot-protection"), defaults.lootProtection()),
                loadFriendlyFire(section.getConfigurationSection("friendly-fire"), defaults.friendlyFire(), warn),
                loadEnderPearl(section.getConfigurationSection("ender-pearl-cooldown"), defaults.enderPearl(), warn));
    }

    private static PvpSettings.DeathbanRules loadDeathban(ConfigurationSection section,
                                                         PvpSettings.DeathbanRules defaults,
                                                         Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        long duration = Math.max(0L, Durations.capSeconds(section.getLong("duration-seconds", defaults.defaultSeconds()), "duration-seconds", warn));

        Map<String, Long> tiers = new LinkedHashMap<>();
        ConfigurationSection tierSection = section.getConfigurationSection("permission-tiers");
        if (tierSection != null) {
            // Deep keys, leaves only: a permission node is dotted, and Bukkit's YAML
            // reads every dot as a level - "hcfcore.deathban.tier.short: 900" arrives
            // as a section "hcfcore" holding "deathban" holding... The full path of a
            // leaf is the node. Reading the top level only saw "hcfcore", a section,
            // and dropped the shipped tier with a warning on the first real start.
            for (String permission : tierSection.getKeys(true)) {
                if (tierSection.isConfigurationSection(permission)) {
                    continue;
                }
                long seconds = tierSection.getLong(permission, -1L);
                if (seconds < 0) {
                    warn.accept("deathban.permission-tiers." + permission
                            + " must be a number of seconds; ignored.");
                    continue;
                }
                tiers.put(permission, seconds);
            }
        }
        return new PvpSettings.DeathbanRules(
                section.getBoolean("enabled", defaults.enabled()), duration, tiers);
    }

    private static PvpSettings.CombatTagRules loadCombatTag(ConfigurationSection section,
                                                           PvpSettings.CombatTagRules defaults, Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        return new PvpSettings.CombatTagRules(
                section.getBoolean("enabled", defaults.enabled()),
                Math.max(0L, Durations.capSeconds(section.getLong("duration-seconds", defaults.durationSeconds()), "duration-seconds", warn)),
                section.getBoolean("tag-attacker", defaults.tagAttacker()),
                section.getBoolean("kill-on-logout", defaults.killOnLogout()),
                section.getBoolean("block-teleport", defaults.blockTeleport()));
    }

    private static PvpSettings.EnderPearlRules loadEnderPearl(ConfigurationSection section,
                                                             PvpSettings.EnderPearlRules defaults, Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        return new PvpSettings.EnderPearlRules(
                section.getBoolean("enabled", defaults.enabled()),
                Math.max(0L, Durations.capSeconds(section.getLong("seconds", defaults.seconds()), "seconds", warn)),
                section.getBoolean("show-on-item", defaults.showOnItem()),
                section.getBoolean("clear-on-death", defaults.clearOnDeath()),
                section.getBoolean("block-teleport", defaults.blockTeleport()));
    }

    private static PvpSettings.StrengthRules loadStrength(ConfigurationSection section,
                                                         PvpSettings.StrengthRules defaults,
                                                         Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        double vanilla = section.getDouble("vanilla-bonus-per-level", defaults.vanillaBonusPerLevel());
        if (vanilla < 0) {
            warn.accept("strength-nerf.vanilla-bonus-per-level cannot be negative; using "
                    + defaults.vanillaBonusPerLevel() + ".");
            vanilla = defaults.vanillaBonusPerLevel();
        }
        return new PvpSettings.StrengthRules(
                section.getBoolean("enabled", defaults.enabled()),
                vanilla,
                Math.max(0.0, section.getDouble("nerfed-bonus-per-level",
                        defaults.nerfedBonusPerLevel())));
    }

    private static PvpSettings.KnockbackRules loadKnockback(ConfigurationSection section,
                                                           PvpSettings.KnockbackRules defaults) {
        if (section == null) {
            return defaults;
        }
        return new PvpSettings.KnockbackRules(
                section.getBoolean("enabled", defaults.enabled()),
                Math.max(0.0, section.getDouble("horizontal", defaults.horizontal())),
                Math.max(0.0, section.getDouble("vertical", defaults.vertical())));
    }

    private static PvpSettings.AttackSpeedRules loadAttackSpeed(ConfigurationSection section,
                                                               PvpSettings.AttackSpeedRules defaults,
                                                               Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        double value = section.getDouble("value", defaults.value());
        if (!(value > 0.0)) {
            warn.accept("attack-speed.value must be above 0, got " + value + "; using " + defaults.value() + ".");
            value = defaults.value();
        }
        return new PvpSettings.AttackSpeedRules(section.getBoolean("enabled", defaults.enabled()), value);
    }

    private static PvpSettings.LootProtectionRules loadLootProtection(ConfigurationSection section,
                                                                     PvpSettings.LootProtectionRules defaults) {
        if (section == null) {
            return defaults;
        }
        return new PvpSettings.LootProtectionRules(
                section.getBoolean("enabled", defaults.enabled()),
                Math.max(0L, section.getLong("seconds", defaults.seconds())),
                section.getBoolean("team-shares", defaults.teamShares()));
    }

    private static PvpSettings.FriendlyFireRules loadFriendlyFire(ConfigurationSection section,
                                                                 PvpSettings.FriendlyFireRules defaults,
                                                                 Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        FriendlyFire.AllyRule allies = defaults.allies();
        String raw = section.getString("allies");
        if (raw != null) {
            try {
                allies = FriendlyFire.AllyRule.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException e) {
                warn.accept("friendly-fire.allies: '" + raw + "' is not ALWAYS, EVENT_AREAS or NEVER; using "
                        + defaults.allies() + ".");
            }
        }
        return new PvpSettings.FriendlyFireRules(section.getBoolean("teammates", defaults.teammates()), allies);
    }

    private static PvpSettings.SafeZoneRules loadSafeZones(ConfigurationSection section,
                                                          PvpSettings.SafeZoneRules defaults) {
        if (section == null) {
            return defaults;
        }
        return new PvpSettings.SafeZoneRules(section.getBoolean("enabled", defaults.enabled()));
    }
}
