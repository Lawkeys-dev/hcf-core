package com.lawkeys.hcfcore.theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Where a menu's items go, inside the theme's frame ({@code theme.yml}, {@code menus}):
 * how many rows, which slots hold panes, which the items - centred row by row, spread
 * out when a few fit on one row - and, when they do not all fit, the page's arrows.
 * Pure: the menus fill it in.
 *
 * @param rows        the menu's rows, 1 to 6
 * @param itemSlots   the slots of this page's items, in order
 * @param frameSlots  the slots holding the frame's pane
 * @param cornerSlots the slots holding the corners' pane (a part of the frame)
 * @param page        this page, from 0
 * @param pages       how many there are, 1 or more
 * @param firstItem   the index, in the whole list, of this page's first item
 * @param previous    the previous page's arrow, or {@code -1}
 * @param next        the next page's arrow, or {@code -1}
 */
public record MenuLayout(int rows, List<Integer> itemSlots, Set<Integer> frameSlots, Set<Integer> cornerSlots,
                         int page, int pages, int firstItem, int previous, int next) {

    private static final int COLUMNS = 9;
    private static final int MAX_ROWS = 6;

    public MenuLayout {
        itemSlots = List.copyOf(itemSlots);
        frameSlots = Set.copyOf(frameSlots);
        cornerSlots = Set.copyOf(cornerSlots);
    }

    /** @return the size of the inventory */
    public int size() {
        return rows * COLUMNS;
    }

    /**
     * @param count how many items there are in all
     * @param frame {@code full}, {@code bars} or {@code none}
     * @param page  the page wanted, from 0; out of range, the nearest one
     */
    public static MenuLayout of(int count, String frame, int page) {
        count = Math.max(0, count);
        boolean full = "full".equals(frame);
        boolean bars = full || "bars".equals(frame);
        int width = full ? COLUMNS - 2 : COLUMNS;
        int framedRows = bars ? 2 : 0;
        int innerMax = MAX_ROWS - framedRows;
        int capacity = innerMax * width;
        boolean paged = count > capacity;
        if (paged && !bars) {
            // No frame to hold the arrows: the last row is theirs.
            innerMax -= 1;
            capacity = innerMax * width;
        }
        int pages = paged ? (count + capacity - 1) / capacity : 1;
        page = Math.max(0, Math.min(page, pages - 1));
        int first = page * capacity;
        int onPage = Math.min(capacity, count - first);
        int innerRows = paged ? innerMax : Math.max(1, (onPage + width - 1) / width);
        int rows = innerRows + framedRows + (paged && !bars ? 1 : 0);

        Set<Integer> frameSlots = new TreeSet<>();
        Set<Integer> corners = new TreeSet<>();
        for (int slot = 0; slot < rows * COLUMNS; slot++) {
            int row = slot / COLUMNS;
            int column = slot % COLUMNS;
            boolean edgeRow = row == 0 || row == rows - 1;
            boolean edgeColumn = column == 0 || column == COLUMNS - 1;
            if (bars && (edgeRow || (full && edgeColumn))) {
                frameSlots.add(slot);
                if (edgeRow && edgeColumn) {
                    corners.add(slot);
                }
            }
        }

        int top = bars ? 1 : 0;
        int left = full ? 1 : 0;
        List<Integer> slots = new ArrayList<>();
        int placed = 0;
        for (int row = 0; row < innerRows && placed < onPage; row++) {
            int inRow = Math.min(width, onPage - placed);
            // A few on a single row are spread out, one cell apart; otherwise centred side by side.
            boolean spread = innerRows == 1 && inRow * 2 - 1 <= width;
            int step = spread ? 2 : 1;
            int span = (inRow - 1) * step + 1;
            int start = (width - span) / 2;
            for (int k = 0; k < inRow; k++) {
                slots.add((top + row) * COLUMNS + left + start + k * step);
            }
            placed += inRow;
        }

        int previous = -1;
        int next = -1;
        if (paged) {
            int lastRow = (rows - 1) * COLUMNS;
            previous = page > 0 ? lastRow + 3 : -1;
            next = page < pages - 1 ? lastRow + 5 : -1;
        }
        return new MenuLayout(rows, slots, frameSlots, corners, page, pages, first, previous, next);
    }
}
