package com.lawkeys.hcfcore.database.migration;

import com.lawkeys.hcfcore.claim.ClaimSchema;
import com.lawkeys.hcfcore.dtr.DtrSchema;
import com.lawkeys.hcfcore.economy.EconomySchema;
import com.lawkeys.hcfcore.events.king.KingSchema;
import com.lawkeys.hcfcore.hologram.HologramSchema;
import com.lawkeys.hcfcore.kit.KitSchema;
import com.lawkeys.hcfcore.limiter.LimiterSchema;
import com.lawkeys.hcfcore.lives.LivesSchema;
import com.lawkeys.hcfcore.phase.PhaseSchema;
import com.lawkeys.hcfcore.pvp.PvpSchema;
import com.lawkeys.hcfcore.redeem.RedeemSchema;
import com.lawkeys.hcfcore.settings.SettingsSchema;
import com.lawkeys.hcfcore.staff.StaffSchema;
import com.lawkeys.hcfcore.stats.StatsSchema;
import com.lawkeys.hcfcore.team.TeamSchema;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No migration names a table, column or index with a MySQL reserved word.
 *
 * <p>The tests run on SQLite, which accepts them: {@code lines} was a column of
 * {@code hcf_holograms} from the start, and the first start on a real MySQL 8.4
 * refused the migration outright and kept the server closed (13/09/2026). The list
 * comes from MySQL itself; an identifier quoted with backticks, which both
 * databases accept, passes.
 */
class MysqlReservedWordsTest {

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "CREATE TABLE(?:\\s+IF NOT EXISTS)?\\s+(\\S+)\\s*\\((.*)\\)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ADD_COLUMN = Pattern.compile(
            "ALTER TABLE\\s+(\\S+)\\s+ADD(?:\\s+COLUMN)?\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATE_INDEX = Pattern.compile(
            "CREATE(?:\\s+UNIQUE)?\\s+INDEX(?:\\s+IF NOT EXISTS)?\\s+(\\S+)\\s+ON\\s+(\\S+)\\s*\\(([^)]*)\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CONSTRAINT = Pattern.compile(
            "^(PRIMARY KEY|UNIQUE|FOREIGN KEY|CONSTRAINT|KEY|INDEX|CHECK)\\b", Pattern.CASE_INSENSITIVE);

    static List<Migration> allMigrations() {
        return Stream.of(TeamSchema.migrations(), ClaimSchema.migrations(), DtrSchema.migrations(),
                        PvpSchema.migrations(), EconomySchema.migrations(), KingSchema.migrations(),
                        PhaseSchema.migrations(), StaffSchema.migrations(), StatsSchema.migrations(),
                        KitSchema.migrations(), LimiterSchema.migrations(), LivesSchema.migrations(),
                        RedeemSchema.migrations(), SettingsSchema.migrations(), HologramSchema.migrations(),
                        com.lawkeys.hcfcore.economy.bounty.BountySchema.migrations())
                .flatMap(List::stream)
                .toList();
    }

    private static Set<String> reservedWords() throws IOException {
        Set<String> words = new HashSet<>();
        try (InputStream in = Objects.requireNonNull(
                MysqlReservedWordsTest.class.getResourceAsStream("/mysql-8.4-reserved-words.txt"));
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    words.add(line.toLowerCase(Locale.ROOT));
                }
            }
        }
        return words;
    }

    @Test
    void theListIsMysqlsOwn() throws IOException {
        Set<String> words = reservedWords();
        assertEquals(262, words.size());
        assertTrue(words.containsAll(List.of("lines", "rank", "key", "order", "group", "index", "range")));
    }

    @Test
    void noMigrationUsesAReservedWordBare() throws IOException {
        Set<String> reserved = reservedWords();
        List<String> found = new ArrayList<>();
        List<Migration> migrations = allMigrations();
        assertTrue(migrations.size() >= 23, "every module's schema is listed");
        for (Migration migration : migrations) {
            String where = migration.module() + " v" + migration.version();
            for (String statement : migration.statements()) {
                for (String identifier : identifiers(statement)) {
                    if (!identifier.startsWith("`") && reserved.contains(identifier.toLowerCase(Locale.ROOT))) {
                        found.add(where + ": " + identifier);
                    }
                }
            }
        }
        assertTrue(found.isEmpty(), "reserved in MySQL - quote with backticks or rename: " + found);
    }

    @Test
    void theParserSeesColumnsConstraintsAndIndexes() {
        List<String> names = identifiers("""
                CREATE TABLE IF NOT EXISTS hcf_x (
                    a VARCHAR(36) NOT NULL,
                    b DECIMAL(10, 2),
                    PRIMARY KEY (a, b)
                )""");
        assertEquals(List.of("hcf_x", "a", "b", "a", "b"), names);
        assertEquals(List.of("hcf_x", "zone"), identifiers("ALTER TABLE hcf_x ADD COLUMN zone VARCHAR(8)"));
        assertEquals(List.of("idx", "hcf_x", "a", "b"), identifiers("CREATE INDEX idx ON hcf_x (a, b)"));
    }

    /** Table, column and index names a statement declares or lists. */
    static List<String> identifiers(String statement) {
        List<String> names = new ArrayList<>();
        Matcher create = CREATE_TABLE.matcher(statement.strip());
        if (create.find()) {
            names.add(create.group(1));
            for (String definition : splitTopLevel(create.group(2))) {
                String trimmed = definition.strip();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (CONSTRAINT.matcher(trimmed).find()) {
                    int open = trimmed.indexOf('(');
                    int close = trimmed.indexOf(')', open + 1);
                    if (open >= 0 && close > open) {
                        for (String column : trimmed.substring(open + 1, close).split(",")) {
                            names.add(column.strip());
                        }
                    }
                } else {
                    names.add(trimmed.split("\\s+", 2)[0]);
                }
            }
        }
        Matcher add = ADD_COLUMN.matcher(statement);
        while (add.find()) {
            names.add(add.group(1));
            names.add(add.group(2));
        }
        Matcher index = CREATE_INDEX.matcher(statement);
        while (index.find()) {
            names.add(index.group(1));
            names.add(index.group(2));
            for (String column : index.group(3).split(",")) {
                names.add(column.strip());
            }
        }
        return names;
    }

    private static List<String> splitTopLevel(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : body.toCharArray()) {
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
    }
}
