package com.lawkeys.hcfcore.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Reads {@code abilities.yml} into {@link AbilitySettings}, from the plain maps and
 * lists a YAML file is made of - so what a valid ability is can be tested without a
 * server.
 *
 * <p>Nothing here throws on bad input. A value that cannot be used is reported
 * through {@code warn} and replaced by its default; an ability that cannot work at
 * all - no type, an item the server does not know - is reported and left out, and
 * the others load.
 */
public final class AbilityConfig {

    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_-]{1,32}");
    /** Settings every ability reads, whatever its type. */
    private static final List<String> COMMON = List.of("type", "enabled", "material", "name", "lore", "glow",
            "enchantments", "cooldown-seconds", "consume", "uses", "commands");

    private final Predicate<String> knownItem;
    private final Predicate<String> knownEffect;
    private final Consumer<String> warn;

    /**
     * @param knownItem   whether the server knows an item by this name (upper case)
     * @param knownEffect whether the server knows an effect by this key (lower case, no namespace)
     */
    public AbilityConfig(Predicate<String> knownItem, Predicate<String> knownEffect, Consumer<String> warn) {
        this.knownItem = Objects.requireNonNull(knownItem, "knownItem");
        this.knownEffect = Objects.requireNonNull(knownEffect, "knownEffect");
        this.warn = Objects.requireNonNull(warn, "warn");
    }

    public AbilitySettings parse(Map<String, ?> root) {
        AbilitySettings defaults = AbilitySettings.defaults();
        if (root == null) {
            return defaults;
        }
        Map<String, ?> global = map(root.get("global"), "global");
        Map<String, ?> disabled = map(global.get("disabled-in"), "global.disabled-in");
        AbilitySettings.DisabledIn d = defaults.disabledIn();
        AbilitySettings.DisabledIn disabledIn = new AbilitySettings.DisabledIn(
                bool(disabled.get("safezone"), d.safezone(), "global.disabled-in.safezone"),
                bool(disabled.get("citadel"), d.citadel(), "global.disabled-in.citadel"),
                bool(disabled.get("events"), d.events(), "global.disabled-in.events"),
                bool(disabled.get("nether"), d.nether(), "global.disabled-in.nether"),
                bool(disabled.get("end"), d.end(), "global.disabled-in.end"),
                bool(disabled.get("warzone"), d.warzone(), "global.disabled-in.warzone"),
                territoryMode(disabled.get("event-territory"), d.eventTerritory()));

        List<Ability> abilities = new ArrayList<>();
        map(root.get("abilities"), "abilities").forEach((key, value) -> {
            Ability ability = parseAbility(key, map(value, "abilities." + key));
            if (ability != null) {
                abilities.add(ability);
            }
        });

        Map<String, ?> pocket = map(root.get("pocket-bard"), "pocket-bard");
        List<PocketBardItem> pocketItems = new ArrayList<>();
        map(pocket.get("items"), "pocket-bard.items").forEach((key, value) -> {
            PocketBardItem item = parsePocketItem(key, map(value, "pocket-bard.items." + key));
            if (item != null) {
                pocketItems.add(item);
            }
        });
        int size = (int) whole(pocket.get("menu-size"), defaults.pocketBardSize(), "pocket-bard.menu-size");
        if (size < 9 || size > 54 || size % 9 != 0) {
            warn.accept("pocket-bard.menu-size: " + size + " is not 9, 18, 27, 36, 45 or 54; 9 is used.");
            size = 9;
        }
        final int menuSize = size;
        pocketItems.removeIf(item -> {
            if (item.slot() >= menuSize) {
                warn.accept("pocket-bard.items." + item.id() + ".slot: " + item.slot() + " is outside a menu of "
                        + menuSize + "; left out.");
                return true;
            }
            return false;
        });

        return new AbilitySettings(
                bool(global.get("enabled"), defaults.enabled(), "global.enabled"),
                whole(global.get("cooldown-seconds"), defaults.globalCooldownSeconds(), "global.cooldown-seconds"),
                whole(global.get("hits-within-seconds"), defaults.hitsWithinSeconds(), "global.hits-within-seconds"),
                text(global.get("menu-title"), defaults.menuTitle()),
                disabledIn, abilities,
                text(pocket.get("menu-title"), defaults.pocketBardTitle()), menuSize, pocketItems);
    }

    private Ability parseAbility(String rawId, Map<String, ?> entry) {
        String id = rawId.trim().toLowerCase(Locale.ROOT);
        String at = "abilities." + rawId;
        if (!VALID_ID.matcher(id).matches()) {
            warn.accept(at + ": an id is 1 to 32 letters, digits, _ or -; left out.");
            return null;
        }
        if (!bool(entry.get("enabled"), true, at + ".enabled")) {
            return null;
        }
        AbilityType type = AbilityType.parse(text(entry.get("type"), null)).orElse(null);
        if (type == null) {
            warn.accept(at + ".type: '" + entry.get("type") + "' is not an ability type; left out.");
            return null;
        }
        String material = itemName(entry.get("material"));
        if (material == null || !knownItem.test(material)) {
            warn.accept(at + ".material: '" + entry.get("material") + "' is not an item this server knows; left out.");
            return null;
        }
        if (type == AbilityType.SWITCHER || type == AbilityType.RAGE_BALL || type == AbilityType.THROWN_EFFECTS) {
            if (!material.equals("SNOWBALL") && !material.equals("EGG")) {
                warn.accept(at + ".material: a " + type.configName() + " is thrown - SNOWBALL or EGG; left out.");
                return null;
            }
        }
        if (type == AbilityType.PORTABLE_ARCHER && !material.equals("BOW")) {
            warn.accept(at + ".material: a portable-archer is a BOW; left out.");
            return null;
        }
        if (type == AbilityType.FAKE_PEARL && !material.equals("ENDER_PEARL")) {
            warn.accept(at + ".material: a fake-pearl is an ENDER_PEARL; left out.");
            return null;
        }
        if (type == AbilityType.GRAPPLING_HOOK && !material.equals("FISHING_ROD")) {
            warn.accept(at + ".material: a grappling-hook is a FISHING_ROD; left out.");
            return null;
        }
        List<String> commands = strings(entry.get("commands"), at + ".commands");
        if (type == AbilityType.COMMANDS && commands.isEmpty()) {
            warn.accept(at + ": a commands ability with no commands would do nothing; left out.");
            return null;
        }
        Map<String, Integer> enchantments = new LinkedHashMap<>();
        map(entry.get("enchantments"), at + ".enchantments").forEach((key, value) -> {
            if (value instanceof Number level && level.intValue() >= 1) {
                enchantments.put(key.trim().toLowerCase(Locale.ROOT), level.intValue());
            } else {
                warn.accept(at + ".enchantments." + key + ": '" + value + "' is not a level; ignored.");
            }
        });
        for (String key : entry.keySet()) {
            if (!COMMON.contains(key) && type.params().stream().noneMatch(param -> param.key().equals(key))) {
                warn.accept(at + "." + key + ": a " + type.configName() + " does not read this; ignored.");
            }
        }
        return new Ability(id, type, material, text(entry.get("name"), "&f" + id),
                strings(entry.get("lore"), at + ".lore"),
                bool(entry.get("glow"), true, at + ".glow"), enchantments,
                whole(entry.get("cooldown-seconds"), 0, at + ".cooldown-seconds"),
                bool(entry.get("consume"), true, at + ".consume"),
                // A Portable Archer is a bow that breaks: 5 shots unless told otherwise.
                whole(entry.get("uses"), type == AbilityType.PORTABLE_ARCHER ? 5 : 0, at + ".uses"),
                commands, params(type, entry, at));
    }

    private AbilityParams params(AbilityType type, Map<String, ?> entry, String at) {
        Map<String, Object> values = new HashMap<>();
        for (AbilityType.Param param : type.params()) {
            Object raw = entry.get(param.key());
            String where = at + "." + param.key();
            Object value = switch (param.kind()) {
                case WHOLE -> whole(raw, ((Number) param.fallback()).longValue(), where);
                case DECIMAL -> decimal(raw, ((Number) param.fallback()).doubleValue(), where);
                case BOOL -> bool(raw, (Boolean) param.fallback(), where);
                case EFFECT -> raw == null ? param.fallback() : orFallback(effect(map(raw, where), where), param);
                case EFFECTS -> raw == null ? param.fallback() : effects(raw, where);
                case STRINGS -> raw == null ? param.fallback() : strings(raw, where);
            };
            values.put(param.key(), value);
        }
        return new AbilityParams(values);
    }

    private static Object orFallback(Object value, AbilityType.Param param) {
        return value == null ? param.fallback() : value;
    }

    private PocketBardItem parsePocketItem(String rawId, Map<String, ?> entry) {
        String id = rawId.trim().toLowerCase(Locale.ROOT);
        String at = "pocket-bard.items." + rawId;
        String material = itemName(entry.get("material"));
        if (material == null || !knownItem.test(material)) {
            warn.accept(at + ".material: '" + entry.get("material") + "' is not an item this server knows; left out.");
            return null;
        }
        AbilityEffect effect = effect(map(entry.get("effect"), at + ".effect"), at + ".effect");
        if (effect == null) {
            warn.accept(at + ": a Pocket Bard item gives an effect; left out.");
            return null;
        }
        String name = text(entry.get("name"), "&f" + id);
        return new PocketBardItem(id, material, name, strings(entry.get("lore"), at + ".lore"),
                (int) whole(entry.get("amount"), 3, at + ".amount"), effect,
                decimal(entry.get("radius"), 20, at + ".radius"),
                bool(entry.get("include-self"), true, at + ".include-self"),
                (int) whole(entry.get("slot"), 0, at + ".slot"),
                text(entry.get("menu-name"), name),
                whole(entry.get("cooldown-seconds"), 60, at + ".cooldown-seconds"));
    }

    // ------------------------------------------------------------------

    /** @return the effect, or {@code null} (reported) */
    private AbilityEffect effect(Map<String, ?> section, String where) {
        if (section.isEmpty()) {
            return null;
        }
        String key = text(section.get("effect"), "").trim().toLowerCase(Locale.ROOT);
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        if (key.isEmpty() || !knownEffect.test(key)) {
            warn.accept(where + ".effect: '" + section.get("effect") + "' is not an effect this server knows; ignored.");
            return null;
        }
        long level = whole(section.get("level"), 1, where + ".level");
        if (level < 1 || level > AbilityEffect.MAX_LEVEL) {
            warn.accept(where + ".level: " + level + " is not from 1 to " + AbilityEffect.MAX_LEVEL + "; 1 is used.");
            level = 1;
        }
        long seconds = whole(section.get("seconds"), 1, where + ".seconds");
        return new AbilityEffect(key, (int) level, (int) Math.max(1, seconds));
    }

    private List<AbilityEffect> effects(Object raw, String where) {
        List<AbilityEffect> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            warn.accept(where + ": a list of effects is expected; none.");
            return out;
        }
        for (int i = 0; i < list.size(); i++) {
            AbilityEffect effect = effect(map(list.get(i), where + "[" + i + "]"), where + "[" + i + "]");
            if (effect != null) {
                out.add(effect);
            }
        }
        return List.copyOf(out);
    }

    private List<String> strings(Object raw, String where) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            warn.accept(where + ": a list is expected; ignored.");
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
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
        warn.accept(where + ": a block of settings is expected; ignored.");
        return Map.of();
    }

    private com.lawkeys.hcfcore.events.setup.TerritoryAbilityMode territoryMode(
            Object raw, com.lawkeys.hcfcore.events.setup.TerritoryAbilityMode fallback) {
        if (raw == null) {
            return fallback;
        }
        return com.lawkeys.hcfcore.events.setup.TerritoryAbilityMode.parse(raw).orElseGet(() -> {
            warn.accept("global.disabled-in.event-territory: '" + raw + "' is not never, during-event or always; "
                    + fallback.id() + " is used.");
            return fallback;
        });
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
        if (raw instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue())) {
            return number.longValue();
        }
        warn.accept(where + ": '" + raw + "' is not a whole number; " + fallback + " is used.");
        return fallback;
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

    private static String text(Object raw, String fallback) {
        return raw == null ? fallback : raw.toString();
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
}
