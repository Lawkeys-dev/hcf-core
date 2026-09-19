package com.lawkeys.hcfcore.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Turns template lines into the text a player sees.
 *
 * <p>Pure Java: the server layer collects the values, this decides what the board
 * says. That split is what lets the interesting rules - dropping empty lines,
 * de-duplicating identical ones, honouring the line limit - be tested without a
 * server.
 */
public final class LineRenderer {

    /** A scoreboard shows at most fifteen lines; anything past that is never drawn. */
    public static final int MAX_LINES = 15;

    private final Map<String, String> values = new LinkedHashMap<>();

    /**
     * @param value what this placeholder expands to; {@code null} and empty are the
     *              same thing, and both make a line that consists only of this
     *              placeholder disappear
     */
    public LineRenderer with(String placeholder, String value) {
        values.put(Objects.requireNonNull(placeholder, "placeholder"), value == null ? "" : value);
        return this;
    }

    public LineRenderer with(String placeholder, int value) {
        return with(placeholder, String.valueOf(value));
    }

    /** @return what a placeholder expands to, empty if it was never given */
    public String value(String placeholder) {
        return values.getOrDefault(placeholder, "");
    }

    /** @return this line with its placeholders filled in */
    public String render(String template) {
        String line = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            line = line.replace(entry.getKey(), entry.getValue());
        }
        return line;
    }

    /**
     * @return whether a row is to be dropped: it names at least one placeholder,
     *         and every one it names came out empty. "Team: %team%" with no team is
     *         dropped rather than shown as a bare "Team:"; a separator, which names
     *         none, never is.
     */
    boolean isEmptyRow(String template) {
        boolean namesOne = false;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (template.contains(entry.getKey())) {
                if (!entry.getValue().isEmpty()) {
                    return false;
                }
                namesOne = true;
            }
        }
        return namesOne;
    }

    /**
     * Renders every line and drops the ones that came out empty.
     *
     * <p>An empty line is how a conditional row works: {@code %combat_line%} expands
     * to nothing while the player is not in combat, so the row is simply absent
     * rather than sitting there blank. The same holds for a row with text around its
     * placeholders - see {@link #isEmptyRow}.
     *
     * <p>Colour codes are left as they are, for the caller to translate afterwards:
     * a value can carry its own ({@code %dtr_coloured%} does), and translating the
     * template first would leave those printed as raw {@code &} text.
     *
     * <p>Identical lines are kept as they are: {@code PlayerBoard} gives each row a
     * hidden entry of its own and writes the text as that row's prefix, so two rows
     * reading the same thing - two separator bars, typically - both show.
     */
    public List<String> renderAll(List<String> templates) {
        List<String> rendered = new ArrayList<>();
        for (String template : templates) {
            if (isEmptyRow(template)) {
                continue;
            }
            String line = render(template);
            if (line.isBlank()) {
                continue;
            }
            rendered.add(line);
            if (rendered.size() >= MAX_LINES) {
                break;
            }
        }
        return rendered;
    }
}
