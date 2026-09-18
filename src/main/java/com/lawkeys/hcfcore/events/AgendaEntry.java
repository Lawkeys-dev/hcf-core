package com.lawkeys.hcfcore.events;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One line another module wants shown in the {@code /events} agenda.
 *
 * <p>It carries a language key and its placeholders rather than a finished
 * sentence, so the module contributing the line still owns its own wording and
 * its own translations (ARCHITECTURE.md section 10) while {@code events/} owns
 * the command.
 */
public record AgendaEntry(String messageKey, Map<String, String> placeholders) {

    public AgendaEntry {
        Objects.requireNonNull(messageKey, "messageKey");
        placeholders = Map.copyOf(Objects.requireNonNull(placeholders, "placeholders"));
    }

    /** @param placeholders alternating key and value, as elsewhere in the codebase */
    public static AgendaEntry of(String messageKey, String... placeholders) {
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException("placeholders must be key/value pairs");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < placeholders.length; i += 2) {
            map.put(placeholders[i], placeholders[i + 1]);
        }
        return new AgendaEntry(messageKey, map);
    }
}
