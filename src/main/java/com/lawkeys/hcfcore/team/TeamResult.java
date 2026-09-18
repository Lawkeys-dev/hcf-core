package com.lawkeys.hcfcore.team;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of a {@link TeamManager} operation.
 *
 * <p>The manager never talks to a player: it reports what happened as a
 * language key plus placeholders (ARCHITECTURE.md section 10), and the command
 * layer turns that into a message. This keeps the whole rule engine free of the
 * server API and therefore unit-testable (CONTRIBUTING.md section 3).
 */
public final class TeamResult {

    private final boolean success;
    private final String messageKey;
    private final Map<String, String> placeholders;
    private final Team team;

    private TeamResult(boolean success, String messageKey, Map<String, String> placeholders, Team team) {
        this.success = success;
        this.messageKey = Objects.requireNonNull(messageKey, "messageKey");
        this.placeholders = Map.copyOf(placeholders);
        this.team = team;
    }

    /**
     * @param placeholders alternating placeholder name and value, e.g.
     *                     {@code "team", "Wizards", "player", "Notch"}
     */
    public static TeamResult ok(String messageKey, Team team, String... placeholders) {
        return new TeamResult(true, messageKey, toMap(placeholders), team);
    }

    public static TeamResult fail(String messageKey, String... placeholders) {
        return new TeamResult(false, messageKey, toMap(placeholders), null);
    }

    private static Map<String, String> toMap(String... placeholders) {
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException("placeholders must be name/value pairs, got " + placeholders.length);
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < placeholders.length; i += 2) {
            map.put(placeholders[i], placeholders[i + 1]);
        }
        return map;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isFailure() {
        return !success;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Map<String, String> getPlaceholders() {
        return placeholders;
    }

    /** @return the team the operation acted on, present only on success. */
    public Optional<Team> getTeam() {
        return Optional.ofNullable(team);
    }

    @Override
    public String toString() {
        return (success ? "ok(" : "fail(") + messageKey + placeholders + ')';
    }
}
