package com.lawkeys.hcfcore.team;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The shipped {@code teams.yml} and the built-in defaults say the same thing - the
 * defaults are what a server gets for a key its file lacks.
 */
class TeamsYamlTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> file() throws IOException {
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources/teams.yml"))) {
            return new Yaml().load(reader);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void theShortcutsShippedAreTheDefaults() throws IOException {
        Map<String, Object> shortcuts = (Map<String, Object>) file().get("shortcuts");
        TeamSettings.Shortcuts defaults = TeamSettings.Shortcuts.defaults();
        assertEquals(defaults.enabled(), shortcuts.get("enabled"));
        assertEquals(defaults.subcommands(), shortcuts.get("subcommands"));
        assertEquals(defaults.commands(), shortcuts.get("commands"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theTeamSettingsShippedAreTheDefaults() throws IOException {
        Map<String, Object> custom = (Map<String, Object>) file().get("team-settings");
        TeamSettings.CustomRules defaults = TeamSettings.CustomRules.defaults();
        assertEquals(defaults.enabled(), custom.get("enabled"));
        Set<JoinMode> modes = new HashSet<>();
        ((List<String>) custom.get("join-modes")).forEach(mode -> modes.add(JoinMode.fromId(mode).orElseThrow()));
        assertEquals(defaults.joinModes(), modes);
        assertEquals(defaults.defaultJoinMode(), JoinMode.fromId((String) custom.get("default-join-mode")).orElseThrow());
        assertEquals(defaults.descriptionLength(), ((Map<String, Object>) custom.get("description")).get("max-length"));
        assertEquals(defaults.discordPattern().pattern(), ((Map<String, Object>) custom.get("discord")).get("pattern"));
        assertEquals(defaults.locked(), new HashSet<>((List<String>) custom.get("locked")));
        assertEquals(0, ((Number) file().get("max-officers")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyActionHasItsShippedRole() throws IOException {
        Map<String, Object> roles = (Map<String, Object>) file().get("required-roles");
        Set<String> keys = new HashSet<>();
        for (TeamAction action : TeamAction.values()) {
            keys.add(action.configKey());
            assertEquals(TeamSettings.defaults().requiredRole(action),
                    TeamRole.fromId((String) roles.get(action.configKey())).orElseThrow(), action.configKey());
        }
        assertEquals(keys, roles.keySet());
    }
}
