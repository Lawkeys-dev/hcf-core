package com.lawkeys.hcfcore.events.conquest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Something about a Conquest the server should hear.
 *
 * @param teamId the team it is about, or {@code null}
 */
public record ConquestUpdate(Type type, String messageKey, UUID teamId, Map<String, String> placeholders) {

    public enum Type {
        STARTED,
        /** A team held a zone long enough and scored from it. */
        ZONE_CAPTURED,
        /** A member of a team with points died. */
        POINTS_LOST,
        WON,
        /** The hard stop came first: no winner. */
        EXPIRED,
        STOPPED
    }

    public ConquestUpdate {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(messageKey, "messageKey");
        placeholders = Map.copyOf(placeholders);
    }

    static ConquestUpdate of(Type type, String messageKey, UUID teamId, String... pairs) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            placeholders.put(pairs[i], pairs[i + 1]);
        }
        return new ConquestUpdate(type, messageKey, teamId, placeholders);
    }
}
