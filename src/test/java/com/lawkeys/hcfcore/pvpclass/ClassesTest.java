package com.lawkeys.hcfcore.pvpclass;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassesTest {

    private static final Set<String> ITEMS = Set.of(
            "GOLDEN_HELMET", "GOLDEN_CHESTPLATE", "GOLDEN_LEGGINGS", "GOLDEN_BOOTS",
            "LEATHER_HELMET", "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS", "LEATHER_BOOTS",
            "CHAINMAIL_HELMET", "CHAINMAIL_CHESTPLATE", "CHAINMAIL_LEGGINGS", "CHAINMAIL_BOOTS",
            "DIAMOND_HELMET", "DIAMOND_CHESTPLATE", "DIAMOND_LEGGINGS", "DIAMOND_BOOTS",
            "SUGAR", "SPIDER_EYE", "GOLDEN_SWORD", "FEATHER", "BLAZE_POWDER");
    private static final Set<String> EFFECTS = Set.of("speed", "strength", "resistance", "wither", "jump_boost",
            "poison");
    /** The game's colours for a few dyes, as Paper's DyeColor#getColor gives them. */
    private static final Map<String, Integer> PALETTE = Map.of(
            "GREEN", 0x5E7C16, "LIME", 0x80C71F, "RED", 0xB02E26, "BLACK", 0x1D1D21, "WHITE", 0xF9FFFE);

    private static final List<String> GOLD = List.of("GOLDEN_HELMET", "GOLDEN_CHESTPLATE", "GOLDEN_LEGGINGS", "GOLDEN_BOOTS");
    private static final List<String> LEATHER = List.of("LEATHER_HELMET", "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS", "LEATHER_BOOTS");

    private final List<String> warnings = new ArrayList<>();
    private final ClassConfig config = new ClassConfig(ITEMS::contains, EFFECTS::contains, PALETTE::containsKey,
            warnings::add);

    private static Map<String, Object> armor(String prefix) {
        Map<String, Object> armor = new LinkedHashMap<>();
        armor.put("helmet", prefix + "_HELMET");
        armor.put("chestplate", prefix + "_CHESTPLATE");
        armor.put("leggings", prefix + "_LEGGINGS");
        armor.put("boots", prefix + "_BOOTS");
        return armor;
    }

    private static Map<String, Object> root(Map<String, Object> classes) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("warmup-seconds", 10);
        root.put("classes", classes);
        return root;
    }

    private static Map<String, Object> bard() {
        Map<String, Object> bard = new LinkedHashMap<>();
        bard.put("display-name", "&6Bard");
        bard.put("armor", armor("GOLDEN"));
        bard.put("passive-effects", Map.of("speed", 2));
        bard.put("energy", Map.of("max", 100, "per-second", 1.0));
        bard.put("held-effects", Map.of("SUGAR", Map.of("effect", "speed", "level", 2, "seconds", 5,
                "radius", 20, "targets", "team")));
        bard.put("click-effects", Map.of("SPIDER_EYE", Map.of("effect", "minecraft:wither", "level", 2,
                "seconds", 5, "targets", "enemies", "energy", 35)));
        return bard;
    }

    @Nested
    class Configuration {

        @Test
        void aFullClassIsReadWithEveryPart() {
            ClassSettings settings = config.parse(root(Map.of("bard", bard())));
            PvpClass bard = settings.find("bard").orElseThrow();
            assertEquals(GOLD, bard.armor());
            assertEquals(Map.of("speed", 2), bard.passiveEffects());
            assertEquals(100.0, bard.energy().max());
            assertEquals(ClassTarget.TEAM, bard.heldEffects().get("SUGAR").target());
            ClickEffect wither = bard.clickEffects().get("SPIDER_EYE");
            assertEquals("wither", wither.effect().effect(), "the minecraft: namespace is dropped");
            assertEquals(ClassTarget.ENEMIES, wither.target());
            assertEquals(35, wither.energyCost());
            assertTrue(warnings.isEmpty(), warnings::toString);
        }

        @Test
        void aClassWithAnUnknownArmourPieceIsSkippedAndTheOthersLoad() {
            Map<String, Object> broken = new LinkedHashMap<>();
            Map<String, Object> armor = armor("GOLDEN");
            armor.put("boots", "GOLD_BOOTS");
            broken.put("armor", armor);
            Map<String, Object> archer = Map.of("armor", armor("LEATHER"));
            ClassSettings settings = config.parse(root(new LinkedHashMap<>(Map.of("broken", broken, "archer", archer))));
            assertTrue(settings.find("broken").isEmpty());
            assertTrue(settings.find("archer").isPresent());
            assertEquals(1, warnings.size());
        }

        @Test
        void twoClassesOnTheSameSetKeepTheFirst() {
            Map<String, Object> classes = new LinkedHashMap<>();
            classes.put("bard", bard());
            classes.put("gold-copy", Map.of("armor", armor("GOLDEN")));
            ClassSettings settings = config.parse(root(classes));
            assertEquals(List.of("bard"), settings.classes().stream().map(PvpClass::id).toList());
            assertEquals(1, warnings.size());
        }

        @Test
        void anUnknownEffectIsLeftOutAndTheClassStays() {
            Map<String, Object> archer = new LinkedHashMap<>();
            archer.put("armor", armor("LEATHER"));
            archer.put("passive-effects", Map.of("speeed", 3, "resistance", 2));
            PvpClass loaded = config.parse(root(Map.of("archer", archer))).find("archer").orElseThrow();
            assertEquals(Map.of("resistance", 2), loaded.passiveEffects());
            assertEquals(1, warnings.size());
        }

        @Test
        void anEnergyCostWithoutEnergyIsIgnored() {
            Map<String, Object> archer = new LinkedHashMap<>();
            archer.put("armor", armor("LEATHER"));
            archer.put("click-effects", Map.of("SUGAR", Map.of("effect", "speed", "level", 4, "energy", 20)));
            PvpClass loaded = config.parse(root(Map.of("archer", archer))).find("archer").orElseThrow();
            assertEquals(0, loaded.clickEffects().get("SUGAR").energyCost());
            assertEquals(1, warnings.size());
        }

        @Test
        void aBadValueFallsBackToItsDefault() {
            Map<String, Object> root = root(Map.of("bard", bard()));
            root.put("warmup-seconds", "ten");
            assertEquals(10, config.parse(root).warmupSeconds());
            assertEquals(1, warnings.size());
        }

        @Test
        void aTeamEffectReachesItsUserUnlessLeftOut() {
            Map<String, Object> bard = new LinkedHashMap<>();
            bard.put("armor", armor("GOLDEN"));
            bard.put("held-effects", Map.of(
                    "BLAZE_POWDER", Map.of("effect", "strength", "level", 1, "targets", "team", "include-self", false),
                    "SUGAR", Map.of("effect", "speed", "level", 2, "targets", "team")));
            bard.put("click-effects", Map.of(
                    "BLAZE_POWDER", Map.of("effect", "strength", "level", 2, "targets", "team")));
            PvpClass loaded = config.parse(root(Map.of("bard", bard))).find("bard").orElseThrow();
            assertFalse(loaded.heldEffects().get("BLAZE_POWDER").includeSelf(), "Strength to the team only");
            assertTrue(loaded.heldEffects().get("SUGAR").includeSelf(), "the Bard gets it too by default");
            assertTrue(loaded.clickEffects().get("BLAZE_POWDER").includeSelf(), "the burst reaches the Bard");
            assertTrue(warnings.isEmpty());
        }

        @Test
        void onlyATeamEffectCanLeaveItsUserOut() {
            Map<String, Object> rogue = new LinkedHashMap<>();
            rogue.put("armor", armor("CHAINMAIL"));
            rogue.put("click-effects", Map.of("FEATHER",
                    Map.of("effect", "jump_boost", "level", 5, "targets", "self", "include-self", false)));
            PvpClass loaded = config.parse(root(Map.of("rogue", rogue))).find("rogue").orElseThrow();
            assertTrue(loaded.clickEffects().get("FEATHER").includeSelf());
            assertEquals(1, warnings.size());
        }

        @Test
        void aClassMayHaveItsOwnWarmup() {
            Map<String, Object> diamond = new LinkedHashMap<>();
            diamond.put("armor", armor("DIAMOND"));
            diamond.put("warmup-seconds", 0);
            ClassSettings settings = config.parse(root(Map.of("diamond", diamond, "bard", bard())));
            assertEquals(0, settings.warmupOf(settings.find("diamond").orElseThrow()), "the Diamond turns on at once");
            assertEquals(10, settings.warmupOf(settings.find("bard").orElseThrow()), "the shared warmup otherwise");
        }

        @Test
        void anArcherTagIsFifteenPercentUnlessSetOtherwise() {
            Map<String, Object> archer = new LinkedHashMap<>();
            archer.put("armor", armor("LEATHER"));
            archer.put("archer-tag", Map.of("seconds", 10));
            PvpClass loaded = config.parse(root(Map.of("archer", archer))).find("archer").orElseThrow();
            assertEquals(1.15, loaded.archerTag().damageMultiplier());
            assertEquals(15, loaded.archerTag().percent());
        }

        @Test
        void heldEffectsAreRenewedFourTimesASecondUnlessSetOtherwise() {
            assertEquals(5, config.parse(root(Map.of("bard", bard()))).heldIntervalTicks());
            Map<String, Object> root = root(Map.of("bard", bard()));
            root.put("held-effect-interval-ticks", 10);
            assertEquals(10, config.parse(root).heldIntervalTicks());
            assertTrue(warnings.isEmpty());
        }

        @Test
        void aHeldIntervalOutsideOneToTwentyTicksIsReported() {
            for (Object bad : List.of(0, 21, 2.5, "fast")) {
                Map<String, Object> root = root(Map.of("bard", bard()));
                root.put("held-effect-interval-ticks", bad);
                assertEquals(5, config.parse(root).heldIntervalTicks(), "for " + bad);
            }
            assertEquals(4, warnings.size());
        }

        @Test
        void clickEffectsTargetTheirUserByDefault() {
            Map<String, Object> rogue = new LinkedHashMap<>();
            rogue.put("armor", armor("CHAINMAIL"));
            rogue.put("click-effects", Map.of("FEATHER", Map.of("effect", "jump_boost", "level", 5)));
            PvpClass loaded = config.parse(root(Map.of("rogue", rogue))).find("rogue").orElseThrow();
            assertEquals(ClassTarget.SELF, loaded.clickEffects().get("FEATHER").target());
            assertTrue(loaded.clickEffects().get("FEATHER").consume());
        }

        @Test
        void theSpecialPartsAreReadForAnyClass() {
            Map<String, Object> custom = new LinkedHashMap<>();
            custom.put("armor", armor("LEATHER"));
            custom.put("archer-tag", Map.of("seconds", 8, "damage-multiplier", 1.5));
            custom.put("backstab", Map.of("weapon", "golden_sword", "damage", 4.0));
            custom.put("invisible-below-y", -20);
            PvpClass loaded = config.parse(root(Map.of("assassin", custom))).find("assassin").orElseThrow();
            assertEquals(50, loaded.archerTag().percent());
            assertEquals("GOLDEN_SWORD", loaded.backstab().weapon());
            assertEquals(-20, loaded.invisibleBelowY());
            assertTrue(warnings.isEmpty(), warnings::toString);
        }

        @Test
        void anInvalidIdIsSkipped() {
            ClassSettings settings = config.parse(root(Map.of("My Class", Map.of("armor", armor("LEATHER")))));
            assertTrue(settings.classes().isEmpty());
            assertEquals(1, warnings.size());
        }
    }

    @Nested
    class Dyes {

        private Map<String, Object> archer(Map<String, Object> dyeEffects) {
            Map<String, Object> archer = new LinkedHashMap<>();
            archer.put("armor", armor("LEATHER"));
            archer.put("dye-effects", dyeEffects);
            return archer;
        }

        @Test
        void aColourGivesItsEffectAndChance() {
            PvpClass archer = config.parse(root(Map.of("archer", archer(Map.of("green",
                    Map.of("effect", "poison", "level", 1, "seconds", 10, "chance", 20)))))).find("archer").orElseThrow();
            DyeEffect green = archer.dyeEffects().get("GREEN");
            assertEquals("poison", green.effect().effect());
            assertEquals(10, green.effect().seconds());
            assertEquals(20.0, green.chance());
            assertTrue(warnings.isEmpty(), warnings::toString);
        }

        @Test
        void anUnknownColourIsLeftOut() {
            PvpClass archer = config.parse(root(Map.of("archer", archer(Map.of("CACTUS",
                    Map.of("effect", "poison", "chance", 20)))))).find("archer").orElseThrow();
            assertTrue(archer.dyeEffects().isEmpty());
            assertEquals(1, warnings.size());
        }

        @Test
        void aChanceAboveAHundredIsRefused() {
            PvpClass archer = config.parse(root(Map.of("archer", archer(Map.of("GREEN",
                    Map.of("effect", "poison", "chance", 150)))))).find("archer").orElseThrow();
            assertTrue(archer.dyeEffects().isEmpty());
            assertEquals(1, warnings.size());
        }

        @Test
        void aSetThatCannotBeDyedIsReported() {
            Map<String, Object> bard = bard();
            bard.put("dye-effects", Map.of("GREEN", Map.of("effect", "poison", "chance", 20)));
            config.parse(root(Map.of("bard", bard)));
            assertEquals(1, warnings.size(), "gold cannot be dyed");
        }

        @Test
        void theChanceDecidesTheRoll() {
            DyeEffect twenty = new DyeEffect(new ClassEffect("poison", 1, 10), 20);
            assertTrue(twenty.applies(0.0));
            assertTrue(twenty.applies(0.1999));
            assertFalse(twenty.applies(0.2));
            assertFalse(new DyeEffect(new ClassEffect("poison", 1, 10), 0).applies(0.0), "0% never applies");
            assertTrue(new DyeEffect(new ClassEffect("poison", 1, 10), 100).applies(0.9999), "100% always does");
        }

        @Test
        void oneDyeReadsAsItself() {
            assertEquals("GREEN", DyeColours.nearest(0x5E7C16, PALETTE));
        }

        @Test
        void aMixReadsAsTheClosestDye() {
            // One green and one lime dye: the game averages them to #6FA11A (its brightening
            // changes nothing here, both dyes peak in the green channel), a hair closer to green.
            assertEquals("GREEN", DyeColours.nearest(0x6FA11A, PALETTE));
            // One green and two lime average to #74AE1C: lime.
            assertEquals("LIME", DyeColours.nearest(0x74AE1C, PALETTE));
        }

        @Test
        void aSetHasAColourOnlyWhenAllFourPiecesShareIt() {
            List<Integer> green = List.of(0x5E7C16, 0x5E7C16, 0x5A7A18, 0x5E7C16);
            assertEquals("GREEN", DyeColours.ofSet(green, PALETTE).orElseThrow());
            List<Integer> oneRed = List.of(0x5E7C16, 0xB02E26, 0x5E7C16, 0x5E7C16);
            assertTrue(DyeColours.ofSet(oneRed, PALETTE).isEmpty());
            List<Integer> oneUndyed = new ArrayList<>(green);
            oneUndyed.set(2, null);
            assertTrue(DyeColours.ofSet(oneUndyed, PALETTE).isEmpty(), "an undyed piece means no colour");
        }
    }

    @Nested
    class Warmup {

        private final UUID player = UUID.randomUUID();
        private final ClassSettings settings = config.parse(root(Map.of("bard", bard(),
                "archer", Map.of("armor", armor("LEATHER")))));
        private final ClassManager manager = new ClassManager(() -> settings);
        private final PvpClass bard = settings.find("bard").orElseThrow();
        private final PvpClass archer = settings.find("archer").orElseThrow();
        private final ClassManager.TeamRoom always = pvpClass -> true;

        private List<ClassManager.Kind> kinds(List<ClassManager.Change> changes) {
            return changes.stream().map(ClassManager.Change::kind).toList();
        }

        @Test
        void aClassTurnsOnOnlyOnceTheWarmupIsOver() {
            assertEquals(List.of(ClassManager.Kind.WARMUP_STARTED), kinds(manager.update(player, bard, 0, always)));
            assertTrue(manager.update(player, bard, 9_999, always).isEmpty());
            assertTrue(manager.active(player).isEmpty());
            assertEquals(1, manager.warmupRemaining(player, 9_001));
            assertEquals(List.of(ClassManager.Kind.ACTIVATED), kinds(manager.update(player, bard, 10_000, always)));
            assertEquals("bard", manager.active(player).orElseThrow().id());
        }

        @Test
        void aClassWithoutWarmupTurnsOnAtOnce() {
            ClassSettings noWarmup = config.parse(root(Map.of("diamond",
                    Map.of("armor", armor("DIAMOND"), "warmup-seconds", 0))));
            ClassManager instant = new ClassManager(() -> noWarmup);
            PvpClass diamond = noWarmup.find("diamond").orElseThrow();
            assertEquals(List.of(ClassManager.Kind.WARMUP_STARTED, ClassManager.Kind.ACTIVATED),
                    kinds(instant.update(player, diamond, 0, always)));
            assertEquals("diamond", instant.active(player).orElseThrow().id());
        }

        @Test
        void takingAPieceOffDuringTheWarmupStartsItOver() {
            manager.update(player, bard, 0, always);
            manager.update(player, null, 5_000, always);
            manager.update(player, bard, 6_000, always);
            assertTrue(manager.update(player, bard, 10_000, always).isEmpty(), "the warmup restarted at 6s");
            assertEquals(List.of(ClassManager.Kind.ACTIVATED), kinds(manager.update(player, bard, 16_000, always)));
        }

        @Test
        void takingAPieceOffTurnsTheClassOffAtOnce() {
            manager.update(player, bard, 0, always);
            manager.update(player, bard, 10_000, always);
            assertEquals(List.of(ClassManager.Kind.DEACTIVATED), kinds(manager.update(player, null, 10_500, always)));
            assertTrue(manager.active(player).isEmpty());
        }

        @Test
        void changingSetTurnsTheOldClassOffAndWarmsTheNewOneUp() {
            manager.update(player, bard, 0, always);
            manager.update(player, bard, 10_000, always);
            assertEquals(List.of(ClassManager.Kind.DEACTIVATED, ClassManager.Kind.WARMUP_STARTED),
                    kinds(manager.update(player, archer, 11_000, always)));
            assertTrue(manager.active(player).isEmpty());
        }

        @Test
        void aFullTeamRefusesTheClassUntilTheSetComesOff() {
            manager.update(player, bard, 0, always);
            assertEquals(List.of(ClassManager.Kind.REFUSED_TEAM_LIMIT),
                    kinds(manager.update(player, bard, 10_000, pvpClass -> false)));
            assertTrue(manager.update(player, bard, 20_000, always).isEmpty(), "not retried while still worn");
            manager.update(player, null, 21_000, always);
            assertEquals(List.of(ClassManager.Kind.WARMUP_STARTED), kinds(manager.update(player, bard, 22_000, always)));
        }

        @Test
        void countsWhoIsInAClass() {
            UUID other = UUID.randomUUID();
            manager.update(player, bard, 0, always);
            manager.update(player, bard, 10_000, always);
            manager.update(other, bard, 0, always);
            assertEquals(1, manager.countIn(List.of(player, other), "bard"), "the other is still warming up");
        }

        @Test
        void energyFillsOverTimeUpToItsMaximumAndIsSpent() {
            manager.update(player, bard, 0, always);
            manager.update(player, bard, 10_000, always);
            assertEquals(30.0, manager.energy(player, 40_000), 1e-9);
            assertFalse(manager.spend(player, 35, 40_000), "not enough yet");
            assertEquals(30.0, manager.energy(player, 40_000), 1e-9, "a refused spend changes nothing");
            assertTrue(manager.spend(player, 20, 40_000));
            assertEquals(10.0, manager.energy(player, 40_000), 1e-9);
            assertEquals(100.0, manager.energy(player, 1_000_000), 1e-9, "capped at max");
        }

        @Test
        void energyStartsOverWithTheClass() {
            manager.update(player, bard, 0, always);
            manager.update(player, bard, 10_000, always);
            manager.update(player, null, 60_000, always);
            manager.update(player, bard, 60_000, always);
            manager.update(player, bard, 70_000, always);
            assertEquals(0.0, manager.energy(player, 70_000), 1e-9);
        }

        @Test
        void aClassWithoutEnergyHasNone() {
            manager.update(player, archer, 0, always);
            manager.update(player, archer, 10_000, always);
            assertEquals(0.0, manager.energy(player, 100_000), 1e-9);
            assertTrue(manager.spend(player, 0, 100_000), "a free click needs no energy");
        }
    }

    @Nested
    class Backstabs {

        private final Backstab backstab = new Backstab("GOLDEN_SWORD", 6.0, 15, true, 60);

        @Test
        void fromBehindFacingTheSameWayIsABackstab() {
            // The victim faces +Z (yaw 0); the attacker stands at -Z, also facing +Z.
            assertTrue(backstab.isBehind(0, 0, 0f, 0, -1.5, 0f));
            assertTrue(backstab.isBehind(0, 0, 0f, 0.5, -1.5, 40f), "within the angle");
        }

        @Test
        void faceToFaceIsNot() {
            assertFalse(backstab.isBehind(0, 0, 0f, 0, 1.5, 180f));
        }

        @Test
        void standingBehindButFacingSidewaysIsNot() {
            assertFalse(backstab.isBehind(0, 0, 0f, 0, -1.5, 90f));
        }

        @Test
        void facingTheSameWayButInFrontIsNot() {
            // Walking backwards into somebody's face.
            assertFalse(backstab.isBehind(0, 0, 0f, 0, 1.5, 0f));
        }

        @Test
        void yawsWrapAround() {
            assertEquals(20.0, Backstab.angleBetween(350f, 10f), 1e-9);
            assertEquals(180.0, Backstab.angleBetween(0f, 180f), 1e-9);
            assertTrue(backstab.isBehind(0, 0, 350f, -0.2, -1.5, 10f));
        }
    }

    @Nested
    class ArcherTagging {

        private final UUID victim = UUID.randomUUID();
        private final ArcherTags tags = new ArcherTags();
        private final ArcherTag tag = new ArcherTag(10, 1.25);

        @Test
        void aTaggedPlayerTakesMoreDamageUntilTheMarkEnds() {
            assertEquals(1.0, tags.multiplier(victim, 0));
            tags.tag(victim, tag, 0);
            assertEquals(1.25, tags.multiplier(victim, 9_999));
            assertEquals(1, tags.remaining(victim, 9_001));
            assertEquals(1.0, tags.multiplier(victim, 10_000));
            assertEquals(0, tags.remaining(victim, 10_000));
        }

        @Test
        void aNewHitStartsTheMarkOver() {
            tags.tag(victim, tag, 0);
            tags.tag(victim, tag, 8_000);
            assertEquals(1.25, tags.multiplier(victim, 15_000));
        }

        @Test
        void thePercentageIsRead() {
            assertEquals(25, tag.percent());
        }
    }

    @Nested
    class Text {

        @Test
        void effectsReadAsWordsWithRomanLevels() {
            assertEquals("Jump Boost VII", new ClassEffect("minecraft:jump_boost", 7, 5).displayName());
            assertEquals("Speed 12", new ClassEffect("speed", 12, 5).displayName());
        }

        @Test
        void aWholeSetIsNamedByItsMaterial() {
            PvpClass bard = config.parse(root(Map.of("bard", bard()))).find("bard").orElseThrow();
            assertEquals("Golden", bard.armorSet().orElseThrow());
            assertEquals("Golden Helmet, Golden Chestplate, Golden Leggings, Golden Boots", bard.armorPieces());
        }

        @Test
        void aMixedSetHasNoMaterial() {
            Map<String, Object> armor = armor("GOLDEN");
            armor.put("boots", "LEATHER_BOOTS");
            PvpClass mixed = config.parse(root(Map.of("mixed", Map.of("armor", armor)))).find("mixed").orElseThrow();
            assertTrue(mixed.armorSet().isEmpty());
        }

        @Test
        void targetsReadTheirConfigurationWords() {
            assertEquals(ClassTarget.TEAM_AND_ALLIES, ClassTarget.parse("team-and-allies").orElseThrow());
            assertEquals("team-and-allies", ClassTarget.TEAM_AND_ALLIES.configName());
            assertTrue(ClassTarget.parse("friends").isEmpty());
        }

        @Test
        void energyNeverGoesAboveItsMaximum() {
            Energy energy = new Energy(100, 2);
            assertEquals(100.0, energy.after(95, 10_000));
            assertEquals(20.0, energy.after(0, 10_000));
        }

        @Test
        void matchingFindsTheClassWhoseSetIsWorn() {
            ClassSettings settings = config.parse(root(Map.of("bard", bard(),
                    "archer", Map.of("armor", armor("LEATHER")))));
            assertEquals("archer", settings.matching(LEATHER).orElseThrow().id());
            List<String> mixed = new ArrayList<>(LEATHER);
            mixed.set(3, "GOLDEN_BOOTS");
            assertTrue(settings.matching(mixed).isEmpty());
            assertNotNull(settings.find("BARD").orElse(null), "ids are found whatever their case");
            assertNull(settings.find("mage").orElse(null));
        }
    }
}
