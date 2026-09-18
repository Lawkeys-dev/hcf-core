package com.lawkeys.hcfcore.events.conquest;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The Conquests of {@code events.yml}, with the settings they share with the
 * capture events: the switch, the time zone and what a teamless player does.
 */
public record ConquestSettings(boolean enabled, ZoneId timeZone, boolean teamlessPlayersContest,
                               List<ConquestDefinition> definitions) {

    public ConquestSettings {
        Objects.requireNonNull(timeZone, "timeZone");
        definitions = List.copyOf(definitions);
    }

    public static ConquestSettings defaults() {
        return new ConquestSettings(true, ZoneId.systemDefault(), true, List.of());
    }

    public Optional<ConquestDefinition> find(String id) {
        String wanted = id == null ? "" : id.toLowerCase(Locale.ROOT);
        return definitions.stream().filter(definition -> definition.id().toLowerCase(Locale.ROOT).equals(wanted))
                .findFirst();
    }
}
