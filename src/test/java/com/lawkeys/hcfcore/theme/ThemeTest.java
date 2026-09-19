package com.lawkeys.hcfcore.theme;

import com.lawkeys.hcfcore.util.ColorCodes;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** The theme's tokens, hex colours, and the shipped files that use them. */
class ThemeTest {

    private final Theme theme = Theme.defaults();

    @Test
    void aColourTokenBecomesItsHexCode() {
        assertEquals("&#F5B32EHCF &#B3A88Fdone", theme.apply("{primary}HCF {muted}done"));
    }

    @Test
    void thePrefixAndBulletAreFilledInAndMayUseColours() {
        assertEquals("&#F5B32E&lHCF&r&#5E5540 » &#F7F1E3Hi ➥", theme.apply("{prefix}{text}Hi {bullet}"));
    }

    @Test
    void theActionTokenStartsAClickLine() {
        assertEquals("&#FFE39A» Click", theme.apply("{secondary}{action}Click"));
    }

    @Test
    void anUnknownTokenIsLeftAsWritten() {
        assertEquals("a {brace} here", theme.apply("a {brace} here"));
        assertEquals("no tokens", theme.apply("no tokens"));
    }

    @Test
    void aHexCodeBecomesTheGamesSixPairs() {
        assertEquals("§x§f§5§b§3§2§eHCF", ColorCodes.translate("&#F5B32EHCF"));
        assertEquals("&#12G456", ColorCodes.translate("&#12G456"), "not six hex digits: left alone");
        assertEquals("HCF", ColorCodes.strip(ColorCodes.translate("&#F5B32E&lHCF")));
    }

    @Test
    void aTitleIsInSmallCapitalsLeavingCodesTokensAndPlaceholdersAlone() {
        assertEquals("{primary}&lᴀʙɪʟɪᴛɪᴇꜱ %player%", theme.title("{primary}&lAbilities %player%"));
        assertEquals("&#F5B32Eᴛᴇᴀᴍ", theme.title("&#F5B32ETeam"), "a hex code keeps its letters");
        Theme plain = new Theme(theme.colors(), theme.prefix(), theme.bullet(), false, theme.menus());
        assertEquals("Abilities", plain.title("Abilities"));
    }

    @Test
    void aHexColourIsRecognised() {
        assertTrue(Theme.isHex("#F5B32E"));
        assertTrue(Theme.isHex("f5b32e"));
        assertFalse(Theme.isHex("gold"));
    }

    /** Every {token} in the shipped texts is one the theme knows: a typo would print as it is. */
    @Test
    void everyTokenOfTheShippedFilesIsKnown() throws IOException {
        Pattern token = Pattern.compile("\\{([a-z]+)}");
        java.util.Set<String> known = new java.util.HashSet<>(Theme.ROLES);
        known.add("prefix");
        known.add("bullet");
        known.add("action");
        try (var files = Files.list(Path.of("src/main/resources"))) {
            // plugin.yml is the server's, not text the plugin shows: its ${version} is Gradle's.
            for (Path file : files.filter(p -> p.toString().endsWith(".yml")
                    && !p.getFileName().toString().equals("plugin.yml")).toList()) {
                check(file, token, known);
            }
        }
        check(Path.of("src/main/resources/lang/en.yml"), token, known);
    }

    private static void check(Path file, Pattern token, java.util.Set<String> known) throws IOException {
        Matcher m = token.matcher(Files.readString(file));
        while (m.find()) {
            if (!known.contains(m.group(1))) {
                fail(file.getFileName() + ": unknown token {" + m.group(1) + "}");
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void theShippedThemeIsTheDefault() throws IOException {
        Map<String, Object> root;
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources/theme.yml"))) {
            root = new Yaml().load(reader);
        }
        Map<String, Object> colors = (Map<String, Object>) root.get("colors");
        for (String role : Theme.ROLES) {
            assertEquals(theme.colors().get(role), String.valueOf(colors.get(role)), role);
        }
        assertEquals(theme.prefix(), root.get("prefix"));
        assertEquals(theme.bullet(), root.get("bullet"));
    }
}
