package com.lawkeys.hcfcore.events.slide;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** The Slides of {@code events.yml}. */
public record SlideSettings(boolean enabled, ZoneId timeZone, List<SlideDefinition> definitions) {

    public SlideSettings {
        Objects.requireNonNull(timeZone, "timeZone");
        definitions = List.copyOf(definitions);
    }

    public static SlideSettings defaults() {
        return new SlideSettings(true, ZoneId.systemDefault(), List.of());
    }

    public Optional<SlideDefinition> find(String id) {
        String wanted = id == null ? "" : id.toLowerCase(Locale.ROOT);
        return definitions.stream().filter(definition -> definition.id().toLowerCase(Locale.ROOT).equals(wanted))
                .findFirst();
    }
}
