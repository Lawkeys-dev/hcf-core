package com.lawkeys.hcfcore.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Fired when the stand-in a combat-tagged player left behind is killed: the player
 * dies though they are not there. Their items have fallen and their deathban is set;
 * what a death costs a team and earns a killer - DTR, points, statistics, money - is
 * counted by whoever listens, as for a {@code PlayerDeathEvent}.
 *
 * <p>Not cancellable. Main thread.
 */
public class CombatLoggerDeathEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID victimId;
    private final String victimName;
    private final Player killer;
    private final Location location;

    public CombatLoggerDeathEvent(UUID victimId, String victimName, Player killer, Location location) {
        this.victimId = Objects.requireNonNull(victimId, "victimId");
        this.victimName = Objects.requireNonNull(victimName, "victimName");
        this.killer = killer;
        this.location = Objects.requireNonNull(location, "location");
    }

    /** @return the player who logged out, and has now died */
    public UUID getVictimId() {
        return victimId;
    }

    public String getVictimName() {
        return victimName;
    }

    /** @return who killed the stand-in; empty when it died another way */
    public Optional<Player> getKiller() {
        return Optional.ofNullable(killer);
    }

    /** @return where the stand-in fell - where the items did */
    public Location getLocation() {
        return location.clone();
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
