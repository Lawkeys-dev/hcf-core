package com.lawkeys.hcfcore.phase;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The answer to a staff or player command: what to tell whoever typed it, and
 * whether the whole server should hear about it.
 *
 * @param messageKey  what to tell the sender, or {@code null} when the broadcast
 *                    says it all
 * @param broadcast   what everybody hears, or empty
 */
public record PhaseResult(boolean success, String messageKey, Map<String, String> placeholders,
                          Optional<PhaseUpdate> broadcast) {

    public PhaseResult {
        placeholders = Map.copyOf(Objects.requireNonNull(placeholders, "placeholders"));
        Objects.requireNonNull(broadcast, "broadcast");
    }

    static PhaseResult fail(String key, String... placeholders) {
        return new PhaseResult(false, key, pairs(placeholders), Optional.empty());
    }

    static PhaseResult told(String key, String... placeholders) {
        return new PhaseResult(true, key, pairs(placeholders), Optional.empty());
    }

    static PhaseResult announced(PhaseUpdate broadcast) {
        return new PhaseResult(true, null, Map.of(), Optional.of(broadcast));
    }

    static Map<String, String> pairs(String... placeholders) {
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException("placeholders must be key/value pairs");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < placeholders.length; i += 2) {
            map.put(placeholders[i], placeholders[i + 1]);
        }
        return map;
    }
}
