package com.lawkeys.hcfcore.hologram;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills in a hologram's placeholders.
 *
 * <p>Pure Java. The one kind of placeholder is the leaderboard, the use FEATURES.md
 * section 14 gives as its example and the one with data behind it:
 * {@code %top_<board>_<rank>_name%} and {@code %top_<board>_<rank>_value%}, where the
 * board is {@code kills}, {@code deaths}, {@code kdr}, {@code killstreak} or
 * {@code playtime} and the rank runs from 1. A rank nobody holds yet shows the
 * configured filler rather than the raw placeholder.
 */
public final class HologramText {

    /** The deepest rank a hologram can show: a board is a top list, not the whole server. */
    public static final int MAX_RANK = 100;

    private static final Pattern TOP = Pattern.compile("%top_([a-z]+)_(\\d{1,3})_(name|value)%");

    /** One leaderboard row. */
    public record Entry(String name, String value) {
    }

    private HologramText() {
    }

    /** @return whether any line needs refreshing, so static holograms are never redrawn */
    public static boolean isDynamic(List<String> lines) {
        for (String line : lines) {
            if (TOP.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param board  a board and a rank from 1 to its row, or empty when nobody
     *               holds that rank - or when the board is not one of the five
     * @param filler what an empty rank shows
     */
    public static List<String> render(List<String> lines, BiFunction<String, Integer, Optional<Entry>> board,
                                      String filler) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            Matcher matcher = TOP.matcher(line);
            StringBuilder rendered = new StringBuilder();
            while (matcher.find()) {
                int rank = Integer.parseInt(matcher.group(2));
                Optional<Entry> entry = rank >= 1 && rank <= MAX_RANK
                        ? board.apply(matcher.group(1), rank)
                        : Optional.empty();
                String value = entry.map(e -> matcher.group(3).equals("name") ? e.name() : e.value())
                        .orElse(matcher.group(3).equals("name") ? filler : "");
                matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
            }
            matcher.appendTail(rendered);
            out.add(rendered.toString());
        }
        return out;
    }
}
