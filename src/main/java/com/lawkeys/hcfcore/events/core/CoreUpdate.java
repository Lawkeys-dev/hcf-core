package com.lawkeys.hcfcore.events.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Something about a DTC or Last Break the server should hear - modelled on
 * {@code ConquestUpdate}.
 *
 * @param teamId the team it is about, or {@code null}
 */
public record CoreUpdate(Type type, String messageKey, UUID teamId, Map<String, String> placeholders) {

    public enum Type {
        STARTED,
        /** A remaining-count mark was crossed - common health, or one team's own count. */
        MILESTONE,
        WON,
        /** The hard stop came first: no winner. */
        EXPIRED,
        STOPPED
    }

    public CoreUpdate {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(messageKey, "messageKey");
        placeholders = Map.copyOf(placeholders);
    }

    static CoreUpdate of(Type type, String messageKey, UUID teamId, String... pairs) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            placeholders.put(pairs[i], pairs[i + 1]);
        }
        return new CoreUpdate(type, messageKey, teamId, placeholders);
    }
}
