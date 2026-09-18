package com.lawkeys.hcfcore.pvpclass;

import com.lawkeys.hcfcore.util.Durations;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Reads {@code classes.yml} into {@link ClassSettings}, from the plain maps and
 * lists a YAML file is made of - so every rule about what a valid class is can be
 * tested without a server.
 *
 * <p>Nothing here ever throws on bad input. A value that cannot be used is reported
 * through {@code warn} and replaced by its default; a class that cannot work at all
 * - an unknown armour piece, a set another class already uses - is reported and
 * left out, and the others load.
 */
public final class ClassConfig {

    /** A held effect's default duration, in seconds. */
    static final int HELD_SECONDS = 8;

    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final String[] PIECES = {"helmet", "chestplate", "leggings", "boots"};

    private final Predicate<String> knownItem;
    private final Predicate<String> knownEffect;
    private final Predicate<String> knownDye;
    private final Consumer<String> warn;

    /**
     * @param knownItem   whether the server knows an item by this name (upper case)
     * @param knownEffect whether the server knows an effect by this key (lower case, no namespace)
     * @param knownDye    whether the server knows a dye colour by this name (upper case)
     * @param warn        where problems are reported
     */
    public ClassConfig(Predicate<String> knownItem, Predicate<String> knownEffect, Predicate<String> knownDye,
                       Consumer<String> warn) {
        this.knownItem = Objects.requireNonNull(knownItem, "knownItem");
        this.knownEffect = Objects.requireNonNull(knownEffect, "knownEffect");
        this.knownDye = Objects.requireNonNull(knownDye, "knownDye");
        this.warn = Objects.requireNonNull(warn, "warn");
    }

    public ClassSettings parse(Map<String, ?> root) {
        if (root == null) {
            return ClassSettings.defaults();
        }
        boolean enabled = bool(root.get("enabled"), true, "enabled");
        long warmup = seconds(root.get("warmup-seconds"), 10, "warmup-seconds");
        boolean inSafeZones = bool(root.get("abilities-in-safe-zones"), false, "abilities-in-safe-zones");
        List<PvpClass> classes = new ArrayList<>();
        Map<String, ?> list = map(root.get("classes"), "classes");
        for (Map.Entry<String, ?> entry : list.entrySet()) {
            String id = entry.getKey().trim().toLowerCase(Locale.ROOT);
            PvpClass loaded = parseClass(id, map(entry.getValue(), "classes." + entry.getKey()));
            if (loaded == null) {
                continue;
            }
            PvpClass sameSet = classes.stream().filter(other -> other.armor().equals(loaded.armor()))
                    .findFirst().orElse(null);
            if (sameSet != null) {
                warn.accept("classes." + id + ": its armour set is already " + sameSet.id()
                        + "'s, and the first class listed wins; skipped.");
                continue;
            }
            classes.add(loaded);
        }
        return new ClassSettings(enabled, warmup, inSafeZones, classes);
    }

    private PvpClass parseClass(String id, Map<String, ?> entry) {
        String at = "classes." + id;
        if (!VALID_ID.matcher(id).matches()) {
            warn.accept(at + ": an id is 1 to 32 letters, digits, - or _; skipped.");
            return null;
        }
        Map<String, ?> armorSection = map(entry.get("armor"), at + ".armor");
        List<String> armor = new ArrayList<>();
        for (String piece : PIECES) {
            String item = itemName(armorSection.get(piece));
            if (item == null || !knownItem.test(item)) {
                warn.accept(at + ".armor." + piece + ": '" + armorSection.get(piece)
                        + "' is not an item this server knows; the class is skipped.");
                return null;
            }
            armor.add(item);
        }
        String displayName = text(entry.get("display-name"), id);
        String permission = text(entry.get("permission"), "");
        int maxPerTeam = (int) whole(entry.get("max-per-team"), 0, at + ".max-per-team");

        Map<String, Integer> passive = new LinkedHashMap<>();
        map(entry.get("passive-effects"), at + ".passive-effects").forEach((key, value) -> {
            String effect = ClassEffect.normaliseKey(key);
            if (!knownEffect.test(effect)) {
                warn.accept(at + ".passive-effects: '" + key + "' is not an effect this server knows; ignored.");
                return;
            }
            int level = level(value, at + ".passive-effects." + key);
            if (level > 0) {
                passive.put(effect, level);
            }
        });

        Energy energy = null;
        if (entry.get("energy") != null) {
            Map<String, ?> section = map(entry.get("energy"), at + ".energy");
            double max = decimal(section.get("max"), 100.0, at + ".energy.max");
            double perSecond = decimal(section.get("per-second"), 1.0, at + ".energy.per-second");
            if (max > 0) {
                energy = new Energy(max, perSecond);
            } else {
                warn.accept(at + ".energy.max: must be above 0; the class has no energy.");
            }
        }

        Map<String, HeldEffect> held = new LinkedHashMap<>();
        map(entry.get("held-effects"), at + ".held-effects").forEach((key, value) -> {
            String where = at + ".held-effects." + key;
            String item = itemName(key);
            if (item == null || !knownItem.test(item)) {
                warn.accept(where + ": '" + key + "' is not an item this server knows; ignored.");
                return;
            }
            Map<String, ?> section = map(value, where);
            ClassEffect effect = effect(section, HELD_SECONDS, where);
            ClassTarget target = target(section.get("targets"), ClassTarget.TEAM, where);
            if (effect != null) {
                held.put(item, new HeldEffect(effect, target, decimal(section.get("radius"), 20.0, where + ".radius")));
            }
        });

        Map<String, ClickEffect> clicks = new LinkedHashMap<>();
        map(entry.get("click-effects"), at + ".click-effects").forEach((key, value) -> {
            String where = at + ".click-effects." + key;
            String item = itemName(key);
            if (item == null || !knownItem.test(item)) {
                warn.accept(where + ": '" + key + "' is not an item this server knows; ignored.");
                return;
            }
            Map<String, ?> section = map(value, where);
            ClassEffect effect = effect(section, 8, where);
            if (effect == null) {
                return;
            }
            int cost = (int) whole(section.get("energy"), 0, where + ".energy");
            if (cost > 0 && !(entryHasEnergy(entry))) {
                warn.accept(where + ".energy: the class has no energy section, so the cost is ignored.");
                cost = 0;
            }
            clicks.put(item, new ClickEffect(effect,
                    target(section.get("targets"), ClassTarget.SELF, where),
                    decimal(section.get("radius"), 20.0, where + ".radius"),
                    cost,
                    seconds(section.get("cooldown-seconds"), 0, where + ".cooldown-seconds"),
                    bool(section.get("consume"), true, where + ".consume")));
        });

        ArcherTag archerTag = null;
        if (entry.get("archer-tag") != null) {
            Map<String, ?> section = map(entry.get("archer-tag"), at + ".archer-tag");
            long seconds = seconds(section.get("seconds"), 10, at + ".archer-tag.seconds");
            double multiplier = decimal(section.get("damage-multiplier"), 1.25, at + ".archer-tag.damage-multiplier");
            if (seconds > 0 && multiplier >= 1.0) {
                archerTag = new ArcherTag((int) Math.min(Integer.MAX_VALUE, seconds), multiplier);
            } else {
                warn.accept(at + ".archer-tag: seconds must be above 0 and damage-multiplier 1.0 or more;"
                        + " the class has no archer tag.");
            }
        }

        Backstab backstab = null;
        if (entry.get("backstab") != null) {
            String where = at + ".backstab";
            Map<String, ?> section = map(entry.get("backstab"), where);
            String weapon = itemName(section.get("weapon"));
            double damage = decimal(section.get("damage"), 6.0, where + ".damage");
            if (weapon == null || !knownItem.test(weapon)) {
                warn.accept(where + ".weapon: '" + section.get("weapon")
                        + "' is not an item this server knows; the class has no backstab.");
            } else if (!(damage > 0)) {
                warn.accept(where + ".damage: must be above 0; the class has no backstab.");
            } else {
                backstab = new Backstab(weapon, damage,
                        seconds(section.get("cooldown-seconds"), 15, where + ".cooldown-seconds"),
                        bool(section.get("break-weapon"), true, where + ".break-weapon"),
                        decimal(section.get("max-angle"), 60.0, where + ".max-angle"));
            }
        }

        Integer invisibleBelowY = null;
        if (entry.get("invisible-below-y") != null) {
            Object raw = entry.get("invisible-below-y");
            if (raw instanceof Number number) {
                invisibleBelowY = number.intValue();
            } else {
                warn.accept(at + ".invisible-below-y: '" + raw + "' is not a whole number; ignored.");
            }
        }

        Map<String, DyeEffect> dyes = new LinkedHashMap<>();
        map(entry.get("dye-effects"), at + ".dye-effects").forEach((key, value) -> {
            String where = at + ".dye-effects." + key;
            String dye = key.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            if (!knownDye.test(dye)) {
                warn.accept(where + ": '" + key + "' is not a dye colour (WHITE, RED, GREEN...); ignored.");
                return;
            }
            Map<String, ?> section = map(value, where);
            ClassEffect effect = effect(section, 10, where);
            if (effect == null) {
                return;
            }
            double chance = decimal(section.get("chance"), 0.0, where + ".chance");
            if (chance < 0 || chance > 100) {
                warn.accept(where + ".chance: must be from 0 to 100; ignored.");
                return;
            }
            dyes.put(dye, new DyeEffect(effect, chance));
        });
        if (!dyes.isEmpty() && !armor.stream().allMatch(piece -> piece.startsWith("LEATHER_"))) {
            warn.accept(at + ".dye-effects: only leather armour can be dyed, and this set is not all"
                    + " leather; the colours will never match.");
        }

        return new PvpClass(id, displayName, armor, permission, maxPerTeam, passive, energy, held, clicks,
                archerTag, backstab, invisibleBelowY, dyes);
    }

    private static boolean entryHasEnergy(Map<String, ?> entry) {
        return entry.get("energy") != null;
    }

    /** @return the effect of a held or click entry, or {@code null} (reported) if it cannot be used */
    private ClassEffect effect(Map<String, ?> section, int defaultSeconds, String where) {
        Object raw = section.get("effect");
        String key = raw == null ? null : ClassEffect.normaliseKey(raw.toString());
        if (key == null || key.isEmpty() || !knownEffect.test(key)) {
            warn.accept(where + ".effect: '" + raw + "' is not an effect this server knows; ignored.");
            return null;
        }
        int level = level(section.containsKey("level") ? section.get("level") : Integer.valueOf(1), where + ".level");
        if (level <= 0) {
            return null;
        }
        long seconds = seconds(section.get("seconds"), defaultSeconds, where + ".seconds");
        if (seconds <= 0) {
            warn.accept(where + ".seconds: must be above 0; ignored.");
            return null;
        }
        return new ClassEffect(key, level, (int) Math.min(Integer.MAX_VALUE, seconds));
    }

    private ClassTarget target(Object raw, ClassTarget fallback, String where) {
        if (raw == null) {
            return fallback;
        }
        return ClassTarget.parse(raw.toString()).orElseGet(() -> {
            warn.accept(where + ".targets: '" + raw + "' is not one of self, team, team-and-allies,"
                    + " enemies; " + fallback.configName() + " is used.");
            return fallback;
        });
    }

    /** @return a level from 1 to {@link ClassEffect#MAX_LEVEL}, or {@code 0} (reported) */
    private int level(Object raw, String where) {
        if (raw instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue())
                && number.longValue() >= 1 && number.longValue() <= ClassEffect.MAX_LEVEL) {
            return number.intValue();
        }
        warn.accept(where + ": '" + raw + "' is not a level from 1 to " + ClassEffect.MAX_LEVEL + "; ignored.");
        return 0;
    }

    private static String itemName(Object raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.toString().trim().toUpperCase(Locale.ROOT);
        if (name.startsWith("MINECRAFT:")) {
            name = name.substring("MINECRAFT:".length());
        }
        return name.isEmpty() ? null : name;
    }

    private static String text(Object raw, String fallback) {
        return raw == null ? fallback : raw.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> map(Object raw, String where) {
        if (raw == null) {
            return Map.of();
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((key, value) -> out.put(String.valueOf(key), value));
            return out;
        }
        warn.accept(where + ": expected a section; ignored.");
        return Map.of();
    }

    private boolean bool(Object raw, boolean fallback, String where) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Boolean value) {
            return value;
        }
        warn.accept(where + ": '" + raw + "' is not true or false; " + fallback + " is used.");
        return fallback;
    }

    private long whole(Object raw, long fallback, String where) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue())
                && number.longValue() >= 0) {
            return number.longValue();
        }
        warn.accept(where + ": '" + raw + "' is not a whole number of 0 or more; " + fallback + " is used.");
        return fallback;
    }

    /** A duration in seconds, capped as every {@code *-seconds} setting is (CONTRIBUTING.md section 7). */
    private long seconds(Object raw, long fallback, String where) {
        return Durations.capSeconds(whole(raw, fallback, where), where, warn);
    }

    private double decimal(Object raw, double fallback, String where) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number number && Double.isFinite(number.doubleValue())) {
            return number.doubleValue();
        }
        warn.accept(where + ": '" + raw + "' is not a number; " + fallback + " is used.");
        return fallback;
    }
}
