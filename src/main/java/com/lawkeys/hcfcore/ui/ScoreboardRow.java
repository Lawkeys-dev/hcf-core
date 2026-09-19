package com.lawkeys.hcfcore.ui;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One row of {@code ui.yml}'s scoreboard, and the section it belongs to: a row
 * written {@code "[team]&cTeam: &f%team%"} is in section {@code team}, which a player
 * may hide in {@code /settings}; a row with no tag is always shown.
 *
 * @param section the tag, lower case, or {@code null} for none
 * @param text    the row without its tag
 */
public record ScoreboardRow(String section, String text) {

    private static final Pattern TAG = Pattern.compile("^\\[([a-zA-Z0-9_-]+)]");

    public static ScoreboardRow parse(String line) {
        if (line == null) {
            return new ScoreboardRow(null, "");
        }
        Matcher tag = TAG.matcher(line);
        if (!tag.find()) {
            return new ScoreboardRow(null, line);
        }
        return new ScoreboardRow(tag.group(1).toLowerCase(Locale.ROOT), line.substring(tag.end()));
    }
}
