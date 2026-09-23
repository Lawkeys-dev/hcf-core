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
 */
public record DiscordRules(boolean enabled, Map<String, String> webhooks, List<Rule> forward, String username,
                           String avatarUrl) {

    /**
     * @param keys a language key, where {@code *} stands for any run of characters:
     *             {@code events.*} is every event announcement
     * @param to   the webhook's name; empty sends the announcement nowhere
     */
    public record Rule(String keys, String to) {

        public Rule {
            Objects.requireNonNull(keys, "keys");
            to = to == null ? "" : to.trim();
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
    }

    public static DiscordRules off() {
        return new DiscordRules(false, Map.of(), List.of(), "", "");
    }

    /** @return the URL this announcement goes to, or empty for nowhere */
    public Optional<String> urlFor(String key) {
        if (!enabled || key == null) {
            return Optional.empty();
        }
        for (Rule rule : forward) {
            if (rule.matches(key)) {
                String url = rule.to().isEmpty() ? "" : webhooks.getOrDefault(rule.to(), "");
                return url.isBlank() ? Optional.empty() : Optional.of(url.trim());
            }
        }
        return Optional.empty();
    }

    /**
     * @return the webhook's JSON body: the message, cut to Discord's 2000 characters,
     *         and no mention of anybody - a team or a player named "@everyone" must
     *         never ping a whole Discord server
     */
    public String body(String text) {
        String content = text.length() > 2000 ? text.substring(0, 1997) + "..." : text;
        List<String> fields = new ArrayList<>();
        fields.add("\"content\":" + json(content));
        if (!username.isBlank()) {
            fields.add("\"username\":" + json(username));
        }
        if (!avatarUrl.isBlank()) {
            fields.add("\"avatar_url\":" + json(avatarUrl));
        }
        fields.add("\"allowed_mentions\":{\"parse\":[]}");
        return "{" + String.join(",", fields) + "}";
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
