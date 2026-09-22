package com.lawkeys.hcfcore.events.setup;

import java.util.Locale;
import java.util.Optional;

/**
 * When abilities are refused on an event's territory ({@code abilities.yml},
 * {@code disabled-in.event-territory}, and an event's own {@code disable-abilities})
 * - the owner's choice of 22/09/2026, between the two.
 *
 * <p>Pure Java: unit-tested.
 */
public enum TerritoryAbilityMode {

    /** Abilities work on the land, running or not. */
    NEVER,
    /** Refused on the land for as long as the event runs; ordinary server land between runs. */
    DURING_EVENT,
    /** Refused on the land at all times, as on a safe zone. */
    ALWAYS;

    /** @return the id written in the configuration: {@code never}, {@code during-event}, {@code always} */
    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * @return the mode a configuration value names: one of the three ids, or a
     *         boolean - {@code true} as {@code during-event}, {@code false} as
     *         {@code never}, what the two meant before the choice existed; empty
     *         for anything else
     */
    public static Optional<TerritoryAbilityMode> parse(Object raw) {
        if (raw instanceof Boolean value) {
            return Optional.of(value ? DURING_EVENT : NEVER);
        }
        if (raw == null) {
            return Optional.empty();
        }
        String typed = raw.toString().trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (TerritoryAbilityMode mode : values()) {
            if (mode.id().equals(typed)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }

    /** @return whether abilities are refused now, the event running or not */
    public boolean refuses(boolean running) {
        return this == ALWAYS || (this == DURING_EVENT && running);
    }
}
