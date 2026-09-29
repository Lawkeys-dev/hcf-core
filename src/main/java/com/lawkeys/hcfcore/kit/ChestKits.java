package com.lawkeys.hcfcore.kit;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * A kit laid out from a chest ({@code /kit fromchest}): a container holds no armour
 * slots, so the pieces of armour in it are put on - the first helmet, chestplate,
 * leggings and boots found - and everything else fills the inventory in the chest's
 * order. What does not fit the 36 slots of an inventory is left out.
 *
 * <p>A container larger than an inventory - a double chest - is taken as it is,
 * slot for slot and no armour worn: no inventory could hold it, so it is a kit for a
 * {@code [Refill]} sign's window, which shows it in that very order.
 *
 * <p>Pure Java: item names in, the player-inventory slot of each out.
 */
public final class ChestKits {

    /**
     * @return how many slots a kit from a chest of that size holds: an inventory's, or
     *         the whole chest when it is larger
     */
    public static int size(int chestSize) {
        return Math.max(SIZE, chestSize);
    }

    /** A player inventory's contents: 36 slots, four armour slots, the off hand. */
    public static final int SIZE = 41;
    private static final int BOOTS = 36;
    private static final int LEGGINGS = 37;
    private static final int CHESTPLATE = 38;
    private static final int HELMET = 39;
    private static final int MAIN = 36;

    private ChestKits() {
    }

    /**
     * @param materials the chest's items in order, {@code null} for an empty slot
     * @return for each, its slot in the kit, or {@code -1} when it is left out
     */
    public static int[] slots(List<String> materials) {
        int[] slots = new int[materials.size()];
        if (materials.size() > SIZE) {
            for (int i = 0; i < slots.length; i++) {
                slots[i] = materials.get(i) == null ? -1 : i;
            }
            return slots;
        }
        Arrays.fill(slots, -1);
        boolean[] worn = new boolean[SIZE];
        int next = 0;
        for (int i = 0; i < materials.size(); i++) {
            String material = materials.get(i);
            if (material == null) {
                continue;
            }
            int armour = armourSlot(material.toUpperCase(Locale.ROOT));
            if (armour >= 0 && !worn[armour]) {
                worn[armour] = true;
                slots[i] = armour;
            } else if (next < MAIN) {
                slots[i] = next++;
            }
        }
        return slots;
    }

    private static int armourSlot(String material) {
        if (material.endsWith("_HELMET")) {
            return HELMET;
        }
        if (material.endsWith("_CHESTPLATE")) {
            return CHESTPLATE;
        }
        if (material.endsWith("_LEGGINGS")) {
            return LEGGINGS;
        }
        if (material.endsWith("_BOOTS")) {
            return BOOTS;
        }
        return -1;
    }
}
