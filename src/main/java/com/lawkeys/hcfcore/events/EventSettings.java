package com.lawkeys.hcfcore.events;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable snapshot of {@code events.yml}.
 *
 * <p>The event list is data, not code (ARCHITECTURE.md section 9): a new KOTH
 * variant is a YAML block, and {@code /hcf reload} picks it up.
 *
 * @param tickSeconds how often the server layer samples who is standing in a zone
 * @param timeZone    the zone the {@code schedule} times are read in - explicit
 *                    rather than implicit, so a host in another region does not
 *                    silently shift every event
 * @param teamlessPlayersContest whether a player with no team freezes a capture by
 *                    standing in the zone. True reads them as everybody's enemy,
 *                    which is the usual HCF behaviour; false lets teams cap around
 *                    them
 */
public record EventSettings(boolean enabled,
                            long tickSeconds,
                            ZoneId timeZone,
                            boolean announceContests,
                            boolean teamlessPlayersContest,
                            List<CaptureEventDefinition> definitions) {

    public EventSettings {
        Objects.requireNonNull(timeZone, "timeZone");
        definitions = List.copyOf(Objects.requireNonNull(definitions, "definitions"));
    }

    public static EventSettings defaults() {
        return new EventSettings(true, 1L, ZoneId.systemDefault(), true, true, List.of());
    }

    public Optional<CaptureEventDefinition> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (CaptureEventDefinition definition : definitions) {
            if (definition.id().equalsIgnoreCase(id)) {
                return Optional.of(definition);
            }
        }
        return Optional.empty();
    }
}
