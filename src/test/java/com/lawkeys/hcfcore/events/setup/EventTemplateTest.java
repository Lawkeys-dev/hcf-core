package com.lawkeys.hcfcore.events.setup;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /events create}: the shipped example, moved to where staff stand. */
class EventTemplateTest {

    private static Map<String, Object> root;

    @BeforeAll
    static void load() throws IOException {
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources/events.yml"))) {
            root = new Yaml().load(reader);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> template(EventKind kind) {
        return (Map<String, Object>) ((Map<String, Object>) root.get(kind.section())).get(kind.template());
    }

    @SuppressWarnings("unchecked")
    private static int at(Map<String, Object> entry, String key, String axis) {
        Map<String, Object> position = entry;
        for (String part : key.split("\\.")) {
            position = (Map<String, Object>) position.get(part);
        }
        return ((Number) position.get(axis)).intValue();
    }

    @Test
    void aKothKeepsItsSizeWithTheMiddleOfItsFloorWhereStaffStand() {
        Map<String, Object> placed = EventTemplate.place(template(EventKind.KOTH), "koth2", "hcf", 1000, -60, 2000);
        // Shipped: 100..115 by 100..115, heights 60..90.
        assertEquals(1000 - 7, at(placed, "corner-1", "x"));
        assertEquals(1000 + 8, at(placed, "corner-2", "x"));
        assertEquals(2000 - 7, at(placed, "corner-1", "z"));
        assertEquals(-60, at(placed, "corner-1", "y"));
        assertEquals(-30, at(placed, "corner-2", "y"));
        assertEquals("hcf", placed.get("world"));
        assertEquals("{primary}koth2", placed.get("display-name"));
    }

    @Test
    void aDtcCoreLandsWhereStaffStandAndItsZoneFollows() {
        Map<String, Object> placed = EventTemplate.place(template(EventKind.DTC), "dtc2", "world", 0, 64, 0);
        assertEquals(0, at(placed, "core", "x"));
        assertEquals(64, at(placed, "core", "y"));
        assertEquals(0, at(placed, "core", "z"));
        // Shipped: core 410,65,410 in 400..420, heights 60..80.
        assertEquals(-10, at(placed, "corner-1", "x"));
        assertEquals(59, at(placed, "corner-1", "y"));
        assertEquals(10, at(placed, "corner-2", "z"));
        assertEquals("END_STONE", ((Map<?, ?>) placed.get("core")).get("material"));
        assertEquals("BEDROCK", ((Map<?, ?>) placed.get("core")).get("idle-material"));
    }

    @Test
    void aMiniTotemColumnStandsWhereStaffStand() {
        Map<String, Object> placed = EventTemplate.place(template(EventKind.MINI_TOTEM), "mini", "world", 5, 70, 5);
        assertEquals(5, at(placed, "base", "x"));
        assertEquals(70, at(placed, "base", "y"));
        assertEquals(3, ((Number) placed.get("height")).intValue());
        assertEquals("{warning}mini", placed.get("display-name"));
    }

    @Test
    void aConquestKeepsTheLayoutOfItsZones() {
        Map<String, Object> placed = EventTemplate.place(template(EventKind.CONQUEST), "cq", "world", 0, 0, 0);
        // Shipped: four 7x7 zones between 300 and 346, floor at 60.
        assertEquals(-23, at(placed, "zones.red.corner-1", "x"));
        assertEquals(23, at(placed, "zones.yellow.corner-2", "x"));
        assertEquals(0, at(placed, "zones.blue.corner-1", "y"));
        assertArrayEquals(new int[]{-23, -23, 23, 23}, EventTemplate.bounds(placed).orElseThrow());
    }

    @Test
    void killTheKingOnlyChangesWorld() {
        Map<String, Object> placed = EventTemplate.place(template(EventKind.KING), "king", "hcf", 5, 5, 5);
        assertEquals("hcf", placed.get("world"));
        assertTrue(EventTemplate.bounds(placed).isEmpty());
        assertEquals(template(EventKind.KING).get("kit"), placed.get("kit"));
        assertNotSame(template(EventKind.KING).get("kit"), placed.get("kit"));
    }

    @Test
    void aNewEventNeverOpensByItselfAndTheTemplateIsUntouched() {
        Map<String, Object> koth = template(EventKind.KOTH);
        int before = at(koth, "corner-1", "x");
        Map<String, Object> placed = EventTemplate.place(koth, "k", "world", 9999, 0, 9999);
        assertEquals(List.of(), placed.get("schedule"));
        assertEquals(before, at(koth, "corner-1", "x"));
    }
}
