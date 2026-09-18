package com.lawkeys.hcfcore.pvpclass;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One class, as {@code classes.yml} describes it. A player is in a class while
 * wearing its whole armour set - helmet, chestplate, leggings and boots - once the
 * warmup is over.
 *
 * <p>Every part below the armour is optional, and any class may use any of them:
 * the shipped classes are just the usual combinations (the Bard's energy and held
 * items, the Archer's tag, the Rogue's backstab, the Miner's invisibility).
 *
 * @param id              identifier, lower case, unique
 * @param displayName     shown to players, colour codes allowed
 * @param armor           four item names: helmet, chestplate, leggings, boots
 * @param permission      needed to use the class; empty for everybody
 * @param maxPerTeam      how many members of one team may hold it at once; {@code 0} for no limit
 * @param passiveEffects  effect key to level, kept for as long as the class is active
 * @param energy          the class's energy, or {@code null} for none
 * @param heldEffects     item name to the effect holding it hands out
 * @param clickEffects    item name to the effect a right-click with it hands out
 * @param archerTag       the Archer's mark on an arrow hit, or {@code null}
 * @param backstab        the Rogue's backstab, or {@code null}
 * @param invisibleBelowY invisible while below this height, or {@code null}
 * @param dyeEffects      dye name to the effect this class's arrows may add while
 *                        its set is dyed that colour (leather only)
 */
public record PvpClass(String id, String displayName, List<String> armor, String permission, int maxPerTeam,
                       Map<String, Integer> passiveEffects, Energy energy,
                       Map<String, HeldEffect> heldEffects, Map<String, ClickEffect> clickEffects,
                       ArcherTag archerTag, Backstab backstab, Integer invisibleBelowY,
                       Map<String, DyeEffect> dyeEffects, Long warmupSeconds) {

    public static final int ARMOR_PIECES = 4;

    public PvpClass {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        armor = List.copyOf(armor);
        if (armor.size() != ARMOR_PIECES) {
            throw new IllegalArgumentException("armor must name 4 pieces, was " + armor);
        }
        permission = permission == null ? "" : permission.trim();
        maxPerTeam = Math.max(0, maxPerTeam);
        passiveEffects = Map.copyOf(passiveEffects);
        heldEffects = Map.copyOf(heldEffects);
        clickEffects = Map.copyOf(clickEffects);
        dyeEffects = Map.copyOf(dyeEffects);
    }

    /** @return whether these four pieces, helmet to boots, are this class's set */
    public boolean isWornAs(List<String> worn) {
        return armor.equals(worn);
    }

    public boolean hasEnergy() {
        return energy != null;
    }

    /**
     * @return the material the whole set is made of, read as a word - {@code Golden}
     *         for four {@code GOLDEN_} pieces - or empty for a mixed set
     */
    public Optional<String> armorSet() {
        String first = armor.get(0);
        int underscore = first.indexOf('_');
        if (underscore <= 0) {
            return Optional.empty();
        }
        String prefix = first.substring(0, underscore + 1);
        boolean same = armor.stream().allMatch(piece -> piece.startsWith(prefix));
        return same ? Optional.of(readableItem(prefix.substring(0, underscore))) : Optional.empty();
    }

    /** @return the four pieces, readable: {@code Golden Helmet, Golden Chestplate, ...} */
    public String armorPieces() {
        return String.join(", ", armor.stream().map(PvpClass::readableItem).toList());
    }

    /** @return an item name read as words: {@code GOLDEN_SWORD} is {@code Golden Sword} */
    public static String readableItem(String item) {
        StringBuilder out = new StringBuilder();
        for (String word : item.toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
