package com.lawkeys.hcfcore.chat;

import java.util.Objects;

/**
 * Immutable snapshot of {@code chat.yml}.
 *
 * <p>The format is a template rather than a fixed layout, which is what FEATURES.md
 * section 9 asks for: the order of the parts, the colours and the separators are all
 * the operator's to choose.
 *
 * @param enabled      whether this module formats chat at all; when off, the server's
 *                     own format is left alone
 * @param format       the public chat line
 * @param killsFormat  what {@code %kills%} expands to - separate so the brackets and
 *                     colour of the classic {@code [50]} can be changed, or the whole
 *                     thing emptied, without touching the main template
 * @param rangeBlocks  how far a public message carries, in blocks; {@code 0} is the
 *                     whole server, which is the usual HCF setting
 * @param logTeamChat  whether team and ally lines are written to the console, as
 *                     public chat already is - so staff can read them back
 */
public record ChatSettings(boolean enabled, String format, String killsFormat, int rangeBlocks,
                           boolean logTeamChat) {

    /** Expands to the player's name. */
    public static final String PLAYER = "%player%";
    /** Expands to what they said. */
    public static final String MESSAGE = "%message%";
    /** Expands to the LuckPerms prefix, or nothing. */
    public static final String PREFIX = "%prefix%";
    /** Expands to the LuckPerms suffix, or nothing. */
    public static final String SUFFIX = "%suffix%";
    /** Expands to {@link #killsFormat}, or nothing when the player has no kills. */
    public static final String KILLS = "%kills%";

    public ChatSettings {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(killsFormat, "killsFormat");
    }

    /**
     * Renders one chat line.
     *
     * <p>Pure, and therefore testable: this is the whole of the feature's logic, and
     * the listener around it only supplies the four strings.
     *
     * @param kills the player's kill count, which is dropped from the line entirely
     *              when it is zero - a fresh player reading {@code [0]} next to their
     *              name is noise, and every HCF server hides it
     */
    public String render(String prefix, String name, String suffix, int kills, String message) {
        String killsPart = kills <= 0
                ? ""
                : killsFormat.replace("%value%", String.valueOf(kills));
        return format
                .replace(PREFIX, prefix == null ? "" : prefix)
                .replace(SUFFIX, suffix == null ? "" : suffix)
                .replace(KILLS, killsPart)
                .replace(PLAYER, name == null ? "" : name)
                // Last, and deliberately: a player typing "%player%" must not have it
                // expanded, so the message goes in after every other placeholder is gone.
                .replace(MESSAGE, message == null ? "" : message);
    }

    /** Built-in fallback, mirroring {@code resources/chat.yml}. */
    public static ChatSettings defaults() {
        return new ChatSettings(true, "%kills%%prefix%{text}%player%%suffix%{dark}: {text}%message%",
                "{dark}[{primary}%value%{dark}]&r ", 0, true);
    }
}
