package com.lawkeys.hcfcore.util;

import java.util.Locale;
import java.util.OptionalLong;
import java.util.function.Consumer;

/**
 * Renders a number of seconds as something a player can read, and reads back the
 * durations staff type.
 *
 * <p>Gathered here because four modules were about to need it and three had
 * already written it: {@code pvp/} and {@code dtr/} carried byte-for-byte
 * identical copies, and {@code events/} a second, shorter style. Both styles are
 * kept - they are different on purpose, and quietly changing either would change
 * text players see - but each now has exactly one implementation.
 *
 * <p><strong>Which one</strong> (both kept by the project owner, 17/09/2026, as the
 * code uses them): {@link #formatWithSeconds} for what is redrawn every second - the
 * scoreboard, the zone holograms - and for PvP and short waits: combat tag,
 * deathban, DTR regeneration, ability, crowbar and ticket cooldowns, custom timers.
 * {@link #format} for chat announcements and listings - events, SOTW, the Purge,
 * refill warnings - and for long waits, ages and totals: kit cooldowns, "opened 2h
 * ago", playtime.
 */
public final class Durations {

    /**
     * The longest duration staff may type: a hundred years, beyond any map.
     *
     * <p>Every such duration ends up as {@code now + seconds * 1000} in milliseconds,
     * and past this it overflows: a ban, a SOTW or a timer typed a few digits too long
     * wrapped round to the past and was over at once (found in the command review,
     * 15/09/2026). Below it, the sum stays far inside a {@code long}.
     */
    public static final long MAX_SECONDS = 100L * 365L * 86_400L;

    private Durations() {
    }

    /**
     * @return the whole seconds left in {@code millis}, rounded up: a countdown shows
     *         {@code 1s} until it is over, never {@code 0s} while time remains;
     *         {@code 0} once nothing is left
     */
    public static long secondsLeft(long millis) {
        return millis <= 0 ? 0L : (millis - 1) / 1000L + 1;
    }

    /** @return whether staff may ask for that many seconds: more than zero, at most {@link #MAX_SECONDS} */
    public static boolean isAcceptable(long seconds) {
        return seconds > 0 && seconds <= MAX_SECONDS;
    }

    /**
     * Bounds a number of seconds read from a configuration file, where a few digits
     * too many overflow exactly like a typed duration.
     *
     * @return {@code seconds}, or {@link #MAX_SECONDS} after a warning naming {@code key}
     */
    public static long capSeconds(long seconds, String key, Consumer<String> warn) {
        if (seconds <= MAX_SECONDS) {
            return seconds;
        }
        warn.accept(key + " is longer than a hundred years; using " + MAX_SECONDS + ".");
        return MAX_SECONDS;
    }

    /**
     * @return a compact form that drops empty trailing units: {@code 45s},
     *         {@code 1m}, {@code 10m 30s}, {@code 1h}, {@code 1h 30m}
     *
     * <p>Used where the number is an approximation the player is watching go
     * down, such as a capture countdown or the wait before a refill.
     */
    public static String format(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long rest = seconds % 60;
        if (minutes < 60) {
            return rest == 0 ? minutes + "m" : minutes + "m " + rest + "s";
        }
        long hours = minutes / 60;
        long restMinutes = minutes % 60;
        return restMinutes == 0 ? hours + "h" : hours + "h " + restMinutes + "m";
    }

    /**
     * Reads a duration typed by staff: {@code 90} (seconds), {@code 45s},
     * {@code 30m}, {@code 2h}, {@code 1d}, or a sequence such as {@code 1h30m}.
     * Case does not matter; spaces are not allowed, since a command splits on them.
     *
     * @return the duration in seconds, or empty if the text is not one, is zero,
     *         or is longer than {@link #MAX_SECONDS}
     */
    public static OptionalLong parse(String input) {
        if (input == null || input.isBlank()) {
            return OptionalLong.empty();
        }
        String text = input.trim().toLowerCase(Locale.ROOT);
        long total = 0L;
        long number = -1L;
        boolean bare = true;
        try {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c >= '0' && c <= '9') {
                    number = Math.addExact(Math.multiplyExact(Math.max(number, 0L), 10L), c - '0');
                    continue;
                }
                long unit = switch (c) {
                    case 's' -> 1L;
                    case 'm' -> 60L;
                    case 'h' -> 3_600L;
                    case 'd' -> 86_400L;
                    default -> -1L;
                };
                if (unit < 0 || number < 0) {
                    return OptionalLong.empty(); // unknown unit, or a unit with no number
                }
                total = Math.addExact(total, Math.multiplyExact(number, unit));
                number = -1L;
                bare = false;
            }
        } catch (ArithmeticException overflow) {
            return OptionalLong.empty();
        }
        if (number >= 0) {
            // A trailing number with no unit: seconds, but only on its own. "1h30"
            // is ambiguous - thirty seconds or thirty minutes - so it is refused.
            if (!bare) {
                return OptionalLong.empty();
            }
            total = number;
        }
        return isAcceptable(total) ? OptionalLong.of(total) : OptionalLong.empty();
    }

    /**
     * @return a {@code 1h 5m 30s} form that always ends in seconds, skipping only
     *         the leading units that are zero
     *
     * <p>Used where the exact remainder matters to the player, such as a deathban
     * or a combat tag.
     */
    public static String formatWithSeconds(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        StringBuilder out = new StringBuilder();
        if (hours > 0) {
            out.append(hours).append("h ");
        }
        if (hours > 0 || minutes > 0) {
            out.append(minutes).append("m ");
        }
        out.append(seconds).append('s');
        return out.toString();
    }
}
