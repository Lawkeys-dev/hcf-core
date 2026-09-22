package com.lawkeys.hcfcore.events.setup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Which server teams are an event's territory, and what each event says of
 * abilities on it ({@code disable-abilities} in {@code events.yml}) - read by
 * {@code ability/} to refuse abilities on an event's land while it runs (the
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
    public record Declared(String eventId, String team, TerritoryAbilityMode disableAbilities) {

        public Declared {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(team, "team");
        }
    }

    /** Lower-cased team name to the events whose land it is. */
    private final Map<String, List<Declared>> byTeam;

    private EventTerritories(Map<String, List<Declared>> byTeam) {
        this.byTeam = Map.copyOf(byTeam);
    }

    public static EventTerritories none() {
        return new EventTerritories(Map.of());
    }

    public static EventTerritories of(List<Declared> declared) {
        Map<String, List<Declared>> byTeam = new HashMap<>();
        for (Declared event : declared) {
            byTeam.computeIfAbsent(event.team().trim().toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(event);
        }
        byTeam.replaceAll((team, events) -> List.copyOf(events));
        return new EventTerritories(byTeam);
    }

    /** @return whether this server team's land is some event's territory */
    public boolean isTerritory(String team) {
        return team != null && byTeam.containsKey(team.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Each event of the land says when abilities are refused on it - while it runs,
     * always, or never ({@link TerritoryAbilityMode}); several events may share one
     * team - a Totem and its Mini Totem, say - and then a refusal by any wins.
     *
     * @param byDefault {@code abilities.yml}'s {@code disabled-in.event-territory}, for
     *                  an event that says nothing
     * @param running   whether the event with this id runs now
     * @return whether abilities are refused on this team's land now
     */
    public boolean abilitiesRefused(String team, TerritoryAbilityMode byDefault, Predicate<String> running) {
        if (!isTerritory(team)) {
            return false;
        }
        for (Declared event : byTeam.get(team.trim().toLowerCase(Locale.ROOT))) {
            if (Optional.ofNullable(event.disableAbilities()).orElse(byDefault).refuses(running.test(event.eventId()))) {
                return true;
            }
        }
        return false;
    }
}
