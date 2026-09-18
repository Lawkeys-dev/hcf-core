package com.lawkeys.hcfcore.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** "duration-seconds: abc" ran with 30 seconds and nobody was told (13/09/2026). */
class ConfigTypeCheckTest {

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    void aWordWhereANumberIsExpectedIsReported() {
        List<String> lines = ConfigTypeCheck.mismatches(
                map("combat-tag.duration-seconds", 30), map("combat-tag.duration-seconds", "abc"));
        assertEquals(List.of("'combat-tag.duration-seconds' should be a number, not 'abc'"
                + " - it is ignored, the default applies."), lines);
    }

    /** Text to YAML: Bukkit's getLong ignores it like a word, a few loaders parse it. */
    @Test
    void aQuotedNumberIsReportedWithoutPromisingItIsIgnored() {
        assertEquals(List.of("'seconds' is a number written as text ('10')"
                        + " - write it without quotes, or it may be ignored."),
                ConfigTypeCheck.mismatches(map("seconds", 10), map("seconds", "10")));
    }

    @Test
    void aWordWhereTrueOrFalseIsExpectedIsReported() {
        List<String> lines = ConfigTypeCheck.mismatches(map("enabled", true), map("enabled", "maybe"));
        assertEquals(List.of("'enabled' should be true or false, not 'maybe'"
                + " - it is ignored, the default applies."), lines);
    }

    /** 1.5 where 1 was written, or 4 where 4.0 was: both numbers, both read fine. */
    @Test
    void anyNumberFitsANumber() {
        assertTrue(ConfigTypeCheck.mismatches(
                map("a", 1, "b", 4.0), map("a", 1.5, "b", 4)).isEmpty());
    }

    /** The server's own entries and the settings it removed have nothing to be compared with. */
    @Test
    void onlyTheBundledSettingsPresentOnDiskAreChecked() {
        assertTrue(ConfigTypeCheck.mismatches(
                map("kept", 1, "removed", 2), map("kept", 3, "kits.pvp.cooldown", "soon")).isEmpty());
    }

    /** Text and lists are the loaders' business: nothing generic can say what is valid. */
    @Test
    void textAndListsAreLeftToTheirLoader() {
        assertTrue(ConfigTypeCheck.mismatches(
                map("title", "&cHCF", "times", List.of("18:00")), map("title", 12, "times", "18:00")).isEmpty());
    }
}
