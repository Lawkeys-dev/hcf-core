package com.lawkeys.hcfcore.events.totem;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** The Totems and Mini Totems of {@code events.yml}. */
public record TotemSettings(boolean enabled, ZoneId timeZone, List<TotemDefinition> definitions) {

    public TotemSettings {
        Objects.requireNonNull(timeZone, "timeZone");
        definitions = List.copyOf(definitions);
    }

    public static TotemSettings defaults() {
        return new TotemSettings(true, ZoneId.systemDefault(), List.of());
    }

    public Optional<TotemDefinition> find(String id) {
        String wanted = id == null ? "" : id.toLowerCase(Locale.ROOT);
        return definitions.stream().filter(definition -> definition.id().toLowerCase(Locale.ROOT).equals(wanted))
                .findFirst();
    }

    /** @return the Totem whose column that block belongs to, running or not */
    public Optional<TotemDefinition> columnAt(String world, int x, int y, int z) {
        return definitions.stream().filter(definition -> definition.levelOf(world, x, y, z) >= 0).findFirst();
    }
}
