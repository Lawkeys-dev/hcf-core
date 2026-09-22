package com.lawkeys.hcfcore.events;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped {@code events.yml}: the three new families' sections are present,
 * their examples ship with an empty schedule (see the shipped-events warning in
 * {@code docs/reference/configuration/events.md}), and the {@code --8<--}
 * markers the documentation quotes are actually in the file.
 *
 * <p>Modelled on {@code ui/ScoreboardRowTest}.
 */
class EventsYamlTest {

    private static final Path FILE = Path.of("src/main/resources/events.yml");

    @Test
    @SuppressWarnings("unchecked")
    void dtcSectionShipsWithNoScheduleAndSensibleDefaults() throws IOException {
        Map<String, Object> dtc = section("dtc", "dtc");
        assertEquals(List.of(), dtc.get("schedule"));
        assertEquals("SHARED", dtc.get("counter"));
        assertTrue(((Number) dtc.get("breaks")).intValue() > 0);
        Map<String, Object> core = (Map<String, Object>) dtc.get("core");
        assertEquals("OBSIDIAN", core.get("material"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void lastBreakSectionShipsWithNoScheduleAndNoCounterKey() throws IOException {
        Map<String, Object> lastBreak = section("last-break", "last-break");
        assertEquals(List.of(), lastBreak.get("schedule"));
        assertTrue(!lastBreak.containsKey("counter"), "Last Break has no counter key - it is always common health");
        Map<String, Object> core = (Map<String, Object>) lastBreak.get("core");
        assertEquals("OBSIDIAN", core.get("material"));
    }

    @Test
    void slideSectionShipsWithNoScheduleAndSensibleDefaults() throws IOException {
        Map<String, Object> slide = section("slide", "slide");
        assertEquals(List.of(), slide.get("schedule"));
        assertTrue(((Number) slide.get("points-to-win")).intValue() > 0);
        assertTrue(((Number) slide.get("points-per-player")).intValue() > 0);
    }

    @Test
    void totemsShipAsAFiveBlockTotemAndAThreeBlockMiniTotemScheduledNowhere() throws IOException {
        Map<String, Object> totem = section("totem", "totem");
        Map<String, Object> mini = section("totem", "mini-totem");
        assertEquals(5, ((Number) totem.get("height")).intValue());
        assertEquals(3, ((Number) mini.get("height")).intValue());
        for (Map<String, Object> entry : List.of(totem, mini)) {
            assertEquals(List.of(), entry.get("schedule"));
            assertEquals("QUARTZ_BLOCK", entry.get("material"));
            assertEquals("BEDROCK", entry.get("broken-material"));
            assertEquals("BEDROCK", entry.get("idle-material"));
            assertEquals("RESET", entry.get("rival-break"));
            assertEquals(com.lawkeys.hcfcore.events.totem.TotemSettingsLoader.SWORDS, entry.get("tools"),
                    "every sword, as the loader's own default");
        }
    }

    @Test
    void setupSectionShipsWithPositiveDefaults() throws IOException {
        Map<String, Object> root = load();
        @SuppressWarnings("unchecked")
        Map<String, Object> setup = (Map<String, Object>) root.get("setup");
        assertNotNull(setup, "events.yml needs a setup: section for the /events setup commands");
        assertEquals(Boolean.TRUE, setup.get("auto-claim"));
        assertTrue(((Number) setup.get("claim-margin")).intValue() >= 0);
        assertTrue(((Number) setup.get("target-distance")).intValue() > 0);
        assertTrue(((Number) setup.get("zone-height")).intValue() >= 0);
    }

    /**
     * Every {@code --8<-- [start:x]`/`[end:x]} marker the documentation quotes
     * from {@code events.yml} (via {@code --8<-- "src/main/resources/events.yml:x"})
     * must actually be in the file, or the documentation build fails on a
     * missing snippet - this test catches it without a full mkdocs build.
     */
    @Test
    void everyMarkerTheDocsQuoteIsInTheFile() throws IOException {
        String text = Files.readString(FILE);
        for (String marker : List.of("general", "zone-holograms", "setup", "koth", "citadel", "citadel-claim",
                "ktk", "ktk-penalty", "ktk-kit", "conquest", "conquest-zones", "dtc", "last-break", "slide",
                "totem", "mini-totem")) {
            assertTrue(text.contains("[start:" + marker + "]"), "missing [start:" + marker + "] in events.yml");
            assertTrue(text.contains("[end:" + marker + "]"), "missing [end:" + marker + "] in events.yml");
        }
    }

    /** New ids must not collide, case-insensitively, with any other family's. */
    @Test
    @SuppressWarnings("unchecked")
    void newIdsDoNotCollideWithAnyOtherFamily() throws IOException {
        Map<String, Object> root = load();
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (String family : List.of("events", "citadels", "kill-the-king", "conquest", "dtc", "last-break", "slide", "totem")) {
            Map<String, Object> section = (Map<String, Object>) root.get(family);
            if (section == null) {
                continue;
            }
            for (String id : section.keySet()) {
                assertTrue(ids.add(id.toLowerCase(java.util.Locale.ROOT)),
                        "id '" + id + "' collides with another event, case-insensitively");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(String family, String id) throws IOException {
        Map<String, Object> root = load();
        Map<String, Object> familySection = (Map<String, Object>) root.get(family);
        assertNotNull(familySection, "events.yml needs a " + family + ": section");
        Map<String, Object> entry = (Map<String, Object>) familySection.get(id);
        assertNotNull(entry, "events.yml's " + family + ": section needs an example called '" + id + "'");
        return entry;
    }

    private static Map<String, Object> load() throws IOException {
        try (Reader reader = Files.newBufferedReader(FILE)) {
            return new Yaml().load(reader);
        }
    }
}
