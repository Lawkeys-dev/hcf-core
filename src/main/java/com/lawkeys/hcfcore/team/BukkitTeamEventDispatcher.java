package com.lawkeys.hcfcore.team;

import com.lawkeys.hcfcore.api.event.TeamAllianceChangeEvent;
import com.lawkeys.hcfcore.api.event.TeamCreateEvent;
import com.lawkeys.hcfcore.api.event.TeamDisbandEvent;
import com.lawkeys.hcfcore.api.event.TeamMemberJoinEvent;
import com.lawkeys.hcfcore.api.event.TeamMemberLeaveEvent;
import com.lawkeys.hcfcore.api.event.TeamRenameEvent;
import com.lawkeys.hcfcore.api.event.TeamRoleChangeEvent;
import org.bukkit.plugin.PluginManager;

import java.util.Objects;
import java.util.UUID;

/**
 * Publishes {@link TeamManager} state changes as Bukkit events
 * (ARCHITECTURE.md section 7).
 *
 * <p>This is the only place where the team module's rule engine meets the server
 * event system - which is why the engine itself takes a
 * {@link TeamEventDispatcher} instead.
 */
public final class BukkitTeamEventDispatcher implements TeamEventDispatcher {

    private final PluginManager pluginManager;

    public BukkitTeamEventDispatcher(PluginManager pluginManager) {
        this.pluginManager = Objects.requireNonNull(pluginManager, "pluginManager");
    }

    @Override
    public boolean callTeamCreate(Team team, UUID creator) {
        TeamCreateEvent event = new TeamCreateEvent(team, creator);
        pluginManager.callEvent(event);
        return !event.isCancelled();
    }

    @Override
    public boolean callTeamDisband(Team team, UUID actor) {
        TeamDisbandEvent event = new TeamDisbandEvent(team, actor);
        pluginManager.callEvent(event);
        return !event.isCancelled();
    }

    @Override
    public boolean callTeamRename(Team team, String newName, UUID actor) {
        TeamRenameEvent event = new TeamRenameEvent(team, newName, actor);
        pluginManager.callEvent(event);
        return !event.isCancelled();
    }

    @Override
    public void callPlayerJoinTeam(Team team, UUID player) {
        pluginManager.callEvent(new TeamMemberJoinEvent(team, player));
    }

    @Override
    public void callPlayerLeaveTeam(Team team, UUID player, LeaveCause cause) {
        pluginManager.callEvent(new TeamMemberLeaveEvent(team, player, cause));
    }

    @Override
    public void callRoleChange(Team team, UUID player, TeamRole previousRole, TeamRole newRole) {
        pluginManager.callEvent(new TeamRoleChangeEvent(team, player, previousRole, newRole));
    }

    @Override
    public void callAllianceChange(Team a, Team b, boolean allied) {
        pluginManager.callEvent(new TeamAllianceChangeEvent(a, b, allied));
    }
}
