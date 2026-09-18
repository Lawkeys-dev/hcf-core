package com.lawkeys.hcfcore.config;

import com.lawkeys.hcfcore.general.BasicCommands;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped YAML files, read by the parser the server uses. A file that does not
 * parse starts the plugin with its defaults, or not at all for plugin.yml - and the
 * build would not say so: a description with ": " in it once broke plugin.yml.
 */
class ShippedFilesTest {

    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void everyShippedFileParses() throws IOException {
        List<String> broken = new ArrayList<>();
        List<Path> files;
        try (var walk = Files.walk(RESOURCES)) {
            files = walk.filter(file -> file.toString().endsWith(".yml")).toList();
        }
        for (Path file : files) {
            try (Reader reader = Files.newBufferedReader(file)) {
                new Yaml().load(reader);
            } catch (RuntimeException invalid) {
                broken.add(RESOURCES.relativize(file) + ": " + invalid.getMessage().lines().findFirst().orElse(""));
            }
        }
        assertTrue(files.size() > 20, "sanity check: the shipped files were found");
        assertTrue(broken.isEmpty(), "files that do not parse: " + broken);
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyEverydayCommandIsDeclared() throws IOException {
        Map<String, Object> plugin;
        try (Reader reader = Files.newBufferedReader(RESOURCES.resolve("plugin.yml"))) {
            plugin = new Yaml().load(reader);
        }
        Map<String, Object> commands = (Map<String, Object>) plugin.get("commands");
        List<String> missing = BasicCommands.NAMES.stream().filter(name -> !commands.containsKey(name)).toList();
        assertTrue(missing.isEmpty(), "commands missing from plugin.yml: " + missing);
    }
}
