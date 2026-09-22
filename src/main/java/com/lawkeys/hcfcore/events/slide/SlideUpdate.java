package com.lawkeys.hcfcore.events.slide;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Something about a Slide the server should hear - modelled on {@code ConquestUpdate}. */
public record SlideUpdate(Type type, String messageKey, UUID teamId, Map<String, String> placeholders) {

    public enum Type {
        STARTED,
        /** A team's points crossed one of the configured marks. */
        MILESTONE,
        /** A member of a team with points died. */
        POINTS_LOST,
        WON,
        /** The hard stop came first: no winner. */
        EXPIRED,
        STOPPED
    }

    public SlideUpdate {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(messageKey, "messageKey");
        placeholders = Map.copyOf(placeholders);
    }

    static SlideUpdate of(Type type, String messageKey, UUID teamId, String... pairs) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            placeholders.put(pairs[i], pairs[i + 1]);
        }
        return new SlideUpdate(type, messageKey, teamId, placeholders);
    }
}
