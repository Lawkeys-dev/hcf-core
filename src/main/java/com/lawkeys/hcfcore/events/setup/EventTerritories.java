package com.lawkeys.hcfcore.events.setup;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Which server teams are an event's territory, and what each event says of
 * abilities on it ({@code disable-abilities} in {@code events.yml}) - read by
 * {@code ability/} to refuse abilities on event land as on a safe zone (the
 * owner's request of 22/09/2026).
 *
 * <p>Pure Java: unit-tested without a server.
 */
public final class EventTerritories {

    /**
     * One event's claim on a team.
     *
     * @param disableAbilities the event's own say, or {@code null} to follow
     *                         {@code abilities.yml}
     */
    public record Declared(String eventId, String team, Boolean disableAbilities) {

        public Declared {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(team, "team");
        }
    }

    /** Lower-cased team name to what its events say: empty when none says anything. */
    private final Map<String, Optional<Boolean>> byTeam;

    private EventTerritories(Map<String, Optional<Boolean>> byTeam) {
        this.byTeam = Map.copyOf(byTeam);
    }

    public static EventTerritories none() {
        return new EventTerritories(Map.of());
    }

    /**
     * Several events may share one team - a Totem and its Mini Totem, say. Then an
     * event refusing abilities wins over one allowing them: the land is one land.
     */
    public static EventTerritories of(List<Declared> declared) {
        Map<String, Optional<Boolean>> byTeam = new HashMap<>();
        for (Declared event : declared) {
            String key = event.team().trim().toLowerCase(Locale.ROOT);
            Optional<Boolean> before = byTeam.getOrDefault(key, Optional.empty());
            Optional<Boolean> said = Optional.ofNullable(event.disableAbilities());
            byTeam.put(key, before.isPresent() && said.isPresent()
                    ? Optional.of(before.get() || said.get())
                    : before.or(() -> said));
        }
        return new EventTerritories(byTeam);
    }

    /** @return whether this server team's land is some event's territory */
    public boolean isTerritory(String team) {
        return team != null && byTeam.containsKey(team.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * @param byDefault {@code abilities.yml}'s {@code disabled-in.event-territory}
     * @return whether abilities are refused on this team's land: never off event
     *         land, else the events' own say, else the default
     */
    public boolean abilitiesRefused(String team, boolean byDefault) {
        if (!isTerritory(team)) {
            return false;
        }
        return byTeam.get(team.trim().toLowerCase(Locale.ROOT)).orElse(byDefault);
    }
}
