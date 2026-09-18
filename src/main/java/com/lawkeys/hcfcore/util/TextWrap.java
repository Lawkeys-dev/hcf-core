package com.lawkeys.hcfcore.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Breaks a line of text into lines no wider than a given number of characters.
 *
 * <p>For item lore, which the client never wraps: a long sentence in a single lore
 * line runs off the side of the screen.
 */
public final class TextWrap {

    private TextWrap() {
    }

    /**
     * Wraps at spaces where it can, and cuts a word only when that word alone is
     * wider than a line.
     *
     * @param width the widest a line may be; at least 1
     * @return the lines, never empty - blank text gives one empty line
     */
    public static List<String> wrap(String text, int width) {
        if (width < 1) {
            throw new IllegalArgumentException("width must be at least 1");
        }
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : (text == null ? "" : text.trim()).split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            while (word.length() > width) {
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                lines.add(word.substring(0, width));
                word = word.substring(width);
            }
            if (!line.isEmpty() && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty() || lines.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }
}
