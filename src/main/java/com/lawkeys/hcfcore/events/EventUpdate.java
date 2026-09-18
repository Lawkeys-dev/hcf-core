package com.lawkeys.hcfcore.events;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Something that happened to an event during a tick, ready to be rendered.
 *
 * <p>The rule engine returns these instead of broadcasting anything itself: it
 * has no server to broadcast to. The server layer turns each one into a message
 * through {@code LangManager} and, for {@link Type#CAPTURED}, into the reward
 * side effects. Carrying a language key rather than a sentence keeps the "no
 * hardcoded text" rule of ARCHITECTURE.md section 10 intact all the way down.
 *
 * @param teamId the team the update is about, or {@code null} when it is about
 *               nobody in particular (the event starting, or expiring unwon)
 */
public record EventUpdate(Type type,
                          String eventId,
                          UUID teamId,
                          String messageKey,
                          Map<String, String> placeholders) {

    public enum Type {
        /** The event just opened. */
        STARTED,
        /** A team took sole control and the countdown is running. */
        CAPTURE_BEGAN,
        /** Two or more parties are in the zone, so the countdown is not advancing. */
        CONTESTED,
        /** The zone emptied out. */
        CONTROL_LOST,
        /** A remaining-time milestone worth announcing. */
        PROGRESS,
        /** Somebody won. This is the one with side effects. */
        CAPTURED,
        /** The hard time limit ran out with no winner. */
        EXPIRED,
        /** Stopped by staff. */
        STOPPED
    }

    public EventUpdate {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(messageKey, "messageKey");
        placeholders = Map.copyOf(Objects.requireNonNull(placeholders, "placeholders"));
    }

    /** @param placeholders alternating key and value, as elsewhere in the codebase */
    public static EventUpdate of(Type type, String eventId, UUID teamId, String messageKey,
                                 String... placeholders) {
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException("placeholders must be key/value pairs");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < placeholders.length; i += 2) {
            map.put(placeholders[i], placeholders[i + 1]);
        }
        return new EventUpdate(type, eventId, teamId, messageKey, map);
    }
}
