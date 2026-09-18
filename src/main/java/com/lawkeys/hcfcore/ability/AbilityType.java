package com.lawkeys.hcfcore.ability;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What an ability does, and the settings it reads. The behaviour is built in; the
 * values are {@code abilities.yml}'s. Each type is used one way: a right-click, a
 * throw, a hit with the item, a shot from it, or reeling in with it.
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
    /** Teleports, after {@code delay-seconds}, to the last player you hit (within {@code hit-within-seconds}). */
    NINJA(Trigger.RIGHT_CLICK,
            Param.whole("delay-seconds", 3), Param.whole("hit-within-seconds", 10)),
    /** {@code hits-required} hits with it: they cannot build, break or open {@code blocked-blocks} for {@code seconds}. */
    ANTI_BUILD(Trigger.HIT,
            Param.whole("hits-required", 3), Param.whole("seconds", 15),
            Param.strings("blocked-blocks", List.of("CHEST", "TRAPPED_CHEST", "FENCE_GATE", "TRAPDOOR")),
            Param.effects("user-effects", List.of(new AbilityEffect("speed", 3, 10)))),
    /** A bow whose arrows archer-tag the player they hit; it breaks after {@code uses} shots (5 unless set). */
    PORTABLE_ARCHER(Trigger.SHOOT,
            Param.whole("tag-seconds", 10), Param.decimal("damage-multiplier", 1.15)),
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
    /** {@code effects} for every enemy within {@code radius}. */
    BELCH_BOMB(Trigger.RIGHT_CLICK,
            Param.decimal("radius", 8),
            Param.effects("effects", List.of(new AbilityEffect("slowness", 2, 6), new AbilityEffect("blindness", 2, 6)))),
    /** Teleports, after {@code delay-seconds}, to the last player who hit you (within {@code hit-within-seconds}). */
    ANTI_TRAP_STAR(Trigger.RIGHT_CLICK,
            Param.whole("delay-seconds", 3), Param.whole("hit-within-seconds", 10)),
    /** {@code effects} for you. */
    EFFECTS(Trigger.RIGHT_CLICK,
            Param.effects("effects", List.of(new AbilityEffect("strength", 2, 8)))),
    /** {@code positive-chance}% to get {@code good-effects}; {@code bad-effects} otherwise. */
    LUCKY_BARD(Trigger.RIGHT_CLICK,
            Param.decimal("positive-chance", 50),
            Param.effects("good-effects", List.of(new AbilityEffect("strength", 2, 8),
                    new AbilityEffect("speed", 2, 8), new AbilityEffect("regeneration", 2, 8))),
            Param.effects("bad-effects", List.of(new AbilityEffect("slowness", 2, 8),
                    new AbilityEffect("weakness", 1, 8), new AbilityEffect("poison", 1, 8)))),
    /** Takes off your negative effects; the others stay. */
    CLEANSE(Trigger.RIGHT_CLICK),
    /** No fall damage for {@code seconds}. */
    NO_FALL(Trigger.RIGHT_CLICK,
            Param.whole("seconds", 10)),
    /** Launches you up - {@code height} 1 is about 3 blocks - with no fall damage for {@code no-fall-seconds}. */
    ROCKET(Trigger.RIGHT_CLICK,
            Param.decimal("height", 3.5), Param.whole("no-fall-seconds", 6)),
    /** Throws every enemy within {@code radius} in the air - {@code height} 1 is about 3 blocks - and {@code push} away. */
    HULK_SMASH(Trigger.RIGHT_CLICK,
            Param.decimal("radius", 10), Param.decimal("height", 2), Param.decimal("push", 0.3)),
    /** For {@code seconds}, the players you hit can be hit again after {@code hit-delay-ticks}, not the game's 10. */
    COMBO_FISH(Trigger.RIGHT_CLICK,
            Param.whole("seconds", 5), Param.whole("hit-delay-ticks", 2)),
    /**
     * Fires {@code projectiles} eggs scattered in a cone {@code spread} degrees wide: each sets the
     * player it hits on fire and deals {@code damage-hearts}; you are pushed back by {@code recoil}.
     */
    SHOTGUN(Trigger.RIGHT_CLICK,
            Param.whole("projectiles", 10), Param.decimal("spread", 10), Param.decimal("speed", 1.5),
            Param.decimal("damage-hearts", 0.25), Param.whole("fire-seconds", 10), Param.decimal("recoil", 1.0)),
    /**
     * Fireworks burst around you; every player within {@code radius} - with {@code hits-everyone}, you
     * and your teammates too; enemies only without - takes {@code damage-hearts-per-player} for each
     * player caught (at most {@code max-players}), is blinded - and burns, with {@code fire-seconds}.
     */
    SUN(Trigger.RIGHT_CLICK,
            Param.decimal("radius", 8), Param.decimal("damage-hearts-per-player", 0.9), Param.whole("max-players", 10),
            Param.whole("fire-seconds", 0), Param.whole("blindness-seconds", 2), Param.whole("fireworks", 6),
            Param.bool("hits-everyone", true)),
    /** {@code hits-required} hits: {@code chance}% to give the player hit {@code effects}. */
    HIT_EFFECTS(Trigger.HIT,
            Param.whole("hits-required", 1), Param.decimal("chance", 100),
            Param.effects("effects", List.of(new AbilityEffect("slowness", 2, 5)))),
    /**
     * A hit: {@code chance}% that the player hit - in one of {@code classes} - has their
     * helmet swapped for a pumpkin, given back after {@code seconds}.
     */
    PUMPKIN(Trigger.HIT,
            Param.whole("hits-required", 1), Param.decimal("chance", 50), Param.whole("seconds", 10),
            Param.strings("classes", List.of("diamond"))),
    /** A hit: {@code chance}% that the weapon of the player hit swaps places with another item of theirs. */
    DISARM(Trigger.HIT,
            Param.whole("hits-required", 1), Param.decimal("chance", 50)),
    /** {@code hits-required} hits: the hotbar of the player hit is shuffled. */
    SCRAMBLE(Trigger.HIT,
            Param.whole("hits-required", 3)),
    /**
     * {@code hits-required} hits: Hunger strong enough to take {@code food-lost} points (of 20)
     * from the player hit over {@code seconds} - time for them to eat.
     */
    STARVE(Trigger.HIT,
            Param.whole("hits-required", 3), Param.whole("food-lost", 14), Param.whole("seconds", 10)),
    /** A hit: the player hit is pulled towards you, by {@code pull}. */
    GRAB(Trigger.HIT,
            Param.whole("hits-required", 1), Param.decimal("pull", 1.0), Param.decimal("max-speed", 4.0)),
    /** A hit: for {@code seconds}, {@code reflect-percent}% of the damage that player deals you goes back to them. */
    THORNS(Trigger.HIT,
            Param.whole("hits-required", 1), Param.whole("seconds", 10), Param.decimal("reflect-percent", 30)),
    /** Thrown: the player it hits gets {@code effects}. */
    THROWN_EFFECTS(Trigger.THROW,
            Param.effects("effects", List.of(new AbilityEffect("slowness", 1, 10), new AbilityEffect("poison", 1, 10)))),
    /** An ender pearl that flies as one and teleports nobody. */
    FAKE_PEARL(Trigger.THROW),
    /**
     * A fishing rod: reeling in a hook stuck in a block pulls you to it, by {@code pull}, arcing
     * higher with {@code lift};
     * no fall damage while it is in hand, with {@code no-fall-while-held}.
     */
    GRAPPLING_HOOK(Trigger.FISH,
            Param.decimal("pull", 1.0), Param.decimal("lift", 0.4), Param.decimal("max-speed", 4.0),
            Param.bool("no-fall-while-held", true));

    /** How an ability is used. */
    public enum Trigger {
        /** A right-click with the item. */
        RIGHT_CLICK,
        /** Throwing it: a snowball or an egg. */
        THROW,
        /** Hitting a player while holding it. */
        HIT,
        /** Shooting an arrow with it: a bow. */
        SHOOT,
        /** Reeling in with it: a fishing rod. */
        FISH
    }

    /** One setting a type reads, and its value when the file gives none. */
    public record Param(String key, Kind kind, Object fallback) {

        public enum Kind { WHOLE, DECIMAL, BOOL, EFFECT, EFFECTS, STRINGS }

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
