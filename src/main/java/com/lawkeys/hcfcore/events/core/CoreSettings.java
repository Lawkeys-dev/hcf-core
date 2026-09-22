package com.lawkeys.hcfcore.events.core;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The DTCs and Last Breaks of {@code events.yml} - one list, since they share one
 * engine ({@link CoreEventManager}).
 */
public record CoreSettings(boolean enabled, ZoneId timeZone, List<CoreEventDefinition> definitions) {

    public CoreSettings {
        Objects.requireNonNull(timeZone, "timeZone");
        definitions = List.copyOf(definitions);
    }

    public static CoreSettings defaults() {
        return new CoreSettings(true, ZoneId.systemDefault(), List.of());
    }

    public Optional<CoreEventDefinition> find(String id) {
        String wanted = id == null ? "" : id.toLowerCase(Locale.ROOT);
        return definitions.stream().filter(definition -> definition.id().toLowerCase(Locale.ROOT).equals(wanted))
                .findFirst();
    }
}
