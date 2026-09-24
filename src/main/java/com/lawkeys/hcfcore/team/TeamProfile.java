package com.lawkeys.hcfcore.team;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What a team says about itself - its description and its Discord invitation -
 * checked before it is kept. Pure.
 */
public final class TeamProfile {

    private TeamProfile() {
    }

    /**
     * @return the description as it will be shown: trimmed, cut to {@code maxLength},
     *         without colour codes or theme tokens - the team's words, never read as
     *         formatting (as {@code /lff}'s note). Empty clears it
     */
    public static String description(String typed, int maxLength) {
        if (typed == null) {
            return "";
        }
        String text = typed.replace("&", "").replace("{", "").replace("}", "").replace("§", "")
                .replaceAll("\\s+", " ").trim();
        return text.length() > maxLength ? text.substring(0, maxLength).trim() : text;
    }

    /**
     * @param pattern what an invitation link must look like ({@code team-settings.discord.pattern})
     * @return the link, with {@code https://} in front if it had no scheme; empty when
     *         it is not a Discord invitation
     */
    public static Optional<String> discord(String typed, Pattern pattern) {
        if (typed == null) {
            return Optional.empty();
        }
        String link = typed.trim();
        if (link.isEmpty() || !pattern.matcher(link).matches()) {
            return Optional.empty();
        }
        return Optional.of(link.startsWith("http://") || link.startsWith("https://") ? link : "https://" + link);
    }

    /** As shipped: {@code discord.gg/...} or {@code discord.com/invite/...}, with or without {@code https://}. */
    public static final String DEFAULT_DISCORD_PATTERN =
            "^(https?://)?(www\\.)?(discord\\.gg|discord(app)?\\.com/invite)/[A-Za-z0-9-]{2,32}/?$";
}
