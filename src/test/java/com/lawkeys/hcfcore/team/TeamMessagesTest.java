package com.lawkeys.hcfcore.team;

import com.lawkeys.hcfcore.ability.AbilityMessages;
import com.lawkeys.hcfcore.claim.ClaimMessages;
import com.lawkeys.hcfcore.crowbar.CrowbarMessages;
import com.lawkeys.hcfcore.dtr.DtrMessages;
import com.lawkeys.hcfcore.effectcommand.EffectCommandMessages;
import com.lawkeys.hcfcore.limiter.LimiterMessages;
import com.lawkeys.hcfcore.economy.EconomyMessages;
import com.lawkeys.hcfcore.enchant.EnchantMessages;
import com.lawkeys.hcfcore.events.EventMessages;
import com.lawkeys.hcfcore.events.conquest.ConquestMessages;
import com.lawkeys.hcfcore.events.king.KingMessages;
import com.lawkeys.hcfcore.general.GeneralMessages;
import com.lawkeys.hcfcore.hologram.HologramMessages;
import com.lawkeys.hcfcore.kit.KitMessages;
import com.lawkeys.hcfcore.lives.LivesMessages;
import com.lawkeys.hcfcore.phase.PhaseMessages;
import com.lawkeys.hcfcore.pvp.PvpMessages;
import com.lawkeys.hcfcore.pvpclass.ClassMessages;
import com.lawkeys.hcfcore.redeem.RedeemMessages;
import com.lawkeys.hcfcore.resourcenode.ResourceNodeMessages;
import com.lawkeys.hcfcore.schedule.ScheduleMessages;
import com.lawkeys.hcfcore.settings.SettingsMessages;
import com.lawkeys.hcfcore.staff.StaffMessages;
import com.lawkeys.hcfcore.startup.StartupMessages;
import com.lawkeys.hcfcore.stats.StatsMessages;
import com.lawkeys.hcfcore.ui.UiMessages;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the contract between the code and the shipped language file: every key
 * a module's {@code *Messages} class declares must exist in {@code lang/en.yml}.
 * {@link #everyMessagesClassIsChecked} fails when a new one is not listed.
 *
 * <p>Without this, a missing translation only shows up in-game as a raw key -
 * exactly the kind of silent breakage the "no hardcoded text" rule
 * (ARCHITECTURE.md section 10) is meant to avoid.
 */
class TeamMessagesTest {

    /** Every class of language-key constants. */
    private static final List<Class<?>> MESSAGE_CLASSES = List.of(
            AbilityMessages.class,
            ClaimMessages.class,
            ClassMessages.class,
            ConquestMessages.class,
            CrowbarMessages.class,
            DtrMessages.class,
            EconomyMessages.class,
            EffectCommandMessages.class,
            EnchantMessages.class,
            EventMessages.class,
            GeneralMessages.class,
            HologramMessages.class,
            KingMessages.class,
            KitMessages.class,
            LimiterMessages.class,
            LivesMessages.class,
            PhaseMessages.class,
            PvpMessages.class,
            RedeemMessages.class,
            ResourceNodeMessages.class,
            ScheduleMessages.class,
            SettingsMessages.class,
            StaffMessages.class,
            StartupMessages.class,
            StatsMessages.class,
            TeamMessages.class,
            UiMessages.class);

    @Test
    void everyMessageKeyUsedInCodeExistsInTheEnglishLanguageFile() throws Exception {
        Set<String> available = readKeys("/lang/en.yml");
        assertTrue(available.contains("team.create.success"), "sanity check: the reader found team keys");

        List<String> missing = new ArrayList<>();
        for (Class<?> holder : MESSAGE_CLASSES) {
            missing.addAll(missingKeys(holder, available));
        }
        assertTrue(missing.isEmpty(), "keys used in code but missing from lang/en.yml: " + missing);
    }

    /** @return the constants of {@code holder} whose key is absent from the language file */
    private static List<String> missingKeys(Class<?> holder, Set<String> available)
            throws IllegalAccessException {
        List<String> missing = new ArrayList<>();
        for (Field field : holder.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                continue;
            }
            String key = (String) field.get(null);
            if (!available.contains(key)) {
                missing.add(holder.getSimpleName() + '.' + field.getName() + " -> " + key);
            }
        }
        return missing;
    }

    /**
     * A new module's messages class is only checked once it is listed above: three
     * modules' once were not, and nothing said so.
     */
    @Test
    void everyMessagesClassIsChecked() throws IOException {
        Set<String> listed = new java.util.HashSet<>();
        MESSAGE_CLASSES.forEach(holder -> listed.add(holder.getName()));
        Path sources = Path.of("src/main/java");
        List<String> unlisted;
        try (java.util.stream.Stream<Path> files = Files.walk(sources)) {
            unlisted = files
                    .filter(file -> file.getFileName().toString().endsWith("Messages.java"))
                    .map(file -> sources.relativize(file).toString().replace(java.io.File.separatorChar, '.'))
                    .map(name -> name.substring(0, name.length() - ".java".length()))
                    // The private-message manager, not a class of language keys.
                    .filter(name -> !name.equals("com.lawkeys.hcfcore.general.PrivateMessages"))
                    .filter(name -> !listed.contains(name))
                    .sorted()
                    .toList();
        }
        assertTrue(unlisted.isEmpty(), "add these to MESSAGE_CLASSES: " + unlisted);
    }

    /**
     * The minimal reader below takes every key literally, but Bukkit's YAML 1.1
     * parser reads a key {@code on}, {@code off}, {@code yes}, {@code no},
     * {@code true} or {@code false} as a boolean: a message under
     * {@code effect-commands.on} was looked up as a string and never found, in
     * game (18/09/2026). No shipped file may use one as a key.
     */
    @Test
    void noShippedKeyIsReadAsABoolean() throws IOException {
        java.util.regex.Pattern booleanKey = java.util.regex.Pattern.compile(
                "^\\s*(yes|no|true|false|on|off)\\s*:", java.util.regex.Pattern.CASE_INSENSITIVE);
        Path resources = Path.of("src/main/resources");
        List<String> found = new ArrayList<>();
        try (java.util.stream.Stream<Path> files = Files.walk(resources)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".yml")).toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    if (booleanKey.matcher(lines.get(i)).find()) {
                        found.add(resources.relativize(file) + ":" + (i + 1) + " " + lines.get(i).trim());
                    }
                }
            }
        }
        assertTrue(found.isEmpty(), "keys YAML 1.1 reads as booleans: " + found);
    }

    @Test
    void languageFileIsReachableOnTheClasspath() {
        assertNotNull(TeamMessagesTest.class.getResourceAsStream("/lang/en.yml"),
                "lang/en.yml must ship inside the plugin jar");
    }

    /**
     * Collects the dotted paths of every scalar entry in a YAML file.
     *
     * <p>A deliberately minimal, indentation-based reader rather than a YAML
     * dependency: the language file is a plain nested map of quoted strings, and
     * this check only needs the set of paths. It intentionally ignores comments
     * and blank lines, and treats a line ending in ':' as a section.
     */
    private static Set<String> readKeys(String resource) throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        Deque<String> path = new ArrayDeque<>();
        Deque<Integer> indents = new ArrayDeque<>();

        try (InputStream in = TeamMessagesTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "missing resource " + resource);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String stripped = line.strip();
                    if (stripped.isEmpty() || stripped.startsWith("#") || stripped.startsWith("-")) {
                        continue;
                    }
                    int indent = line.length() - line.stripLeading().length();
                    while (!indents.isEmpty() && indents.peek() >= indent) {
                        indents.pop();
                        path.pop();
                    }
                    int colon = stripped.indexOf(':');
                    if (colon < 0) {
                        continue;
                    }
                    String name = stripped.substring(0, colon).strip();
                    boolean isSection = stripped.substring(colon + 1).strip().isEmpty();

                    if (isSection) {
                        path.push(name);
                        indents.push(indent);
                    } else {
                        List<String> parts = new ArrayList<>(path);
                        java.util.Collections.reverse(parts);
                        parts.add(name);
                        keys.add(String.join(".", parts));
                    }
                }
            }
        }
        return keys;
    }
}
