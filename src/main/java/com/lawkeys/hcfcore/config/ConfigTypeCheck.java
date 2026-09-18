package com.lawkeys.hcfcore.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Finds settings holding a value of the wrong type: a word where a number, or
 * true/false, is expected.
 *
 * <p>Bukkit's typed getters ({@code getLong}, {@code getBoolean}...) return the
 * default for such a value without a word, so {@code duration-seconds: abc} ran
 * with 30 seconds and nobody was told (found in game, 13/09/2026) - though the
 * README promises that an invalid value is reported in the console. Checked once,
 * for every file, against the copy bundled in the jar, rather than in each of the
 * modules' loaders. Only the settings the bundled file has are checked: a server's
 * own entries (its kits, its nodes, its enchants) have nothing to compare with.
 */
public final class ConfigTypeCheck {

    private ConfigTypeCheck() {
    }

    /**
     * @param defaults every value of the bundled file, by full path
     * @param actual   every value of the file on disk, by full path
     * @return one line per setting that should be a number or true/false and is not,
     *         in the bundled file's order
     */
    public static List<String> mismatches(Map<String, Object> defaults, Map<String, Object> actual) {
        Objects.requireNonNull(defaults, "defaults");
        Objects.requireNonNull(actual, "actual");
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            String path = entry.getKey();
            if (!actual.containsKey(path)) {
                continue;
            }
            Object value = actual.get(path);
            if (entry.getValue() instanceof Number && !(value instanceof Number)) {
                // A quoted number is text to YAML: most settings ignore it as they
                // would a word, a few read it anyway - so no promise either way.
                lines.add(isNumber(value)
                        ? "'" + path + "' is a number written as text ('" + value
                                + "') - write it without quotes, or it may be ignored."
                        : "'" + path + "' should be a number, not '" + value
                                + "' - it is ignored, the default applies.");
            } else if (entry.getValue() instanceof Boolean && !(value instanceof Boolean)) {
                lines.add("'" + path + "' should be true or false, not '" + value
                        + "' - it is ignored, the default applies.");
            }
        }
        return lines;
    }

    private static boolean isNumber(Object value) {
        if (value == null) {
            return false;
        }
        try {
            Double.parseDouble(value.toString().trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
