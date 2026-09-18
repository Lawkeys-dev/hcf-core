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
 * @param definitions every capture event - the KOTHs, and the Citadels' zones to hold
 * @param citadels    what makes some of those Citadels: their claim and its rules
 */
public record EventSettings(boolean enabled,
                            long tickSeconds,
                            ZoneId timeZone,
                            boolean announceContests,
                            boolean teamlessPlayersContest,
                            List<CaptureEventDefinition> definitions,
                            List<CitadelDefinition> citadels) {

    public EventSettings {
        Objects.requireNonNull(timeZone, "timeZone");
        definitions = List.copyOf(Objects.requireNonNull(definitions, "definitions"));
        citadels = List.copyOf(Objects.requireNonNull(citadels, "citadels"));
    }

    /** Settings without Citadels. */
    public EventSettings(boolean enabled, long tickSeconds, ZoneId timeZone, boolean announceContests,
                         boolean teamlessPlayersContest, List<CaptureEventDefinition> definitions) {
        this(enabled, tickSeconds, timeZone, announceContests, teamlessPlayersContest, definitions, List.of());
    }

    public static EventSettings defaults() {
        return new EventSettings(true, 1L, ZoneId.systemDefault(), true, true, List.of());
    }

    /** @return the Citadel whose claim belongs to the team of this name, if any */
    public Optional<CitadelDefinition> citadelClaimedBy(String teamName) {
        return citadels.stream().filter(citadel -> citadel.isClaimedBy(teamName)).findFirst();
    }

    /** @return the Citadel of this capture event, if it is one */
    public Optional<CitadelDefinition> citadel(String eventId) {
        return citadels.stream().filter(citadel -> citadel.eventId().equalsIgnoreCase(eventId)).findFirst();
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
