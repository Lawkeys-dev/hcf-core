package com.lawkeys.hcfcore.ability;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * What an ability does, and the settings it reads. The behaviour is built in; the
 * values are {@code abilities.yml}'s. Each type is used one way: a right-click, a
 * throw, a hit with the item, or a shot from it.
 */
public enum AbilityType {

    /** Console commands, run with {@code %player%}: any ability the others do not cover. */
    COMMANDS(Trigger.RIGHT_CLICK),
    /** Thrown snowball or egg: swaps places with the player it hits, within {@code distance}. */
    SWITCHER(Trigger.THROW,
            Param.decimal("distance", 8)),
    /** For {@code seconds}, each hit has {@code chance}% to strike lightning, for {@code damage-hearts} through armour. */
    THUNDERBOLT(Trigger.RIGHT_CLICK,
            Param.whole("seconds", 10), Param.decimal("chance", 20), Param.decimal("damage-hearts", 1.5)),
    /** For {@code seconds}, hits are counted (to {@code max-hits}); then {@code effect} for so many seconds each. */
    COMBO(Trigger.RIGHT_CLICK,
            Param.whole("seconds", 10), Param.whole("max-hits", 12), Param.whole("seconds-per-hit", 1),
            Param.effect("effect", new AbilityEffect("strength", 2, 1))),
    /** For {@code seconds}, each hit deals from {@code min-percent} to {@code max-percent} more, at random. */
    LUCKY_MODE(Trigger.RIGHT_CLICK,
            Param.whole("seconds", 10), Param.decimal("min-percent", -10), Param.decimal("max-percent", 35)),
    /** Thrown: where it lands, teammates get {@code team-effects}, enemies {@code enemy-effects}. */
    RAGE_BALL(Trigger.THROW,
            Param.decimal("radius", 8), Param.bool("cooldown-whole-team", true),
            Param.effects("team-effects", List.of(new AbilityEffect("strength", 2, 8),
                    new AbilityEffect("resistance", 3, 8))),
            Param.effects("enemy-effects", List.of(new AbilityEffect("wither", 2, 8)))),
    /** {@code hits-required} hits with it: for {@code seconds}, each hit has {@code chance}% to open a crafting table on them. */
    CRAFTING_CHAOS(Trigger.HIT,
            Param.whole("hits-required", 3), Param.decimal("chance", 20), Param.whole("seconds", 10)),
    /** The last player who hit you (within {@code hit-within-seconds}) takes {@code damage-multiplier} from you, for {@code seconds}. */
    FOCUS_MODE(Trigger.RIGHT_CLICK,
            Param.decimal("damage-multiplier", 1.25), Param.whole("seconds", 10), Param.whole("hit-within-seconds", 15)),
    /** Teleports, after {@code delay-seconds}, to the last player who hit you. */
    NINJA(Trigger.RIGHT_CLICK,
            Param.whole("delay-seconds", 3), Param.whole("hit-within-seconds", 15)),
    /** {@code hits-required} hits with it: they cannot build, break or open {@code blocked-blocks} for {@code seconds}. */
    ANTI_BUILD(Trigger.HIT,
            Param.whole("hits-required", 3), Param.whole("seconds", 15),
            Param.strings("blocked-blocks", List.of("CHEST", "TRAPPED_CHEST", "FENCE_GATE", "TRAPDOOR")),
            Param.effects("user-effects", List.of(new AbilityEffect("speed", 3, 10)))),
    /** A bow whose arrows archer-tag the player they hit; it breaks after {@code uses} shots. */
    PORTABLE_ARCHER(Trigger.SHOOT,
            Param.whole("uses", 5), Param.whole("tag-seconds", 10), Param.decimal("damage-multiplier", 1.15)),
    /** Invisible - armour hidden too with {@code hide-armor} - until hit, with {@code reveal-on-hit}. */
    INVISIBILITY(Trigger.RIGHT_CLICK,
            Param.effect("effect", new AbilityEffect("invisibility", 2, 120)),
            Param.bool("hide-armor", true), Param.bool("reveal-on-hit", true)),
    /** Back, after {@code delay-seconds}, to where you threw your last ender pearl (within {@code pearl-within-seconds}). */
    TIME_WARP(Trigger.RIGHT_CLICK,
            Param.whole("pearl-within-seconds", 15), Param.whole("delay-seconds", 2)),
    /** A menu to pick a set of Bard items (the {@code pocket-bard} section). */
    POCKET_BARD(Trigger.RIGHT_CLICK),
    /** {@code effects}, and no {@code denied-potions} for {@code seconds}. */
    BERSERK(Trigger.RIGHT_CLICK,
            Param.whole("seconds", 8),
            Param.effects("effects", List.of(new AbilityEffect("strength", 2, 8),
                    new AbilityEffect("resistance", 3, 8), new AbilityEffect("regeneration", 3, 8))),
            Param.strings("denied-potions", List.of("healing", "strong_healing"))),
    /** {@code effects}, only at {@code max-health-hearts} or less. */
    CLOSE_CALL(Trigger.RIGHT_CLICK,
            Param.decimal("max-health-hearts", 3.5),
            Param.effects("effects", List.of(new AbilityEffect("strength", 2, 6),
                    new AbilityEffect("regeneration", 5, 6)))),
    /** For {@code seconds}, each hit has {@code chance}% to turn the player hit by {@code degrees}. */
    SWITCH_STICK(Trigger.RIGHT_CLICK,
            Param.whole("seconds", 10), Param.decimal("chance", 20), Param.decimal("degrees", 180)),
    /** In water only: teleports, after {@code delay-seconds}, to the last player who hit you. */
    TELEPORT_EYE(Trigger.RIGHT_CLICK,
            Param.whole("delay-seconds", 3), Param.whole("hit-within-seconds", 15)),
    /** Teleports to the last player who hit you, puts them under anti-build and a pearl cooldown; {@code effects} for you. */
    SAMURAI(Trigger.RIGHT_CLICK,
            Param.whole("delay-seconds", 3), Param.whole("hit-within-seconds", 15),
            Param.whole("anti-build-seconds", 15), Param.whole("ender-pearl-cooldown-seconds", 16),
            Param.effects("effects", List.of(new AbilityEffect("strength", 2, 8), new AbilityEffect("speed", 3, 8)))),
    /** A hit with it: {@code effects-by-space}, by how many free blocks are above the player's head. */
    MAGIC_ROCK(Trigger.HIT,
            Param.effectTable("effects-by-space", Map.of(
                    2, List.of(new AbilityEffect("strength", 2, 8)),
                    3, List.of(new AbilityEffect("strength", 2, 6)),
                    4, List.of(new AbilityEffect("strength", 2, 4)),
                    5, List.of(new AbilityEffect("strength", 2, 2))))),
    /** {@code effects} for every enemy within {@code radius}. */
    BELCH_BOMB(Trigger.RIGHT_CLICK,
            Param.decimal("radius", 8),
            Param.effects("effects", List.of(new AbilityEffect("slowness", 2, 6), new AbilityEffect("blindness", 2, 6)))),
    /** Teleports, after {@code delay-seconds}, to the last player who hit you with a projectile. */
    ANTI_TRAP_STAR(Trigger.RIGHT_CLICK,
            Param.whole("delay-seconds", 3), Param.whole("hit-within-seconds", 15));

    /** How an ability is used. */
    public enum Trigger {
        /** A right-click with the item. */
        RIGHT_CLICK,
        /** Throwing it: a snowball or an egg. */
        THROW,
        /** Hitting a player while holding it. */
        HIT,
        /** Shooting an arrow with it: a bow. */
        SHOOT
    }

    /** One setting a type reads, and its value when the file gives none. */
    public record Param(String key, Kind kind, Object fallback) {

        public enum Kind { WHOLE, DECIMAL, BOOL, EFFECT, EFFECTS, EFFECT_TABLE, STRINGS }

        static Param whole(String key, long fallback) {
            return new Param(key, Kind.WHOLE, fallback);
        }

        static Param decimal(String key, double fallback) {
            return new Param(key, Kind.DECIMAL, fallback);
        }

        static Param bool(String key, boolean fallback) {
            return new Param(key, Kind.BOOL, fallback);
        }

        static Param effect(String key, AbilityEffect fallback) {
            return new Param(key, Kind.EFFECT, fallback);
        }

        static Param effects(String key, List<AbilityEffect> fallback) {
            return new Param(key, Kind.EFFECTS, List.copyOf(fallback));
        }

        static Param effectTable(String key, Map<Integer, List<AbilityEffect>> fallback) {
            return new Param(key, Kind.EFFECT_TABLE, Map.copyOf(fallback));
        }

        static Param strings(String key, List<String> fallback) {
            return new Param(key, Kind.STRINGS, List.copyOf(fallback));
        }
    }

    private final Trigger trigger;
    private final List<Param> params;

    AbilityType(Trigger trigger, Param... params) {
        this.trigger = trigger;
        this.params = List.of(params);
    }

    public Trigger trigger() {
        return trigger;
    }

    public List<Param> params() {
        return params;
    }

    /** @return the word the configuration uses: {@code rage-ball} */
    public String configName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Optional<AbilityType> parse(String word) {
        if (word == null) {
            return Optional.empty();
        }
        String normalised = word.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (AbilityType type : values()) {
            if (type.name().equals(normalised)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
