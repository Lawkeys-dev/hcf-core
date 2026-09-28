package com.lawkeys.hcfcore.integration.discord;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * {@code discord.yml}: the webhooks, and which announcements go to which - pure,
 * unit-tested (the project owner's request, 23/09/2026).
 *
 * @param webhooks a name of the operator's choosing to a Discord webhook URL
 * @param forward  the rules, in order: the first whose pattern matches an
 *                 announcement's language key decides where it goes
 * @param embeds   posts as embeds - the text in a coloured box - rather than plain text
 * @param sensors  the things Discord hears besides the announcements: {@code kills},
 *                 {@code teams}, {@code alliances}, {@code raids}, {@code server},
 *                 {@code joins} - those switched on
 */
public record DiscordRules(boolean enabled, Map<String, String> webhooks, List<Rule> forward, String username,
                           String avatarUrl, boolean embeds, java.util.Set<String> sensors) {

    /**
     * @param keys    a language key, where {@code *} stands for any run of characters:
     *                {@code events.*} is every event announcement
     * @param to      the webhook's name; empty sends the announcement nowhere
     * @param mention who to ping with it: a role ({@code <@&id>}) or a member
     *                ({@code <@id>}); empty for nobody - nothing else ever pings
     * @param color   the embed's colour, {@code 0xRRGGBB}; {@code -1} for Discord's own
     */
    public record Rule(String keys, String to, String mention, int color) {

        public Rule {
            Objects.requireNonNull(keys, "keys");
            to = to == null ? "" : to.trim();
            mention = mention == null ? "" : mention.trim();
        }

        public Rule(String keys, String to) {
            this(keys, to, "", -1);
        }

        boolean matches(String key) {
            StringBuilder regex = new StringBuilder();
            for (String part : keys.trim().toLowerCase(Locale.ROOT).split("\\*", -1)) {
                if (!regex.isEmpty()) {
                    regex.append(".*");
                }
                regex.append(Pattern.quote(part));
            }
            return key.toLowerCase(Locale.ROOT).matches(regex.toString());
        }
    }

    public DiscordRules {
        webhooks = Map.copyOf(Objects.requireNonNull(webhooks, "webhooks"));
        forward = List.copyOf(Objects.requireNonNull(forward, "forward"));
        username = username == null ? "" : username;
        avatarUrl = avatarUrl == null ? "" : avatarUrl;
        sensors = java.util.Set.copyOf(Objects.requireNonNull(sensors, "sensors"));
    }

    public DiscordRules(boolean enabled, Map<String, String> webhooks, List<Rule> forward, String username,
                        String avatarUrl) {
        this(enabled, webhooks, forward, username, avatarUrl, false, java.util.Set.of());
    }

    public static DiscordRules off() {
        return new DiscordRules(false, Map.of(), List.of(), "", "");
    }

    /** Where an announcement goes, and how. */
    public record Route(String url, String mention, int color) {
    }

    /** @return whether this sensor is switched on */
    public boolean hears(String sensor) {
        return enabled && sensors.contains(sensor);
    }

    /** @return the URL this announcement goes to, or empty for nowhere */
    public Optional<String> urlFor(String key) {
        return routeFor(key).map(Route::url);
    }

    /** @return where this announcement goes and how, or empty for nowhere */
    public Optional<Route> routeFor(String key) {
        if (!enabled || key == null) {
            return Optional.empty();
        }
        for (Rule rule : forward) {
            if (rule.matches(key)) {
                String url = rule.to().isEmpty() ? "" : webhooks.getOrDefault(rule.to(), "");
                return url.isBlank() ? Optional.empty() : Optional.of(new Route(url.trim(), rule.mention(), rule.color()));
            }
        }
        return Optional.empty();
    }

    /** @return the body of a plain post, with no ping */
    public String body(String text) {
        return body(text, new Route("", "", -1));
    }

    /**
     * @return the webhook's JSON body: the message, cut to Discord's limits, as text or
     *         an embed, and only the route's own mention allowed to ping - a team or a
     *         player named "@everyone" must never ping a whole Discord server
     */
    public String body(String text, Route route) {
        List<String> fields = new ArrayList<>();
        String mention = route.mention();
        if (embeds) {
            String description = text.length() > 4000 ? text.substring(0, 3997) + "..." : text;
            if (!mention.isEmpty()) {
                fields.add("\"content\":" + json(mention));
            }
            String embed = "{\"description\":" + json(description)
                    + (route.color() >= 0 ? ",\"color\":" + route.color() : "") + "}";
            fields.add("\"embeds\":[" + embed + "]");
        } else {
            String withMention = mention.isEmpty() ? text : mention + " " + text;
            String content = withMention.length() > 2000 ? withMention.substring(0, 1997) + "..." : withMention;
            fields.add("\"content\":" + json(content));
        }
        if (!username.isBlank()) {
            fields.add("\"username\":" + json(username));
        }
        if (!avatarUrl.isBlank()) {
            fields.add("\"avatar_url\":" + json(avatarUrl));
        }
        fields.add("\"allowed_mentions\":" + allowed(mention));
        return "{" + String.join(",", fields) + "}";
    }

    /** Only the rule's own mention may ping: its role or its member, and nothing else. */
    static String allowed(String mention) {
        java.util.regex.Matcher role = Pattern.compile("<@&(\\d+)>").matcher(mention);
        if (role.matches()) {
            return "{\"parse\":[],\"roles\":[\"" + role.group(1) + "\"]}";
        }
        java.util.regex.Matcher user = Pattern.compile("<@!?(\\d+)>").matcher(mention);
        if (user.matches()) {
            return "{\"parse\":[],\"users\":[\"" + user.group(1) + "\"]}";
        }
        return "{\"parse\":[]}";
    }

    static String json(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
