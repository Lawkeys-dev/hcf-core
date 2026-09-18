package com.lawkeys.hcfcore.api.event;

import com.lawkeys.hcfcore.team.Team;
import org.bukkit.event.Event;

import java.util.Objects;

/**
 * Base class for every event concerning a {@link Team}.
 *
 * <p>All team events are fired on the main server thread, since the team module
 * only mutates state from command and listener callbacks.
 */
public abstract class TeamEvent extends Event {

    private final Team team;

    protected TeamEvent(Team team) {
        this.team = Objects.requireNonNull(team, "team");
    }

    public Team getTeam() {
        return team;
    }
}
