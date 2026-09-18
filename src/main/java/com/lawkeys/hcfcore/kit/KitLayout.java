package com.lawkeys.hcfcore.kit;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Where one player wants a kit's items: the kit layout editor (FEATURES.md section 9,
 * the "editmenu" the project owner described on 12/09/2026 as a kit layout editor).
 *
 * <p>Pure Java. A layout is a set of moves, kit slot to player slot, in the slot
 * numbering of the player's inventory as the server stores it and as
 * {@code /kit create} saves it: {@code 0}-{@code 8} the hotbar, {@code 9}-{@code 35}
 * the storage rows, {@code 36}-{@code 39} the armour, {@code 40} the off hand. Armour
 * is not moved - it has one place to be - so a layout only ever moves the
 * {@linkplain #isMovable movable} slots.
 *
 * <p>A layout never loses an item. {@link #placement} gives every filled slot a
 * distinct destination, whatever the layout says: a move to a slot already taken, or
 * one saved for a kit that has since been re-created with other slots, falls back to
 * the item's own slot and then to the first free one.
 */
public final class KitLayout {

    /** The size of a saved inventory: storage, armour, off hand. */
    public static final int SLOTS = 41;
    public static final int OFF_HAND = 40;

    /** The editor window: five rows. */
    public static final int EDITOR_SIZE = 45;
    /** Where the off hand sits in the editor, under the hotbar's first slot. */
    public static final int EDITOR_OFF_HAND = 36;

    public static final KitLayout NONE = new KitLayout(Map.of());

    /** kit slot to player slot. */
    private final Map<Integer, Integer> moves;

    private KitLayout(Map<Integer, Integer> moves) {
        this.moves = Map.copyOf(moves);
    }

    /**
     * @param moves kit slot to player slot; moves naming a slot that is not movable,
     *              and a second move to a slot already chosen, are dropped, and so are
     *              moves that leave an item where it was
     */
    public static KitLayout of(Map<Integer, Integer> moves) {
        Map<Integer, Integer> kept = new LinkedHashMap<>();
        boolean[] taken = new boolean[SLOTS];
        for (Map.Entry<Integer, Integer> move : Objects.requireNonNull(moves, "moves").entrySet()) {
            Integer from = move.getKey();
            Integer to = move.getValue();
            if (from == null || to == null || !isMovable(from) || !isMovable(to) || taken[to]) {
                continue;
            }
            taken[to] = true;
            if (!from.equals(to)) {
                kept.put(from, to);
            }
        }
        return kept.isEmpty() ? NONE : new KitLayout(kept);
    }

    /** @return whether a layout may put an item in, or take one out of, this slot */
    public static boolean isMovable(int slot) {
        return (slot >= 0 && slot <= 35) || slot == OFF_HAND;
    }

    public boolean isEmpty() {
        return moves.isEmpty();
    }

    public Map<Integer, Integer> moves() {
        return moves;
    }

    /**
     * Where each filled slot of a kit goes.
     *
     * @param filled which of the kit's {@value #SLOTS} slots hold an item
     * @return for each kit slot, the player slot its item goes to, or {@code -1} for an
     *         empty one; every destination distinct, armour never moved
     */
    public int[] placement(boolean[] filled) {
        if (filled.length != SLOTS) {
            throw new IllegalArgumentException("a kit has " + SLOTS + " slots, got " + filled.length);
        }
        int[] to = new int[SLOTS];
        Arrays.fill(to, -1);
        boolean[] taken = new boolean[SLOTS];
        for (int slot = 36; slot < 40; slot++) {
            if (filled[slot]) {
                to[slot] = slot;
                taken[slot] = true;
            }
        }
        // The layout first, then an item's own slot, then the first free one: in
        // that order, a move always wins over an item that merely stayed put.
        for (int slot = 0; slot < SLOTS; slot++) {
            Integer wanted = moves.get(slot);
            if (filled[slot] && isMovable(slot) && wanted != null && !taken[wanted]) {
                to[slot] = wanted;
                taken[wanted] = true;
            }
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            if (filled[slot] && to[slot] < 0 && !taken[slot]) {
                to[slot] = slot;
                taken[slot] = true;
            }
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            if (filled[slot] && to[slot] < 0) {
                to[slot] = firstFree(taken);
                taken[to[slot]] = true;
            }
        }
        return to;
    }

    private static int firstFree(boolean[] taken) {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (isMovable(slot) && !taken[slot]) {
                return slot;
            }
        }
        // 37 movable slots for at most 37 movable items: unreachable.
        throw new IllegalStateException("no free slot");
    }

    // ------------------------------------------------------------------
    // The editor window
    // ------------------------------------------------------------------

    /**
     * The editor shows the inventory as the player sees it: the three storage rows on
     * top, the hotbar under them, the off hand on the row below.
     *
     * @return the player slot an editor slot stands for, or {@code -1} for one that
     *         stands for none (the bottom row's buttons)
     */
    public static int toPlayerSlot(int editorSlot) {
        if (editorSlot >= 0 && editorSlot < 27) {
            return editorSlot + 9;
        }
        if (editorSlot >= 27 && editorSlot < 36) {
            return editorSlot - 27;
        }
        return editorSlot == EDITOR_OFF_HAND ? OFF_HAND : -1;
    }

    /** @return the editor slot showing a player slot, or {@code -1} for armour */
    public static int toEditorSlot(int playerSlot) {
        if (playerSlot >= 9 && playerSlot <= 35) {
            return playerSlot - 9;
        }
        if (playerSlot >= 0 && playerSlot <= 8) {
            return playerSlot + 27;
        }
        return playerSlot == OFF_HAND ? EDITOR_OFF_HAND : -1;
    }

    // ------------------------------------------------------------------
    // Storage
    // ------------------------------------------------------------------

    /** @return {@code "from:to,from:to"}, the form stored in the database */
    public String format() {
        StringBuilder out = new StringBuilder();
        moves.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(move -> {
            if (!out.isEmpty()) {
                out.append(',');
            }
            out.append(move.getKey()).append(':').append(move.getValue());
        });
        return out.toString();
    }

    /** @return the layout a stored string describes; what cannot be read is skipped, never thrown */
    public static KitLayout parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return NONE;
        }
        Map<Integer, Integer> moves = new LinkedHashMap<>();
        for (String part : stored.split(",")) {
            String[] pair = part.trim().split(":");
            if (pair.length != 2) {
                continue;
            }
            try {
                moves.putIfAbsent(Integer.parseInt(pair[0].trim()), Integer.parseInt(pair[1].trim()));
            } catch (NumberFormatException ignored) {
                // Skipped, as documented.
            }
        }
        return of(moves);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof KitLayout layout && layout.moves.equals(moves);
    }

    @Override
    public int hashCode() {
        return moves.hashCode();
    }

    @Override
    public String toString() {
        return "KitLayout[" + format() + "]";
    }
}
