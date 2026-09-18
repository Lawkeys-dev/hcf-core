package com.lawkeys.hcfcore.enchant;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The kinds of item a custom enchant can go on.
 *
 * <p>Read from the material's name - {@code DIAMOND_HELMET} is a helmet, whatever it
 * is made of - so it is pure and tested, and a material a later version adds in the
 * same family is covered without a change here.
 */
public enum EnchantTarget {
    HELMET, CHESTPLATE, LEGGINGS, BOOTS,
    SWORD, AXE, PICKAXE, SHOVEL, HOE,
    BOW, CROSSBOW, TRIDENT;

    /** The four armour pieces. */
    public static final Set<EnchantTarget> ARMOR = EnumSet.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS);

    /** @return what kind of item this is, or empty when a custom enchant can go on nothing of its kind */
    public static Optional<EnchantTarget> of(String materialName) {
        if (materialName == null) {
            return Optional.empty();
        }
        String name = materialName.toUpperCase(Locale.ROOT);
        if (name.endsWith("_HELMET")) {
            return Optional.of(HELMET);
        }
        if (name.endsWith("_CHESTPLATE")) {
            return Optional.of(CHESTPLATE);
        }
        if (name.endsWith("_LEGGINGS")) {
            return Optional.of(LEGGINGS);
        }
        if (name.endsWith("_BOOTS")) {
            return Optional.of(BOOTS);
        }
        if (name.endsWith("_SWORD")) {
            return Optional.of(SWORD);
        }
        if (name.endsWith("_PICKAXE")) {
            return Optional.of(PICKAXE);
        }
        if (name.endsWith("_AXE")) {
            return Optional.of(AXE);
        }
        if (name.endsWith("_SHOVEL")) {
            return Optional.of(SHOVEL);
        }
        if (name.endsWith("_HOE")) {
            return Optional.of(HOE);
        }
        return switch (name) {
            case "BOW" -> Optional.of(BOW);
            case "CROSSBOW" -> Optional.of(CROSSBOW);
            case "TRIDENT" -> Optional.of(TRIDENT);
            default -> Optional.empty();
        };
    }

    /**
     * @return the targets a word from {@code enchants.yml} names: one kind, or a group
     *         - {@code armor}, {@code tool} (pickaxe, axe, shovel, hoe), {@code weapon}
     *         (sword, axe) - or empty for a word that names nothing
     */
    public static Set<EnchantTarget> parse(String word) {
        if (word == null) {
            return Set.of();
        }
        return switch (word.trim().toLowerCase(Locale.ROOT)) {
            case "armor", "armour" -> ARMOR;
            case "tool", "tools" -> EnumSet.of(PICKAXE, AXE, SHOVEL, HOE);
            case "weapon", "weapons" -> EnumSet.of(SWORD, AXE);
            default -> {
                try {
                    yield EnumSet.of(valueOf(word.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    yield Set.of();
                }
            }
        };
    }
}
