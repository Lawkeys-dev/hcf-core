package com.lawkeys.hcfcore.ui.tab;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which head a grid cell shows, from the tag its line starts with. Pure.
 *
 * <ul>
 *   <li>{@code [head:self]} - the viewer's own head;</li>
 *   <li>{@code [head:member:3]} - the third member of the viewer's team, in the order
 *       of {@code %member_3%};</li>
 *   <li>{@code [head:top:1]} - the leader of the first of the top teams;</li>
 *   <li>{@code [head:MHF_Chest]} - any Minecraft account's skin, by name: the
 *       {@code MHF_} accounts are the classic icons (a chest, a question mark, arrows...).</li>
 * </ul>
 * A line without a tag shows the grid's default head.
 */
public record HeadTag(Kind kind, int index, String name, String text) {

    public enum Kind { NONE, SELF, MEMBER, TOP, ACCOUNT }

    private static final Pattern TAG = Pattern.compile("^\\[head:([^\\]]+)]");
    private static final Pattern ACCOUNT_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    public HeadTag {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
    }

    /** @return the head a line asks for, and the line without its tag */
    public static HeadTag parse(String line) {
        String source = line == null ? "" : line;
        Matcher matcher = TAG.matcher(source);
        if (!matcher.find()) {
            return new HeadTag(Kind.NONE, 0, null, source);
        }
        String rest = source.substring(matcher.end());
        String value = matcher.group(1).trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals("self")) {
            return new HeadTag(Kind.SELF, 0, null, rest);
        }
        for (Kind kind : new Kind[] {Kind.MEMBER, Kind.TOP}) {
            String prefix = kind.name().toLowerCase(Locale.ROOT) + ":";
            if (lower.startsWith(prefix)) {
                try {
                    int index = Integer.parseInt(lower.substring(prefix.length()).trim());
                    return index >= 1 ? new HeadTag(kind, index, null, rest) : new HeadTag(Kind.NONE, 0, null, rest);
                } catch (NumberFormatException e) {
                    return new HeadTag(Kind.NONE, 0, null, rest);
                }
            }
        }
        return ACCOUNT_NAME.matcher(value).matches()
                ? new HeadTag(Kind.ACCOUNT, 0, value, rest)
                : new HeadTag(Kind.NONE, 0, null, rest);
    }
}
