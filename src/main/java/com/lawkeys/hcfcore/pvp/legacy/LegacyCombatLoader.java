package com.lawkeys.hcfcore.pvp.legacy;

import com.lawkeys.hcfcore.pvp.legacy.LegacyCombatSettings.Apple;
import com.lawkeys.hcfcore.pvp.legacy.LegacyCombatSettings.AppleEffect;
import com.lawkeys.hcfcore.pvp.legacy.LegacyCombatSettings.Throw;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reads {@code pvp.yml}'s {@code legacy-combat} section. A missing value takes its
 * 1.7.10 default; one that cannot be used is reported and replaced by it.
 */
public final class LegacyCombatLoader {

    private LegacyCombatLoader() {
    }

    public static LegacyCombatSettings load(ConfigurationSection section, Consumer<String> warn) {
        LegacyCombatSettings d = LegacyCombatSettings.defaults();
        if (section == null) {
            return d;
        }
        ConfigurationSection cooldown = section.getConfigurationSection("attack-cooldown");
        ConfigurationSection crits = section.getConfigurationSection("critical-hits");
        ConfigurationSection weapons = section.getConfigurationSection("weapon-damage");
        ConfigurationSection enchantments = section.getConfigurationSection("enchantments");
        ConfigurationSection blocking = section.getConfigurationSection("sword-blocking");
        ConfigurationSection knockback = section.getConfigurationSection("knockback");
        ConfigurationSection potions = section.getConfigurationSection("thrown-potions");
        ConfigurationSection pearls = section.getConfigurationSection("ender-pearls");
        ConfigurationSection regen = section.getConfigurationSection("natural-regeneration");
        ConfigurationSection apples = section.getConfigurationSection("golden-apples");
        ConfigurationSection strength = section.getConfigurationSection("strength");
        ConfigurationSection rod = section.getConfigurationSection("fishing-rod");

        var kb = d.knockback();
        return new LegacyCombatSettings(
                new LegacyCombatSettings.AttackCooldown(
                        bool(cooldown, "remove", d.attackCooldown().remove()),
                        positive(cooldown, "attack-speed", d.attackCooldown().attackSpeed(), "attack-cooldown", warn)),
                section.getBoolean("no-sweep-attacks", d.noSweepAttacks()),
                new LegacyCombatSettings.Criticals(
                        bool(crits, "enabled", d.criticals().enabled()),
                        positive(crits, "multiplier", d.criticals().multiplier(), "critical-hits", warn)),
                readWeapons(weapons, d.weaponDamage(), warn),
                new LegacyCombatSettings.Enchantments(
                        bool(enchantments, "enabled", d.enchantments().enabled()),
                        Math.max(0.0, number(enchantments, "sharpness-per-level",
                                d.enchantments().sharpnessPerLevel()))),
                new LegacyCombatSettings.SwordBlocking(
                        bool(blocking, "enabled", d.swordBlocking().enabled()),
                        number(blocking, "base", d.swordBlocking().base()),
                        number(blocking, "factor", d.swordBlocking().factor())),
                new LegacyCombatSettings.Knockback(
                        bool(knockback, "enabled", kb.enabled()),
                        positive(knockback, "friction", kb.friction(), "knockback", warn),
                        number(knockback, "horizontal", kb.horizontal()),
                        number(knockback, "vertical", kb.vertical()),
                        number(knockback, "vertical-limit", kb.verticalLimit()),
                        number(knockback, "extra-horizontal", kb.extraHorizontal()),
                        number(knockback, "extra-vertical", kb.extraVertical())),
                section.getBoolean("disable-offhand", d.disableOffhand()),
                section.getBoolean("disable-shields", d.disableShields()),
                readThrow(potions, d.potions(), "thrown-potions", warn),
                new LegacyCombatSettings.Pearls(
                        bool(pearls, "no-cooldown", d.pearls().noCooldown()),
                        readThrow(pearls, d.pearls().throwing(), "ender-pearls", warn)),
                new LegacyCombatSettings.Regeneration(
                        bool(regen, "enabled", d.regeneration().enabled()),
                        positive(regen, "interval-seconds", d.regeneration().intervalSeconds(),
                                "natural-regeneration", warn),
                        positive(regen, "amount", d.regeneration().amount(), "natural-regeneration", warn),
                        regen == null ? d.regeneration().minimumFood()
                                : Math.max(0, Math.min(20, regen.getInt("minimum-food", d.regeneration().minimumFood()))),
                        Math.max(0.0, number(regen, "exhaustion", d.regeneration().exhaustion()))),
                new LegacyCombatSettings.GoldenApples(
                        bool(apples, "enabled", d.goldenApples().enabled()),
                        readApple(apples == null ? null : apples.getConfigurationSection("golden-apple"),
                                d.goldenApples().golden(), "golden-apple", warn),
                        readApple(apples == null ? null : apples.getConfigurationSection("enchanted-golden-apple"),
                                d.goldenApples().enchanted(), "enchanted-golden-apple", warn)),
                new LegacyCombatSettings.Strength(
                        bool(strength, "enabled", d.strength().enabled()),
                        Math.max(0.0, number(strength, "per-level", d.strength().perLevel())),
                        bool(strength == null ? null : strength.getConfigurationSection("nerf"), "enabled",
                                d.strength().nerfEnabled()),
                        Math.max(0.0, number(strength == null ? null : strength.getConfigurationSection("nerf"),
                                "per-level", d.strength().nerfPerLevel()))),
                new LegacyCombatSettings.FishingRod(bool(rod, "enabled", d.fishingRod().enabled()),
                        bool(rod, "remove-hook", d.fishingRod().removeHook())));
    }

    private static LegacyCombatSettings.WeaponDamage readWeapons(ConfigurationSection section,
                                                                 LegacyCombatSettings.WeaponDamage fallback,
                                                                 Consumer<String> warn) {
        if (section == null) {
            return fallback;
        }
        ConfigurationSection table = section.getConfigurationSection("damage");
        Map<String, Double> damage = new java.util.LinkedHashMap<>();
        if (table == null) {
            damage.putAll(fallback.damage());
        } else {
            for (String item : table.getKeys(false)) {
                double value = table.getDouble(item, -1);
                if (!(value > 0) || !Double.isFinite(value)) {
                    warn.accept("legacy-combat.weapon-damage.damage." + item + " must be above 0; ignored.");
                    continue;
                }
                damage.put(item.trim().toLowerCase(java.util.Locale.ROOT), value);
            }
        }
        return new LegacyCombatSettings.WeaponDamage(section.getBoolean("enabled", fallback.enabled()), damage);
    }

    private static Throw readThrow(ConfigurationSection section, Throw fallback, String where, Consumer<String> warn) {
        if (section == null) {
            return fallback;
        }
        return new Throw(
                section.getBoolean("enabled", fallback.enabled()),
                positive(section, "speed", fallback.speed(), where, warn),
                section.getDouble("pitch-offset", fallback.pitchOffset()),
                Math.max(0.0, section.getDouble("inaccuracy", fallback.inaccuracy())));
    }

    private static Apple readApple(ConfigurationSection section, Apple fallback, String where, Consumer<String> warn) {
        if (section == null) {
            return fallback;
        }
        List<AppleEffect> effects = new ArrayList<>();
        if (section.contains("effects")) {
            for (Map<?, ?> entry : section.getMapList("effects")) {
                Object effect = entry.get("effect");
                Object level = entry.get("level");
                Object seconds = entry.get("seconds");
                if (effect == null || !(level instanceof Number l) || !(seconds instanceof Number s)
                        || l.intValue() < 1 || s.intValue() < 1) {
                    warn.accept("legacy-combat.golden-apples." + where + ".effects: " + entry
                            + " needs an effect, a level and seconds of 1 or more; ignored.");
                    continue;
                }
                effects.add(new AppleEffect(effect.toString().trim().toLowerCase(java.util.Locale.ROOT),
                        l.intValue(), s.intValue()));
            }
        } else {
            effects.addAll(fallback.effects());
        }
        return new Apple(Math.max(0, Math.min(20, section.getInt("food", fallback.food()))),
                Math.max(0.0, section.getDouble("saturation", fallback.saturation())), effects);
    }

    private static boolean bool(ConfigurationSection section, String key, boolean fallback) {
        return section == null ? fallback : section.getBoolean(key, fallback);
    }

    private static double number(ConfigurationSection section, String key, double fallback) {
        return section == null ? fallback : section.getDouble(key, fallback);
    }

    private static double positive(ConfigurationSection section, String key, double fallback, String where,
                                   Consumer<String> warn) {
        double value = number(section, key, fallback);
        if (!(value > 0) || !Double.isFinite(value)) {
            warn.accept("legacy-combat." + where + "." + key + " must be above 0; using " + fallback + ".");
            return fallback;
        }
        return value;
    }
}
