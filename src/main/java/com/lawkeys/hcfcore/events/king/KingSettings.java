package com.lawkeys.hcfcore.events.king;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable snapshot of the Kill the King half of {@code events.yml}.
 *
 * @param enabled  the file's own {@code enabled}: one switch for every event
 * @param timeZone the zone the schedules are read in, shared with the captures
 */
public record KingSettings(boolean enabled, ZoneId timeZone, List<KingEventDefinition> definitions) {

    public KingSettings {
        Objects.requireNonNull(timeZone, "timeZone");
        definitions = List.copyOf(Objects.requireNonNull(definitions, "definitions"));
    }

    public static KingSettings defaults() {
        return new KingSettings(true, ZoneId.systemDefault(), List.of());
    }

    public Optional<KingEventDefinition> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (KingEventDefinition definition : definitions) {
            if (definition.id().equalsIgnoreCase(id)) {
                return Optional.of(definition);
            }
        }
        return Optional.empty();
    }
}
