package com.lawkeys.hcfcore.util;

/**
 * Translation of legacy {@code &}-prefixed colour codes into the section sign
 * the Minecraft client expects.
 *
 * <p>Implemented here, in plain Java, rather than through a server-API helper:
 * chat formatting APIs have churned a lot across Minecraft versions, and message
 * rendering is something we want covered by unit tests that run without a
 * server.
 */
public final class ColorCodes {

    /** The character the client interprets as a formatting prefix. */
    public static final char COLOR_CHAR = '§';

    private ColorCodes() {
    }

    /**
     * Replaces {@code &x} with {@code §x} for every valid code {@code x}, and a hex
     * colour {@code &#rrggbb} with the game's {@code §x§r§r§g§g§b§b}.
     *
     * <p>{@code &&} escapes a literal ampersand, and an {@code &} followed by
     * anything that is not a code is left alone - so a message like
     * "Fish &amp; chips" is not mangled.
     */
    public static String translate(String input) {
        if (input == null || input.indexOf('&') < 0) {
            return input;
        }
        char[] chars = input.toCharArray();
        StringBuilder out = new StringBuilder(chars.length);
        for (int i = 0; i < chars.length; i++) {
            char current = chars[i];
            if (current != '&' || i == chars.length - 1) {
                out.append(current);
                continue;
            }
            char next = chars[i + 1];
            if (next == '#' && isHex(chars, i + 2)) {
                out.append(COLOR_CHAR).append('x');
                for (int k = i + 2; k < i + 8; k++) {
                    out.append(COLOR_CHAR).append(Character.toLowerCase(chars[k]));
                }
                i += 7;
            } else if (next == '&') {
                out.append('&');
                i++;
            } else if (isCode(next)) {
                out.append(COLOR_CHAR).append(Character.toLowerCase(next));
                i++;
            } else {
                out.append(current);
            }
        }
        return out.toString();
    }

    /**
     * Protects text a player typed from {@link #translate}.
     *
     * <p>Every message template is translated <em>after</em> its placeholders are
     * filled in, so a player's own text dropped into one would have its {@code &}
     * codes honoured - colouring or obfuscating a line staff have to read. Doubling
     * each ampersand turns it into the escape {@link #translate} already understands,
     * and the text comes out exactly as it was typed.
     *
     * <p>A raw {@link #COLOR_CHAR} is dropped: it needs no translation to format the
     * line, and a command argument can carry one. A brace is followed by an invisible
     * zero-width space, so a player who types {@code {prefix}} or {@code {primary}}
     * does not get the theme's tokens ({@code theme.yml}) either.
     */
    public static String escape(String input) {
        return input == null ? null
                : input.replace(String.valueOf(COLOR_CHAR), "").replace("&", "&&").replace("{", "{\u200B");
    }

    /** Strips every already-translated formatting sequence from {@code input}. */
    public static String strip(String input) {
        if (input == null || input.indexOf(COLOR_CHAR) < 0) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (current == COLOR_CHAR && i + 1 < input.length()
                    && (isCode(input.charAt(i + 1)) || Character.toLowerCase(input.charAt(i + 1)) == 'x')) {
                i++;
                continue;
            }
            out.append(current);
        }
        return out.toString();
    }

    /** Whether six hex digits start at {@code from}. */
    private static boolean isHex(char[] chars, int from) {
        if (from + 6 > chars.length) {
            return false;
        }
        for (int k = from; k < from + 6; k++) {
            if (Character.digit(chars[k], 16) < 0) {
                return false;
            }
        }
        return true;
    }

    /** Colours 0-9 and a-f, formats k-o, and reset r: the standard legacy code set. */
    private static boolean isCode(char c) {
        char lower = Character.toLowerCase(c);
        return (lower >= '0' && lower <= '9')
                || (lower >= 'a' && lower <= 'f')
                || (lower >= 'k' && lower <= 'o')
                || lower == 'r';
    }
}
