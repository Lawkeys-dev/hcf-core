package com.lawkeys.hcfcore.team;

import java.util.UUID;

/**
 * Seam through which {@link TeamManager} announces state changes to the rest of
 * the server, without depending on the Bukkit event system itself.
 *
 * <p>ARCHITECTURE.md section 7 asks for custom events so other modules (and
 * third-party plugins) can extend the core without touching it. The Bukkit
 * implementation is {@code BukkitTeamEventDispatcher}; tests use {@link #NO_OP}.
 *
 * <p>Methods returning {@code boolean} correspond to cancellable events: they are
 * called <em>before</em> the change is applied and returning {@code false} aborts
 * it. Since Bukkit events must be fired on the main thread, every mutating
 * {@code TeamManager} call is main-thread-only.
 */
public interface TeamEventDispatcher {

    /** @return {@code false} to cancel the creation. */
    boolean callTeamCreate(Team team, UUID creator);

    /** @return {@code false} to cancel the disband. */
    boolean callTeamDisband(Team team, UUID actor);

    /** @return {@code false} to cancel the rename. */
    boolean callTeamRename(Team team, String newName, UUID actor);

    void callPlayerJoinTeam(Team team, UUID player);

    void callPlayerLeaveTeam(Team team, UUID player, LeaveCause cause);

    void callRoleChange(Team team, UUID player, TeamRole previousRole, TeamRole newRole);

    void callAllianceChange(Team a, Team b, boolean allied);

    /** Why a player stopped being a member. */
    enum LeaveCause {
        LEAVE,
        KICK,
        DISBAND,
        FORCED
    }

    TeamEventDispatcher NO_OP = new TeamEventDispatcher() {
        @Override
        public boolean callTeamCreate(Team team, UUID creator) {
            return true;
        }

        @Override
        public boolean callTeamDisband(Team team, UUID actor) {
            return true;
        }

        @Override
        public boolean callTeamRename(Team team, String newName, UUID actor) {
            return true;
        }

        @Override
        public void callPlayerJoinTeam(Team team, UUID player) {
        }

        @Override
        public void callPlayerLeaveTeam(Team team, UUID player, LeaveCause cause) {
        }

        @Override
        public void callRoleChange(Team team, UUID player, TeamRole previousRole, TeamRole newRole) {
        }

        @Override
        public void callAllianceChange(Team a, Team b, boolean allied) {
        }
    };
}
