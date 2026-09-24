package com.lawkeys.hcfcore.ui.tab;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The HCF grid's geometry. Pure.
 *
 * <p>The game lays the tab list out by itself: with {@value #SIZE} entries it draws
 * {@value #COLUMNS} columns of {@value #ROWS}, filling each column top to bottom
 * before the next. So the grid is {@value #SIZE} made-up entries - one per cell,
 * each with a fixed identity - ordered by their list order, highest first, and the
 * real players are taken off the list for as long as the grid shows.
 */
public final class TabGrid {

    public static final int COLUMNS = 4;
    public static final int ROWS = 20;
    public static final int SIZE = COLUMNS * ROWS;

    /**
     * How many default skins the game picks from, and which one every cell gets. A
     * head-less entry shows the default skin its identity falls on -
     * {@code floorMod(uuid.hashCode(), 18)} - so random identities made a patchwork
     * of nine faces; these are chosen to all fall on the same one.
     */
    static final int DEFAULT_SKINS = 18;
    static final int DEFAULT_SKIN = 15;

    private static final List<UUID> IDS = IntStream.range(0, SIZE).mapToObj(TabGrid::uniformId).toList();
    private static final Set<UUID> ID_SET = Set.copyOf(IDS);

    private TabGrid() {
    }

    /**
     * @param columns the templates of each column, top to bottom; a short column is
     *                padded with blank cells, a long one cut at {@value #ROWS}, a
     *                missing one blank
     * @return the {@value #SIZE} cells in the order the game draws them
     */
    public static List<String> cells(List<List<String>> columns) {
        List<String> cells = new ArrayList<>(SIZE);
        for (int column = 0; column < COLUMNS; column++) {
            List<String> rows = column < columns.size() ? columns.get(column) : List.of();
            for (int row = 0; row < ROWS; row++) {
                cells.add(row < rows.size() && rows.get(row) != null ? rows.get(row) : "");
            }
        }
        return cells;
    }

    private static UUID uniformId(int cell) {
        for (int attempt = 0; ; attempt++) {
            UUID id = UUID.nameUUIDFromBytes(("hcfcore-tab-" + cell + "-" + attempt).getBytes(StandardCharsets.UTF_8));
            if (Math.floorMod(id.hashCode(), DEFAULT_SKINS) == DEFAULT_SKIN) {
                return id;
            }
        }
    }

    /** @return the identity of a cell - the same on every server, every time */
    public static UUID id(int cell) {
        return IDS.get(cell);
    }

    /** @return whether an entry is one of the grid's cells rather than a player */
    public static boolean isCell(UUID id) {
        return ID_SET.contains(id);
    }

    /**
     * @return a cell's profile name: empty. Never shown - every cell has a display
     *         name - but the game completes names in the chat from the list's
     *         profiles, and offered {@code !tab00}... to a player pressing Tab (found
     *         in game, 24/09/2026). An empty name is never offered: with nothing typed
     *         the suggestion equals the text and is dropped, and once a letter is typed
     *         an empty name no longer matches
     */
    public static String name(int cell) {
        return "";
    }

    /** @return a cell's list order: the game puts the highest first */
    public static int listOrder(int cell) {
        return SIZE - cell;
    }

    /** @return the cells whose text differs, so only those are sent again */
    public static List<Integer> changed(List<String> before, List<String> after) {
        return IntStream.range(0, after.size())
                .filter(i -> before == null || i >= before.size() || !before.get(i).equals(after.get(i)))
                .boxed()
                .collect(Collectors.toList());
    }
}
