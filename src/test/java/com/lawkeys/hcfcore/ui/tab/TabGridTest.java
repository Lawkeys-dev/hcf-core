package com.lawkeys.hcfcore.ui.tab;

import com.lawkeys.hcfcore.mode.GameMode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabGridTest {

    @Test
    void theCellsRunDownEachColumnBeforeTheNext() {
        List<String> cells = TabGrid.cells(List.of(List.of("a1", "a2"), List.of("b1"), List.of(), List.of("d1")));
        assertEquals(80, cells.size());
        assertEquals("a1", cells.get(0));
        assertEquals("a2", cells.get(1));
        assertEquals("", cells.get(2), "a short column is padded");
        assertEquals("b1", cells.get(20));
        assertEquals("d1", cells.get(60));
    }

    @Test
    void aLongColumnIsCutAndAMissingOneIsBlank() {
        List<String> longColumn = IntStream.range(0, 25).mapToObj(i -> "r" + i).toList();
        List<String> cells = TabGrid.cells(List.of(longColumn));
        assertEquals("r19", cells.get(19));
        assertEquals("", cells.get(20), "row 21 never spills into the next column");
        assertEquals("", cells.get(79));
    }

    @Test
    void theFirstCellIsListedFirst() {
        assertTrue(TabGrid.listOrder(0) > TabGrid.listOrder(1));
        assertTrue(TabGrid.listOrder(79) > 0);
    }

    @Test
    void everyCellHasItsOwnFixedIdentity() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < TabGrid.SIZE; i++) {
            ids.add(TabGrid.id(i));
            assertTrue(TabGrid.name(i).length() <= 16, "a profile name is sixteen characters at most");
            assertTrue(TabGrid.isCell(TabGrid.id(i)));
        }
        assertEquals(TabGrid.SIZE, ids.size());
        assertEquals(TabGrid.id(7), TabGrid.id(7));
        assertFalse(TabGrid.isCell(UUID.randomUUID()), "a player is never taken for a cell");
    }

    @Test
    void onlyChangedCellsAreSentAgain() {
        assertEquals(List.of(1), TabGrid.changed(List.of("a", "b", "c"), List.of("a", "x", "c")));
        assertEquals(List.of(0, 1), TabGrid.changed(null, List.of("a", "b")));
    }

    @Test
    void autoIsTheGridOnHcfAndTheListOnAKitmap() {
        assertEquals(TabStyle.HCF, TabStyle.AUTO.resolve(GameMode.HCF));
        assertEquals(TabStyle.CLASSIC, TabStyle.AUTO.resolve(GameMode.KITMAP));
        assertEquals(TabStyle.HCF, TabStyle.HCF.resolve(GameMode.KITMAP), "a chosen style is kept");
        assertEquals(TabStyle.CLASSIC, TabStyle.parse(" Classic ").orElseThrow());
        assertTrue(TabStyle.parse("grid").isEmpty());
        assertEquals(TabSort.KILLS, TabSort.parse("kills").orElseThrow());
    }
}
