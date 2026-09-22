package com.lawkeys.hcfcore.events.totem;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Something about a Totem the server should hear, and how the column must now look.
 *
 * @param teamId the team it is about, or {@code null}
 */
public record TotemUpdate(Type type, String messageKey, UUID teamId, Map<String, String> placeholders) {

    public enum Type {
        STARTED,
        /** A block broken, the team one step closer. */
        BROKEN,
        /** Another team broke a block: the totem starts over. */
        RESET,
        WON,
        /** The hard stop came first: no winner. */
        EXPIRED,
        STOPPED
    }

    public TotemUpdate {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(messageKey, "messageKey");
        placeholders = Map.copyOf(placeholders);
    }

    static TotemUpdate of(Type type, String messageKey, UUID teamId, String... pairs) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            placeholders.put(pairs[i], pairs[i + 1]);
        }
        return new TotemUpdate(type, messageKey, teamId, placeholders);
    }
}
