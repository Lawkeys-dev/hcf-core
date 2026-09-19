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
 * @param colors    role to hex colour, {@code #rrggbb}
 * @param prefix    what {@code {prefix}} becomes; it may use the colour tokens
 * @param bullet    what {@code {bullet}} becomes: a list symbol, {@code ➥}
 * @param smallCaps whether menu and scoreboard titles are written in small capitals
 * @param menus     how menus are framed
 */
public record Theme(Map<String, String> colors, String prefix, String bullet, boolean smallCaps, Menus menus) {

    /** Every colour role, in the order the file lists them. */
    public static final java.util.List<String> ROLES = java.util.List.of(
            "primary", "secondary", "text", "muted", "dark", "success", "error", "warning");

    private static final Pattern TOKEN = Pattern.compile("\\{([a-z]+)}");
    private static final Pattern HEX = Pattern.compile("#?[0-9a-fA-F]{6}");
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

    public Theme {
        Map<String, String> clean = new LinkedHashMap<>();
        colors.forEach((role, hex) -> clean.put(role.toLowerCase(Locale.ROOT), normalise(hex)));
        colors = Map.copyOf(clean);
        prefix = Objects.requireNonNullElse(prefix, "");
        bullet = Objects.requireNonNullElse(bullet, "");
        Objects.requireNonNull(menus, "menus");
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

    private static String normalise(String hex) {
        String value = hex.trim();
        return (value.startsWith("#") ? value : "#" + value).toUpperCase(Locale.ROOT);
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
        return once.indexOf('{') < 0 ? once : replace(once, false);
    }

    private String replace(String input, boolean withPrefix) {
        Matcher tokens = TOKEN.matcher(input);
        StringBuilder out = new StringBuilder(input.length() + 16);
        while (tokens.find()) {
            String name = tokens.group(1);
            String value = colors.containsKey(name) ? "&" + colors.get(name)
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
