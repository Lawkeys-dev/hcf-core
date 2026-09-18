package com.lawkeys.hcfcore.phase;

import java.util.Map;
import java.util.Objects;

/** A change of phase the whole server should hear about. */
public record PhaseUpdate(Type type, String messageKey, Map<String, String> placeholders) {

    public enum Type {
        SOTW_STARTED, SOTW_PROGRESS, SOTW_ENDED, SOTW_STOPPED, EOTW_STARTED, EOTW_STOPPED,
        PURGE_STARTED, PURGE_ENDED, PURGE_STOPPED
    }

    public PhaseUpdate {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(messageKey, "messageKey");
        placeholders = Map.copyOf(Objects.requireNonNull(placeholders, "placeholders"));
    }

    static PhaseUpdate of(Type type, String messageKey, String... placeholders) {
        return new PhaseUpdate(type, messageKey, PhaseResult.pairs(placeholders));
    }
}
