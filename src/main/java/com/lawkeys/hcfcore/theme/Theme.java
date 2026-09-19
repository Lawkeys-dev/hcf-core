package com.lawkeys.hcfcore.theme;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The plugin's look ({@code theme.yml}): its colours by role, its message prefix,
 * its list symbol, its menus. Every text the plugin shows - {@code lang/en.yml}, the
 * other files' names and lines - may say {@code {primary}}, {@code {muted}},
 * {@code {prefix}}... instead of a colour code: changing a colour here changes it
 * everywhere at once. {@code {bullet}} is the list symbol, {@code {action}} what
 * starts a menu item's "click to" line.
 *
 * <p>Pure Java: {@link #apply} turns the tokens into {@code &} codes, which
 * {@code ColorCodes} then translates as ever.
 *
 * <p>A role may be a <em>gradient</em>, {@code #F5B32E>#FF4D3D} - two colours or more:
 * the text it colours fades from one to the next, letter by letter, up to the next
 * colour change.
 *
 * @param colors    role to hex colour, {@code #rrggbb}, or a gradient, {@code #rrggbb>#rrggbb}
 * @param prefix    what {@code {prefix}} becomes; it may use the colour tokens
 * @param bullet    what {@code {bullet}} becomes: a list symbol, {@code ➥}
 * @param smallCaps whether menu and scoreboard titles are written in small capitals
 * @param menus     how menus are framed
 * @param overrides what the theme sets in place of the other files: texts, chat, nametags
 */
public record Theme(Map<String, String> colors, String prefix, String bullet, boolean smallCaps, Menus menus,
                    Overrides overrides) {

    /** Every colour role, in the order the file lists them. */
    public static final java.util.List<String> ROLES = java.util.List.of(
            "primary", "secondary", "text", "muted", "dark", "success", "error", "warning");

    private static final Pattern TOKEN = Pattern.compile("\\{([a-z]+)}");
    private static final Pattern HEX = Pattern.compile("#?[0-9a-fA-F]{6}");
    private static final Pattern GRADIENT = Pattern.compile("#?[0-9a-fA-F]{6}(\\s*>\\s*#?[0-9a-fA-F]{6})+");
    /** Marks where a gradient role starts, until {@link #expandGradients} lays it out. */
    private static final char MARK = '\u0001';
    private static final String SMALL_FROM = "abcdefghijklmnopqrstuvwxyz";
    private static final String SMALL_TO = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    /**
     * How a menu is laid out.
     *
     * @param frame      {@code full}: panes all around; {@code bars}: the top and bottom rows; {@code none}
     * @param pane       the frame's pane, as the server spells materials
     * @param cornerPane the four corners' pane
     * @param action     what starts a line saying what a click does, {@code » }
     */
    public record Menus(String frame, String pane, String cornerPane, String action) {
        public Menus {
            frame = Objects.requireNonNullElse(frame, "full").toLowerCase(Locale.ROOT);
            pane = Objects.requireNonNullElse(pane, "BLACK_STAINED_GLASS_PANE").toUpperCase(Locale.ROOT);
            cornerPane = Objects.requireNonNullElse(cornerPane, pane).toUpperCase(Locale.ROOT);
            action = Objects.requireNonNullElse(action, "");
        }

        public boolean framed() {
            return frame.equals("full") || frame.equals("bars");
        }
    }

    /**
     * What the theme sets in place of the other files, so one {@code theme.yml} carries
     * a whole look. Each is optional: {@code null}, or empty, leaves the other file's.
     *
     * @param messages       language key ({@code menus.next}) to text, over {@code lang/en.yml}
     * @param chatFormat     over {@code chat.yml}'s {@code format}
     * @param killsFormat    over {@code chat.yml}'s {@code kills-format}
     * @param nametagTeam    over {@code apollo.yml}'s {@code nametags.team-line}
     * @param nametagName    over {@code apollo.yml}'s {@code nametags.name-line}
     * @param nametagColors  relation ({@code self}, {@code ally}...) to its colour, over {@code nametags.colors}
     */
    public record Overrides(Map<String, String> messages, String chatFormat, String killsFormat,
                            String nametagTeam, String nametagName, Map<String, String> nametagColors) {
        public static final Overrides NONE = new Overrides(Map.of(), null, null, null, null, Map.of());

        public Overrides {
            messages = Map.copyOf(Objects.requireNonNullElse(messages, Map.of()));
            nametagColors = Map.copyOf(Objects.requireNonNullElse(nametagColors, Map.of()));
        }
    }

    public Theme {
        Map<String, String> clean = new LinkedHashMap<>();
        colors.forEach((role, hex) -> clean.put(role.toLowerCase(Locale.ROOT), normalise(hex)));
        colors = Map.copyOf(clean);
        prefix = Objects.requireNonNullElse(prefix, "");
        bullet = Objects.requireNonNullElse(bullet, "");
        Objects.requireNonNull(menus, "menus");
        overrides = Objects.requireNonNullElse(overrides, Overrides.NONE);
    }

    public Theme(Map<String, String> colors, String prefix, String bullet, boolean smallCaps, Menus menus) {
        this(colors, prefix, bullet, smallCaps, menus, Overrides.NONE);
    }

    /** As shipped: Or royal, the owner's choice of 19/09/2026. */
    public static Theme defaults() {
        Map<String, String> colors = new LinkedHashMap<>();
        colors.put("primary", "#F5B32E");
        colors.put("secondary", "#FFE39A");
        colors.put("text", "#F7F1E3");
        colors.put("muted", "#B3A88F");
        colors.put("dark", "#5E5540");
        colors.put("success", "#8CE36B");
        colors.put("error", "#F2594B");
        colors.put("warning", "#FFD24A");
        return new Theme(colors, "{primary}&lHCF&r{dark} » ", "➥", true,
                new Menus("full", "BLACK_STAINED_GLASS_PANE", "YELLOW_STAINED_GLASS_PANE", "» "));
    }

    /** @return whether this is a hex colour, with or without its {@code #} */
    public static boolean isHex(String value) {
        return value != null && HEX.matcher(value.trim()).matches();
    }

    /** @return whether this is a colour a role may be: a hex colour, or a gradient of them */
    public static boolean isColour(String value) {
        return value != null && (isHex(value) || GRADIENT.matcher(value.trim()).matches());
    }

    private static String normalise(String colour) {
        StringBuilder out = new StringBuilder();
        for (String stop : colour.split(">")) {
            String value = stop.trim();
            if (!out.isEmpty()) {
                out.append('>');
            }
            out.append((value.startsWith("#") ? value : "#" + value).toUpperCase(Locale.ROOT));
        }
        return out.toString();
    }

    /**
     * Turns the tokens into codes: a colour role into {@code &#rrggbb}, {@code {prefix}}
     * into the prefix, {@code {bullet}} into the list symbol. An unknown token is
     * left as it is written, so a brace in a message means what it says.
     */
    public String apply(String input) {
        if (input == null || input.indexOf('{') < 0) {
            return input;
        }
        String once = replace(input, true);
        // The prefix may itself use the colour tokens.
        String twice = once.indexOf('{') < 0 ? once : replace(once, false);
        return twice.indexOf(MARK) < 0 ? twice : expandGradients(twice);
    }

    private String replace(String input, boolean withPrefix) {
        Matcher tokens = TOKEN.matcher(input);
        StringBuilder out = new StringBuilder(input.length() + 16);
        while (tokens.find()) {
            String name = tokens.group(1);
            String colour = colors.get(name);
            String value = colour != null && colour.indexOf('>') >= 0 ? MARK + name + MARK
                    : colour != null ? "&" + colour
                    : withPrefix && name.equals("prefix") ? prefix
                    : name.equals("bullet") ? bullet
                    : name.equals("action") ? menus.action()
                    : tokens.group();
            tokens.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        tokens.appendTail(out);
        return out.toString();
    }

    /**
     * Lays each gradient out: the text after a gradient role, up to the next colour
     * change, gets a colour per letter, fading through the role's stops. A format
     * ({@code &l}) inside it is kept on every letter - a colour code resets it.
     */
    private String expandGradients(String input) {
        StringBuilder out = new StringBuilder(input.length() * 4);
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c != MARK) {
                out.append(c);
                i++;
                continue;
            }
            int close = input.indexOf(MARK, i + 1);
            if (close < 0) {
                break;
            }
            String role = input.substring(i + 1, close);
            int end = segmentEnd(input, close + 1);
            out.append(gradient(stops(colors.get(role)), input.substring(close + 1, end)));
            i = end;
        }
        return out.toString();
    }

    /** Where a gradient stops: the next gradient, or the next colour code - a format code does not. */
    private static int segmentEnd(String input, int from) {
        int j = from;
        while (j < input.length()) {
            char c = input.charAt(j);
            if (c == MARK) {
                return j;
            }
            if (c == '&' && j + 1 < input.length()) {
                char next = Character.toLowerCase(input.charAt(j + 1));
                if (next == '&') {
                    j += 2;
                    continue;
                }
                if (next == '#' || next == 'r' || (next >= '0' && next <= '9') || (next >= 'a' && next <= 'f')) {
                    return j;
                }
            }
            j++;
        }
        return input.length();
    }

    private static int[][] stops(String gradient) {
        String[] parts = gradient.split(">");
        int[][] out = new int[parts.length][];
        for (int k = 0; k < parts.length; k++) {
            int rgb = Integer.parseInt(parts[k].trim().substring(1), 16);
            out[k] = new int[] {(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
        }
        return out;
    }

    private static String gradient(int[][] stops, String segment) {
        // What is shown, one entry per letter; the formats met on the way are kept for the letters after them.
        java.util.List<String> letters = new java.util.ArrayList<>();
        java.util.List<String> formatsAt = new java.util.ArrayList<>();
        StringBuilder formats = new StringBuilder();
        int i = 0;
        while (i < segment.length()) {
            char c = segment.charAt(i);
            if (c == '&' && i + 1 < segment.length()) {
                char next = Character.toLowerCase(segment.charAt(i + 1));
                if (next == '&') {
                    letters.add("&&");
                    formatsAt.add(formats.toString());
                    i += 2;
                    continue;
                }
                if (next >= 'k' && next <= 'o') {
                    formats.append('&').append(next);
                    i += 2;
                    continue;
                }
            }
            int cp = segment.codePointAt(i);
            letters.add(new String(Character.toChars(cp)));
            formatsAt.add(formats.toString());
            i += Character.charCount(cp);
        }
        StringBuilder out = new StringBuilder(segment.length() * 16);
        int count = letters.size();
        for (int k = 0; k < count; k++) {
            String letter = letters.get(k);
            if (letter.isBlank()) {
                out.append(letter);
                continue;
            }
            double t = count == 1 ? 0.0 : (double) k / (count - 1);
            out.append('&').append(hexAt(stops, t)).append(formatsAt.get(k)).append(letter);
        }
        return out.toString();
    }

    private static String hexAt(int[][] stops, double t) {
        double scaled = t * (stops.length - 1);
        int from = Math.min((int) Math.floor(scaled), stops.length - 2);
        double local = scaled - from;
        int[] a = stops[from];
        int[] b = stops[from + 1];
        int r = (int) Math.round(a[0] + (b[0] - a[0]) * local);
        int g = (int) Math.round(a[1] + (b[1] - a[1]) * local);
        int bl = (int) Math.round(a[2] + (b[2] - a[2]) * local);
        return String.format(Locale.ROOT, "#%02X%02X%02X", r, g, bl);
    }

    /**
     * A title as the theme writes it: in small capitals with {@code small-caps-titles},
     * its codes, tokens and placeholders left alone.
     */
    public String title(String input) {
        if (!smallCaps || input == null) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length());
        boolean inToken = false;
        boolean inPlaceholder = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '&' && input.startsWith("#", i + 1) && i + 8 <= input.length()) {
                out.append(input, i, i + 8);
                i += 7;
                continue;
            }
            if ((c == '&' || c == '§') && i + 1 < input.length()) {
                out.append(c).append(input.charAt(++i));
                continue;
            }
            if (c == '{') {
                inToken = true;
            } else if (c == '}') {
                inToken = false;
            } else if (c == '%') {
                inPlaceholder = !inPlaceholder;
            }
            int small = inToken || inPlaceholder ? -1 : SMALL_FROM.indexOf(Character.toLowerCase(c));
            out.append(small < 0 ? c : SMALL_TO.charAt(small));
        }
        return out.toString();
    }
}
