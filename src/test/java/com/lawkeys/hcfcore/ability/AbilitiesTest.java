package com.lawkeys.hcfcore.ability;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Partner items: the configuration, and the rules that need no server. */
class AbilitiesTest {

    private static final Set<String> EFFECTS = Set.of("strength", "resistance", "wither", "speed", "invisibility",
            "regeneration", "slowness", "blindness", "jump_boost", "absorption", "nausea", "poison", "weakness");

    private final List<String> warnings = new ArrayList<>();
    private final AbilityConfig config = new AbilityConfig(item -> !item.startsWith("NOT_"), EFFECTS::contains,
            warnings::add);

    private static Map<String, Object> ability(String type, String material) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", type);
        entry.put("material", material);
        return entry;
    }

    private AbilitySettings parse(Map<String, Object> abilities) {
        return config.parse(Map.of("abilities", abilities));
    }

    @Nested
    class Configuration {

        @Test
        @SuppressWarnings("unchecked")
        void theShippedFileLoadsEveryAbilityWithoutAWarning() throws IOException {
            Map<String, Object> root;
            try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources/abilities.yml"))) {
                root = new Yaml().load(reader);
            }
            AbilitySettings settings = config.parse(root);
            assertTrue(warnings.isEmpty(), "warnings: " + warnings);
            assertEquals(43, settings.abilities().size(), "every ability of the shipped file");
            for (AbilityType type : AbilityType.values()) {
                if (type != AbilityType.COMMANDS) {
                    assertTrue(settings.abilities().stream().anyMatch(a -> a.type() == type), type + " is shipped");
                }
            }
            assertEquals(4, settings.pocketBard().size());
            assertTrue(settings.pocketBard().stream().allMatch(item -> item.cooldownSeconds() == 60),
                    "a minute between two uses of a set");
            assertEquals(10, settings.globalCooldownSeconds());
            assertTrue(settings.disabledIn().citadel());
            assertFalse(settings.disabledIn().warzone());
            assertFalse(settings.ability("grappling-hook").orElseThrow().consume(), "the rod stays");
            assertEquals(List.of("diamond"), settings.ability("pumpkin-reaper").orElseThrow().params().strings("classes"));
            assertEquals(0, settings.ability("pocket-bard").orElseThrow().cooldownSeconds(),
                    "no cooldown on the Pocket Bard itself: its items have theirs");
        }

        @Test
        void aSettingNotGivenTakesItsTypesDefault() {
            Ability thunderbolt = parse(Map.of("bolt", ability("thunderbolt", "GOLD_INGOT"))).ability("bolt").orElseThrow();
            assertEquals(20.0, thunderbolt.params().decimal("chance"));
            assertEquals(1.5, thunderbolt.params().decimal("damage-hearts"));
            assertTrue(thunderbolt.consume() && thunderbolt.glow(), "consumed and shining unless told otherwise");
        }

        @Test
        void anAbilityThatCannotWorkIsLeftOut() {
            Map<String, Object> abilities = new LinkedHashMap<>();
            abilities.put("no-type", ability("teleport-everyone", "STICK"));
            abilities.put("unknown-item", ability("ninja", "NOT_AN_ITEM"));
            abilities.put("thrown-stick", ability("switcher", "STICK"));
            abilities.put("sword-bow", ability("portable-archer", "DIAMOND_SWORD"));
            abilities.put("stone-pearl", ability("fake-pearl", "STONE"));
            abilities.put("stick-hook", ability("grappling-hook", "STICK"));
            abilities.put("thrown-apple", ability("thrown-effects", "APPLE"));
            abilities.put("empty-commands", ability("commands", "STICK"));
            abilities.put("Bad Id!", ability("ninja", "NETHER_STAR"));
            abilities.put("fine", ability("ninja", "NETHER_STAR"));
            AbilitySettings settings = parse(abilities);
            assertEquals(List.of("fine"), settings.abilities().stream().map(Ability::id).toList());
            assertEquals(9, warnings.size());
        }

        @Test
        void aBadValueIsReportedAndReplaced() {
            Map<String, Object> bolt = ability("thunderbolt", "GOLD_INGOT");
            bolt.put("chance", "often");
            bolt.put("radius", 5);
            Ability loaded = parse(Map.of("bolt", bolt)).ability("bolt").orElseThrow();
            assertEquals(20.0, loaded.params().decimal("chance"));
            assertEquals(2, warnings.size(), "a bad value, and a setting a thunderbolt does not read");
        }

        @Test
        void anUnknownEffectIsLeftOutOfItsList() {
            Map<String, Object> belch = ability("belch-bomb", "SLIME_BALL");
            belch.put("effects", List.of(Map.of("effect", "slowness", "level", 2, "seconds", 6),
                    Map.of("effect", "levitation-forever", "level", 1, "seconds", 6)));
            Ability loaded = parse(Map.of("belch", belch)).ability("belch").orElseThrow();
            assertEquals(1, loaded.params().effects("effects").size());
            assertEquals(1, warnings.size());
        }

        @Test
        void aDisabledAbilityIsLeftOutQuietly() {
            Map<String, Object> off = ability("ninja", "NETHER_STAR");
            off.put("enabled", false);
            assertTrue(parse(Map.of("off", off)).abilities().isEmpty());
            assertTrue(warnings.isEmpty());
        }

        @Test
        void aPocketBardSetOutsideItsMenuIsLeftOut() {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("material", "SUGAR");
            item.put("effect", Map.of("effect", "speed", "level", 3, "seconds", 6));
            item.put("slot", 12);
            AbilitySettings settings = config.parse(Map.of("pocket-bard",
                    Map.of("menu-size", 9, "items", Map.of("speed", item))));
            assertTrue(settings.pocketBard().isEmpty());
            assertEquals(1, warnings.size());
        }

        @Test
        void aPocketBardSetWaitsAMinuteUnlessSetOtherwise() {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("material", "SUGAR");
            item.put("effect", Map.of("effect", "speed", "level", 3, "seconds", 6));
            assertEquals(60, config.parse(Map.of("pocket-bard", Map.of("items", Map.of("speed", item))))
                    .pocketBard().get(0).cooldownSeconds());
        }
    }

    @Nested
    class Rules {

        @Test
        void luckyModeRangesFromTheLowestToTheHighestPercent() {
            assertEquals(0.9, AbilityRules.luckyMultiplier(-10, 35, 0.0), 1e-9);
            assertEquals(1.35, AbilityRules.luckyMultiplier(-10, 35, 1.0), 1e-9);
            assertEquals(1.125, AbilityRules.luckyMultiplier(35, -10, 0.5), 1e-9, "the bounds in either order");
        }

        @Test
        void theSunHurtsMoreWithEveryEnemyCaughtUpToItsCap() {
            assertEquals(1.8, AbilityRules.sunDamage(1, 10, 0.9), 1e-9, "0.9 heart for one");
            assertEquals(18.0, AbilityRules.sunDamage(10, 10, 0.9), 1e-9, "9 hearts for ten");
            assertEquals(18.0, AbilityRules.sunDamage(14, 10, 0.9), 1e-9, "no more past the cap");
            assertEquals(0.0, AbilityRules.sunDamage(0, 10, 0.9), 1e-9);
        }

        @Test
        void aShotgunFanIsSpreadEvenly() {
            assertArrayEquals(new double[] {-5, -2.5, 0, 2.5, 5}, AbilityRules.fan(5, 10), 1e-9);
            assertArrayEquals(new double[] {0}, AbilityRules.fan(1, 10), 1e-9, "one straight ahead");
        }

        @Test
        void aLaunchGrowsWithTheSquareRootOfTheHeight() {
            assertEquals(0.8, AbilityRules.launchSpeed(1), 1e-9);
            assertEquals(1.6, AbilityRules.launchSpeed(4), 1e-9);
            assertEquals(0.0, AbilityRules.launchSpeed(-1), 1e-9);
        }

        @Test
        void aPullGoesTowardsItsTargetAndIsCapped() {
            double[] v = AbilityRules.pullVelocity(10, 0, 0, 1.0, 4.0);
            assertTrue(v[0] > 0 && v[1] > 0, "forward, with an arc");
            assertEquals(0.0, v[2], 1e-9);
            double[] far = AbilityRules.pullVelocity(200, 0, 0, 1.0, 4.0);
            assertEquals(4.0, Math.sqrt(far[0] * far[0] + far[1] * far[1] + far[2] * far[2]), 1e-9);
            assertArrayEquals(new double[] {0, 0, 0}, AbilityRules.pullVelocity(0, 0, 0, 1.0, 4.0), 1e-9);
        }

        @Test
        void aBlockedEntryCoversItsWholeFamily() {
            List<String> blocked = List.of("CHEST", "FENCE_GATE", "TRAPDOOR");
            // CHEST is a block's own name; FENCE_GATE and TRAPDOOR are families.
            java.util.function.Predicate<String> blocks = Set.of("CHEST", "TRAPPED_CHEST", "ENDER_CHEST")::contains;
            assertTrue(AbilityRules.blocked(blocked, "CHEST", blocks));
            assertTrue(AbilityRules.blocked(blocked, "OAK_FENCE_GATE", blocks));
            assertTrue(AbilityRules.blocked(blocked, "IRON_TRAPDOOR", blocks));
            assertFalse(AbilityRules.blocked(blocked, "TRAPPED_CHEST", blocks), "a block's name is that block alone");
            assertFalse(AbilityRules.blocked(blocked, "ENDER_CHEST", blocks));
            assertFalse(AbilityRules.blocked(blocked, "OAK_FENCE", blocks));
        }

        @Test
        void aChanceIsAPercent() {
            assertTrue(AbilityRules.chance(20, 0.19));
            assertFalse(AbilityRules.chance(20, 0.2));
            assertFalse(AbilityRules.chance(0, 0.0));
        }
    }

    @Nested
    class Hits {

        private final HitCounter counter = new HitCounter();
        private final UUID attacker = UUID.randomUUID();
        private final UUID victim = UUID.randomUUID();

        @Test
        void hitsOnTheSamePlayerAddUp() {
            assertEquals(1, counter.hit(attacker, "bone", victim, 0, 10_000));
            assertEquals(2, counter.hit(attacker, "bone", victim, 5_000, 10_000));
            assertEquals(3, counter.hit(attacker, "bone", victim, 9_000, 10_000));
        }

        @Test
        void anotherPlayerOrAPauseStartsOver() {
            counter.hit(attacker, "bone", victim, 0, 10_000);
            assertEquals(1, counter.hit(attacker, "bone", UUID.randomUUID(), 1_000, 10_000), "somebody else");
            counter.hit(attacker, "bone", victim, 2_000, 10_000);
            assertEquals(1, counter.hit(attacker, "bone", victim, 13_000, 10_000), "too long after");
        }

        @Test
        void eachAbilityCountsApart() {
            counter.hit(attacker, "bone", victim, 0, 10_000);
            assertEquals(1, counter.hit(attacker, "chaos", victim, 1_000, 10_000));
        }
    }
}
