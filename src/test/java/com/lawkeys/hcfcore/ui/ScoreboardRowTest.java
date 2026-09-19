package com.lawkeys.hcfcore.ui;

import com.lawkeys.hcfcore.settings.PlayerSetting;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A scoreboard row's section tag, which a player may hide in /settings. */
class ScoreboardRowTest {

    @Test
    void aTaggedRowBelongsToItsSection() {
        ScoreboardRow row = ScoreboardRow.parse("[Team]&cTeam: &f%team%");
        assertEquals("team", row.section(), "lower case");
        assertEquals("&cTeam: &f%team%", row.text(), "the tag left out");
    }

    @Test
    void anUntaggedRowBelongsToNone() {
        ScoreboardRow row = ScoreboardRow.parse("&7&m----------------");
        assertNull(row.section());
        assertEquals("&7&m----------------", row.text());
        assertNull(ScoreboardRow.parse("&c[not a tag] here").section(), "only a tag at the start counts");
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyTagOfTheShippedBoardHasASetting() throws IOException {
        Map<String, Object> root;
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources/ui.yml"))) {
            root = new Yaml().load(reader);
        }
        List<String> lines = (List<String>) ((Map<String, Object>) root.get("scoreboard")).get("lines");
        for (String line : lines) {
            String section = ScoreboardRow.parse(line).section();
            if (section != null) {
                assertTrue(PlayerSetting.scoreboardSection(section).isPresent(), "[" + section + "] has a setting");
            }
        }
        for (String line : UiSettings.defaults().scoreboard().lines()) {
            String section = ScoreboardRow.parse(line).section();
            if (section != null) {
                assertTrue(PlayerSetting.scoreboardSection(section).isPresent(), "[" + section + "] has a setting");
            }
        }
    }
}
