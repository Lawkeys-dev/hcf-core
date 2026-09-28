package com.lawkeys.hcfcore.api.event;

import com.lawkeys.hcfcore.team.Team;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Fired when an event is won: a KOTH or Citadel captured, a Conquest, DTC, Last
 * Break, Slide, Totem or Mini Totem won, Kill the King won by its killer or its
 * King. Not cancellable: it reports what the event engine has already decided, after
 * the points and the rewards.
 *
 * <p>Main thread. Nothing fires for an event stopped, expired or called off.
 */
public class EventWonEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String eventId;
    private final String displayName;
    private final String kind;
    private final Team team;
    private final UUID player;

    /**
     * @param displayName the event's name as configured, colour codes included
     * @param kind        {@code koth}, {@code citadel}, {@code conquest}, {@code dtc},
     *                    {@code lastbreak}, {@code slide}, {@code totem},
     *                    {@code minitotem} or {@code ktk}
     * @param team        the winning team; {@code null} for a solo King's winner with none
     * @param player      the winning player, for Kill the King; {@code null} for a team win
     */
    public EventWonEvent(String eventId, String displayName, String kind, Team team, UUID player) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.team = team;
        this.player = player;
    }

    /** Fires one, on the main thread. */
    public static void fire(String eventId, String displayName, String kind, Team team, UUID player) {
        Bukkit.getPluginManager().callEvent(new EventWonEvent(eventId, displayName, kind, team, player));
    }

    public String getEventId() {
        return eventId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getKind() {
        return kind;
    }

    public Optional<Team> getTeam() {
        return Optional.ofNullable(team);
    }

    /** @return the winning player, for Kill the King; empty for a team's capture */
    public Optional<UUID> getPlayer() {
        return Optional.ofNullable(player);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
