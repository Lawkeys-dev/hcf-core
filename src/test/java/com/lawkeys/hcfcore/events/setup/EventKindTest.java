package com.lawkeys.hcfcore.events.setup;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventKindTest {

    @Test
    void everyTypeNameIsFoundCaseInsensitively() {
        for (EventKind kind : EventKind.values()) {
            assertEquals(Optional.of(kind), EventKind.fromTypeName(kind.typeName().toUpperCase()));
        }
        assertEquals(Optional.empty(), EventKind.fromTypeName("koht"));
        assertEquals(Optional.empty(), EventKind.fromTypeName(null));
    }

    @Test
    void theTotemSectionReadsAsATotemAndIsListedOnce() {
        assertEquals(Optional.of(EventKind.TOTEM), EventKind.fromSection("totem"));
        assertEquals(1, EventKind.sections().stream().filter("totem"::equals).count());
        assertEquals(8, EventKind.sections().size());
    }

    @Test
    void onlyKillTheKingHasNoTerritoryAndNoZone() {
        for (EventKind kind : EventKind.values()) {
            assertEquals(kind != EventKind.KING, kind.hasTerritory(), kind.name());
        }
        assertEquals(EventKind.Zone.MANY, EventKind.CONQUEST.zone());
    }

    @Test
    void onlyTheCoresAndTotemsHaveABlock() {
        assertEquals(List.of(EventKind.DTC, EventKind.LAST_BREAK, EventKind.TOTEM, EventKind.MINI_TOTEM),
                java.util.Arrays.stream(EventKind.values()).filter(EventKind::hasBlock).toList());
    }

    /** {@code /events create} copies the shipped example: every kind must have one. */
    @Test
    @SuppressWarnings("unchecked")
    void everyKindHasItsTemplateInTheShippedFile() throws IOException {
        Map<String, Object> root;
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources/events.yml"))) {
            root = new Yaml().load(reader);
        }
        for (EventKind kind : EventKind.values()) {
            Map<String, Object> section = (Map<String, Object>) root.get(kind.section());
            assertInstanceOf(Map.class, section, kind.section());
            assertTrue(section.containsKey(kind.template()), kind.section() + "." + kind.template());
        }
        assertFalse(root.containsKey("setup") && ((Map<String, Object>) root.get("setup")).containsKey("default-zone-radius"));
    }
}
