package com.lawkeys.hcfcore.team;

import com.lawkeys.hcfcore.util.WorldPosition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The team module's single entry point and its rule engine.
 *
 * <p><strong>No server API.</strong> This class is deliberately pure Java: it
 * holds the in-memory cache that ARCHITECTURE.md section 3 designates as the
 * runtime source of truth, applies every rule, and reports outcomes as
 * {@link TeamResult} language keys. Commands and listeners are thin adapters on
 * top (CONTRIBUTING.md section 3), and the whole rule set is unit-testable without a
 * running server (CONTRIBUTING.md section 3, "Tests").
 *
 * <p><strong>Staff overrides.</strong> Every operation that normally requires a
 * rank takes an {@code actor} uuid. Passing {@code null} means "performed by
 * staff or console": role and membership checks are bypassed. That is what backs
 * the {@code forcedisband}/{@code forcekick}/{@code forcepromote} family from
 * FEATURES.md section 1, without duplicating each operation.
 *
 * <p><strong>Threading.</strong> Mutating methods fire cancellable events and so
 * must be called from the main server thread. {@link #flush()} and
 * {@link #loadAll()} do I/O and must be called from an async task. The caches are
 * concurrent so the async writer can read them safely while the main thread
 * mutates.
 */
public final class TeamManager {

    private final Supplier<TeamSettings> settings;
    private final TeamStore store;
    private final TeamEventDispatcher events;
    private final LongSupplier clock;

    private final Map<UUID, Team> teamsById = new ConcurrentHashMap<>();
    /** Lowercase name to team id, so name lookups and uniqueness are case-insensitive. */
    private final Map<String, UUID> teamIdsByName = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> teamIdByPlayer = new ConcurrentHashMap<>();

    /** player -> (team id -> the invitation). */
    private final Map<UUID, Map<UUID, Invite>> invites = new ConcurrentHashMap<>();
    private final Map<UUID, ChatChannel> chatChannels = new ConcurrentHashMap<>();

    /** Ids awaiting deletion by the next flush; kept separate since the Team object is already gone. */
    private final Set<UUID> pendingDeletions = ConcurrentHashMap.newKeySet();

    public TeamManager(Supplier<TeamSettings> settings, TeamStore store, TeamEventDispatcher events) {
        this(settings, store, events, System::currentTimeMillis);
    }

    /** @param clock epoch-millis source; injectable so expiry logic can be tested deterministically */
    public TeamManager(Supplier<TeamSettings> settings, TeamStore store, TeamEventDispatcher events, LongSupplier clock) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.store = Objects.requireNonNull(store, "store");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private TeamSettings config() {
        return settings.get();
    }

    // ------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------

    public Optional<Team> getTeam(UUID teamId) {
        return teamId == null ? Optional.empty() : Optional.ofNullable(teamsById.get(teamId));
    }

    public Optional<Team> getTeamByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        UUID id = teamIdsByName.get(normalizeName(name));
        return id == null ? Optional.empty() : Optional.ofNullable(teamsById.get(id));
    }

    public Optional<Team> getTeamOf(UUID player) {
        if (player == null) {
            return Optional.empty();
        }
        UUID id = teamIdByPlayer.get(player);
        return id == null ? Optional.empty() : Optional.ofNullable(teamsById.get(id));
    }

    public boolean hasTeam(UUID player) {
        return teamIdByPlayer.containsKey(player);
    }

    /** @return an immutable snapshot of every cached team, player and system alike. */
    public List<Team> getTeams() {
        return List.copyOf(teamsById.values());
    }

    public int getTeamCount() {
        return teamsById.size();
    }

    /**
     * Team ranking (FEATURES.md section 1, "Classement de teams"). System teams are
     * excluded: they are server-owned and would pollute a player leaderboard.
     *
     * @param limit maximum entries; {@code 0} returns every player team
     */
    public List<Team> getTopTeamsByPoints(int limit) {
        List<Team> ranked = new ArrayList<>();
        for (Team team : teamsById.values()) {
            if (!team.getType().isSystem()) {
                ranked.add(team);
            }
        }
        ranked.sort(Comparator.comparingLong(Team::getPoints).reversed()
                .thenComparing(Team::getName, String.CASE_INSENSITIVE_ORDER));
        return limit > 0 && ranked.size() > limit ? List.copyOf(ranked.subList(0, limit)) : List.copyOf(ranked);
    }

    /** @return how {@code other} relates to {@code viewer}; either may be {@code null} for "no team". */
    public TeamRelation getRelation(Team viewer, Team other) {
        if (viewer == null || other == null) {
            return TeamRelation.NEUTRAL;
        }
        if (viewer.getId().equals(other.getId())) {
            return TeamRelation.SELF;
        }
        if (other.getType().isSystem()) {
            return TeamRelation.SYSTEM;
        }
        return viewer.isAlliedWith(other.getId()) ? TeamRelation.ALLY : TeamRelation.ENEMY;
    }

    public TeamRelation getRelation(UUID viewer, UUID target) {
        return getRelation(getTeamOf(viewer).orElse(null), getTeamOf(target).orElse(null));
    }

    // ------------------------------------------------------------------
    // Name validation
    // ------------------------------------------------------------------

    private static String normalizeName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /**
     * Validates a candidate team name against {@code teams.yml} and the names
     * already in use.
     *
     * @param currentTeam team being renamed, so it does not collide with itself;
     *                    {@code null} when creating
     * @return a failing {@link TeamResult}, or empty when the name is acceptable
     */
    public Optional<TeamResult> validateName(String name, Team currentTeam, boolean applyBlacklist) {
        TeamSettings.NameRules rules = config().names();
        String trimmed = name == null ? "" : name.trim();

        if (trimmed.length() < rules.minLength()) {
            return Optional.of(TeamResult.fail(TeamMessages.NAME_TOO_SHORT,
                    "min", String.valueOf(rules.minLength()), "name", trimmed));
        }
        if (trimmed.length() > rules.maxLength()) {
            return Optional.of(TeamResult.fail(TeamMessages.NAME_TOO_LONG,
                    "max", String.valueOf(rules.maxLength()), "name", trimmed));
        }
        if (!rules.pattern().matcher(trimmed).matches()) {
            return Optional.of(TeamResult.fail(TeamMessages.NAME_INVALID_CHARACTERS, "name", trimmed));
        }
        if (applyBlacklist && rules.blacklist().contains(normalizeName(trimmed))) {
            return Optional.of(TeamResult.fail(TeamMessages.NAME_BLACKLISTED, "name", trimmed));
        }

        UUID existing = teamIdsByName.get(normalizeName(trimmed));
        if (existing != null && (currentTeam == null || !existing.equals(currentTeam.getId()))) {
            return Optional.of(TeamResult.fail(TeamMessages.NAME_TAKEN, "name", trimmed));
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // Role checks
    // ------------------------------------------------------------------

    /**
     * @param actor {@code null} for a staff/console override
     * @return a failing result when {@code actor} may not perform {@code action} in
     *         {@code team}, or empty when allowed
     */
    private Optional<TeamResult> checkRole(Team team, UUID actor, TeamAction action) {
        if (actor == null) {
            return Optional.empty();
        }
        Optional<TeamRole> role = team.getRole(actor);
        if (role.isEmpty()) {
            return Optional.of(TeamResult.fail(TeamMessages.NOT_A_MEMBER, "team", team.getName()));
        }
        TeamRole required = config().requiredRole(action);
        if (!role.get().isAtLeast(required)) {
            return Optional.of(TeamResult.fail(TeamMessages.INSUFFICIENT_ROLE,
                    "required", required.name(), "role", role.get().name()));
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // Creation / disband / rename
    // ------------------------------------------------------------------

    /** Creates a player team led by {@code leader}. */
    public TeamResult createTeam(UUID leader, String name) {
        Objects.requireNonNull(leader, "leader");
        if (hasTeam(leader)) {
            return TeamResult.fail(TeamMessages.CREATE_ALREADY_IN_TEAM);
        }
        Optional<TeamResult> invalid = validateName(name, null, true);
        if (invalid.isPresent()) {
            return invalid.get();
        }

        String finalName = name.trim();
        Team team = new Team(UUID.randomUUID(), finalName, TeamType.PLAYER, leader, clock.getAsLong());
        team.setPoints(config().points().starting());

        if (!events.callTeamCreate(team, leader)) {
            return TeamResult.fail(TeamMessages.CREATE_CANCELLED, "name", finalName);
        }

        register(team);
        teamIdByPlayer.put(leader, team.getId());
        invites.remove(leader);
        team.markDirty();

        events.callPlayerJoinTeam(team, leader);
        return TeamResult.ok(TeamMessages.CREATE_SUCCESS, team, "team", finalName);
    }

    /** Creates a system team that is a safe zone - what a system team always was. */
    public TeamResult createSystemTeam(String name) {
        return createSystemTeam(name, SystemZone.SAFE);
    }

    /**
     * Creates a server-owned team (spawn, warzone, event area - FEATURES.md
     * section 1, "System team"). Staff-created, so the name blacklist does not
     * apply, but length/charset/uniqueness still do.
     *
     * @param zone whether its land is a safe zone (spawn) or a combat zone
     *             (warzone, roads, event grounds)
     */
    public TeamResult createSystemTeam(String name, SystemZone zone) {
        Objects.requireNonNull(zone, "zone");
        Optional<TeamResult> invalid = validateName(name, null, false);
        if (invalid.isPresent()) {
            return invalid.get();
        }
        String finalName = name.trim();
        Team team = new Team(UUID.randomUUID(), finalName, TeamType.SYSTEM, null, clock.getAsLong());
        team.setSystemZone(zone);

        if (!events.callTeamCreate(team, null)) {
            return TeamResult.fail(TeamMessages.CREATE_CANCELLED, "name", finalName);
        }
        register(team);
        team.markDirty();
        return TeamResult.ok(zone == SystemZone.SAFE
                        ? TeamMessages.SYSTEM_CREATED_SAFE : TeamMessages.SYSTEM_CREATED_COMBAT,
                team, "team", finalName);
    }

    /**
     * Turns a system team's land into a safe zone or a combat zone. Staff only,
     * which is why there is no actor: nothing about a system team belongs to a
     * player.
     */
    public TeamResult setSystemZone(Team team, SystemZone zone) {
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(zone, "zone");
        if (!team.getType().isSystem()) {
            return TeamResult.fail(TeamMessages.NOT_SYSTEM_TEAM, "team", team.getName());
        }
        team.setSystemZone(zone);
        return TeamResult.ok(zone == SystemZone.SAFE
                        ? TeamMessages.SYSTEM_ZONE_SAFE : TeamMessages.SYSTEM_ZONE_COMBAT,
                team, "team", team.getName());
    }

    /** @param actor {@code null} for {@code forcedisband} */
    public TeamResult disband(Team team, UUID actor) {
        Objects.requireNonNull(team, "team");
        if (team.getType().isSystem() && actor != null) {
            return TeamResult.fail(TeamMessages.SYSTEM_TEAM_IMMUTABLE, "team", team.getName());
        }
        Optional<TeamResult> denied = checkRole(team, actor, TeamAction.DISBAND);
        if (denied.isPresent()) {
            return denied.get();
        }
        if (!events.callTeamDisband(team, actor)) {
            return TeamResult.fail(TeamMessages.DISBAND_CANCELLED, "team", team.getName());
        }

        String name = team.getName();
        for (UUID member : team.getMemberIds()) {
            teamIdByPlayer.remove(member, team.getId());
            chatChannels.remove(member);
            events.callPlayerLeaveTeam(team, member, TeamEventDispatcher.LeaveCause.DISBAND);
        }
        unregister(team);
        return TeamResult.ok(TeamMessages.DISBAND_SUCCESS, team, "team", name);
    }

    public TeamResult rename(Team team, UUID actor, String newName) {
        Objects.requireNonNull(team, "team");
        if (team.getType().isSystem() && actor != null) {
            return TeamResult.fail(TeamMessages.SYSTEM_TEAM_IMMUTABLE, "team", team.getName());
        }
        Optional<TeamResult> denied = checkRole(team, actor, TeamAction.RENAME);
        if (denied.isPresent()) {
            return denied.get();
        }
        Optional<TeamResult> invalid = validateName(newName, team, actor != null);
        if (invalid.isPresent()) {
            return invalid.get();
        }

        String finalName = newName.trim();
        String previous = team.getName();
        if (previous.equals(finalName)) {
            return TeamResult.fail(TeamMessages.RENAME_SAME_NAME, "name", finalName);
        }
        if (!events.callTeamRename(team, finalName, actor)) {
            return TeamResult.fail(TeamMessages.RENAME_CANCELLED, "team", previous);
        }

        teamIdsByName.remove(normalizeName(previous));
        team.setName(finalName);
        teamIdsByName.put(normalizeName(finalName), team.getId());
        return TeamResult.ok(TeamMessages.RENAME_SUCCESS, team, "old", previous, "name", finalName);
    }

    private void register(Team team) {
        teamsById.put(team.getId(), team);
        teamIdsByName.put(normalizeName(team.getName()), team.getId());
        for (UUID member : team.getMemberIds()) {
            teamIdByPlayer.put(member, team.getId());
        }
        pendingDeletions.remove(team.getId());
    }

    private void unregister(Team team) {
        teamsById.remove(team.getId());
        teamIdsByName.remove(normalizeName(team.getName()));
        // Drop invitations to, and alliances with, a team that no longer exists.
        invites.values().forEach(byTeam -> byTeam.remove(team.getId()));
        invites.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        for (Team other : teamsById.values()) {
            other.removeAlly(team.getId());
            other.removeAllyRequest(team.getId());
            other.removeFocusedTeam(team.getId());
        }
        pendingDeletions.add(team.getId());
    }

    // ------------------------------------------------------------------
    // Invites and membership
    // ------------------------------------------------------------------

    /** @return {@code true} if {@code player} holds a live (non-expired) invite from {@code team}. */
    public boolean hasInvite(UUID player, Team team) {
        Map<UUID, Invite> byTeam = invites.get(player);
        Invite invite = byTeam == null ? null : byTeam.get(team.getId());
        if (invite == null) {
            return false;
        }
        if (!invite.isActiveAt(clock.getAsLong())) {
            byTeam.remove(team.getId(), invite);
            return false;
        }
        return true;
    }

    /** @return the teams that currently have a live invite out to {@code player}. */
    public List<Team> getPendingInvites(UUID player) {
        Map<UUID, Invite> byTeam = invites.get(player);
        if (byTeam == null) {
            return List.of();
        }
        List<Team> pending = new ArrayList<>();
        for (Map.Entry<UUID, Invite> entry : Map.copyOf(byTeam).entrySet()) {
            Team team = teamsById.get(entry.getKey());
            if (team == null) {
                byTeam.remove(entry.getKey());
            } else if (hasInvite(player, team)) {
                pending.add(team);
            }
        }
        return List.copyOf(pending);
    }

    public TeamResult invite(Team team, UUID actor, UUID target) {
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(target, "target");
        if (team.getType().isSystem()) {
            return TeamResult.fail(TeamMessages.SYSTEM_TEAM_IMMUTABLE, "team", team.getName());
        }
        Optional<TeamResult> denied = checkRole(team, actor, TeamAction.INVITE);
        if (denied.isPresent()) {
            return denied.get();
        }
        if (team.isMember(target)) {
            return TeamResult.fail(TeamMessages.INVITE_TARGET_IN_TEAM);
        }
        if (hasTeam(target)) {
            return TeamResult.fail(TeamMessages.INVITE_TARGET_IN_TEAM);
        }
        if (isFull(team)) {
            return TeamResult.fail(TeamMessages.TEAM_FULL, "max", String.valueOf(config().maxMembers()));
        }
        if (hasInvite(target, team)) {
            return TeamResult.fail(TeamMessages.INVITE_ALREADY_SENT);
        }

        long expirySeconds = config().inviteExpirySeconds();
        long expiresAt = expirySeconds > 0 ? clock.getAsLong() + expirySeconds * 1000L : 0L;
        invites.computeIfAbsent(target, ignored -> new ConcurrentHashMap<>())
                .put(team.getId(), new Invite(team.getId(), expiresAt));
        return TeamResult.ok(TeamMessages.INVITE_SENT, team, "team", team.getName());
    }

    public TeamResult revokeInvite(Team team, UUID actor, UUID target) {
        Objects.requireNonNull(team, "team");
        Optional<TeamResult> denied = checkRole(team, actor, TeamAction.REVOKE_INVITE);
        if (denied.isPresent()) {
            return denied.get();
        }
        Map<UUID, Invite> byTeam = invites.get(target);
        if (byTeam == null || byTeam.remove(team.getId()) == null) {
            return TeamResult.fail(TeamMessages.INVITE_NOT_FOUND);
        }
        return TeamResult.ok(TeamMessages.INVITE_REVOKED, team, "team", team.getName());
    }

    /**
     * @param player joins on their own behalf
     * @param force  skips the invite, not the member cap: the claim allowance and the
     *               DTR scale with the member count
     */
    public TeamResult join(UUID player, Team team, boolean force) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(team, "team");
        if (team.getType().isSystem()) {
            return TeamResult.fail(TeamMessages.SYSTEM_TEAM_IMMUTABLE, "team", team.getName());
        }
        if (hasTeam(player)) {
            return TeamResult.fail(TeamMessages.JOIN_ALREADY_IN_TEAM);
        }
        if (!force && !hasInvite(player, team)) {
            return TeamResult.fail(TeamMessages.JOIN_NO_INVITE, "team", team.getName());
        }
        if (isFull(team)) {
            return TeamResult.fail(TeamMessages.TEAM_FULL, "max", String.valueOf(config().maxMembers()));
        }

        team.addMember(player, TeamRole.MEMBER);
        teamIdByPlayer.put(player, team.getId());
        invites.remove(player);
        // A team cannot focus its own member (focusPlayer refuses it), so a focus on
        // somebody who joins ends here - it survived the join (found in game, 13/09/2026).
        team.removeFocusedPlayer(player);
        events.callPlayerJoinTeam(team, player);
        return TeamResult.ok(TeamMessages.JOIN_SUCCESS, team, "team", team.getName());
    }

    private boolean isFull(Team team) {
        int max = config().maxMembers();
        return max > 0 && team.getMemberCount() >= max;
    }

    /**
     * A player leaving of their own accord. A leader must hand over leadership
     * first, unless they are the last member - in which case the team is disbanded
     * when {@code disband-on-last-member-leave} is enabled.
     */
    public TeamResult leave(UUID player) {
        Optional<Team> maybeTeam = getTeamOf(player);
        if (maybeTeam.isEmpty()) {
            return TeamResult.fail(TeamMessages.NOT_IN_TEAM);
        }
        Team team = maybeTeam.get();
        TeamRole role = team.getRole(player).orElse(TeamRole.MEMBER);

        if (role == TeamRole.LEADER && team.getMemberCount() > 1) {
            return TeamResult.fail(TeamMessages.LEAVE_LEADER_MUST_TRANSFER, "team", team.getName());
        }
        if (team.getMemberCount() <= 1 && config().disbandOnLastMemberLeave()) {
            String name = team.getName();
            TeamResult disbanded = disband(team, null);
            return disbanded.isSuccess()
                    ? TeamResult.ok(TeamMessages.LEAVE_DISBANDED, team, "team", name)
                    : disbanded;
        }

        removeFromTeam(team, player, TeamEventDispatcher.LeaveCause.LEAVE);
        return TeamResult.ok(TeamMessages.LEAVE_SUCCESS, team, "team", team.getName());
    }

    /** @param actor {@code null} for {@code forcekick} */
    public TeamResult kick(Team team, UUID actor, UUID target) {
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(target, "target");
        Optional<TeamResult> denied = checkRole(team, actor, TeamAction.KICK);
        if (denied.isPresent()) {
            return denied.get();
        }
        if (target.equals(actor)) {
            return TeamResult.fail(TeamMessages.CANNOT_TARGET_SELF);
        }
        Optional<TeamRole> targetRole = team.getRole(target);
        if (targetRole.isEmpty()) {
            return TeamResult.fail(TeamMessages.TARGET_NOT_IN_TEAM);
        }
        if (actor != null) {
            TeamRole actorRole = team.getRole(actor).orElse(TeamRole.MEMBER);
            if (!actorRole.outranks(targetRole.get())) {
                return TeamResult.fail(TeamMessages.INSUFFICIENT_ROLE,
                        "required", TeamRole.LEADER.name(), "role", actorRole.name());
            }
        }

        // Kicking the last member (only reachable through a staff override) would leave
        // an empty team behind: disband it instead, as /team leave does - without
        // removing them first. Removing first fired the leave event on an empty team,
        // whose DTR ceiling was then zero, and "Delta is now RAIDABLE" went out to the
        // whole server just before the team vanished (found in game, 15/09/2026).
        if (team.getMemberCount() == 1 && config().disbandOnLastMemberLeave()) {
            TeamResult disbanded = disband(team, null);
            return disbanded.isSuccess()
                    ? TeamResult.ok(TeamMessages.KICK_SUCCESS, team, "team", team.getName())
                    : disbanded;
        }
        removeFromTeam(team, target, actor == null
                ? TeamEventDispatcher.LeaveCause.FORCED
                : TeamEventDispatcher.LeaveCause.KICK);
        if (targetRole.get() == TeamRole.LEADER) {
            // Only a staff kick reaches the leader, and it left a team with no leader:
            // nobody could disband it, empty its bank or change a role (found in the
            // command review). The highest-ranked member takes over - the project
            // owner's rule, 15/09/2026. Promoted after the removal, so the team never
            // has two leaders at once.
            successorOf(team).ifPresent(successor -> {
                TeamRole previous = team.getRole(successor).orElse(TeamRole.MEMBER);
                team.setRole(successor, TeamRole.LEADER);
                events.callRoleChange(team, successor, previous, TeamRole.LEADER);
            });
        }
        return TeamResult.ok(TeamMessages.KICK_SUCCESS, team, "team", team.getName());
    }

    /**
     * @return who leads a team that has lost its leader: a co-leader, otherwise a
     *         member. Between equals the choice is arbitrary - nothing records who
     *         joined first - so it is made by id, to be the same on every run
     */
    private static Optional<UUID> successorOf(Team team) {
        Optional<UUID> coLeader = team.getMembersWithRole(TeamRole.CO_LEADER).stream().min(Comparator.naturalOrder());
        return coLeader.isPresent()
                ? coLeader
                : team.getMembersWithRole(TeamRole.MEMBER).stream().min(Comparator.naturalOrder());
    }

    private void removeFromTeam(Team team, UUID player, TeamEventDispatcher.LeaveCause cause) {
        team.removeMember(player);
        teamIdByPlayer.remove(player, team.getId());
        chatChannels.remove(player);
        events.callPlayerLeaveTeam(team, player, cause);
    }

    // ------------------------------------------------------------------
    // Roles
    // ------------------------------------------------------------------

    /** @param actor {@code null} for {@code forcepromote} */
    public TeamResult promote(Team team, UUID actor, UUID target) {
        Optional<TeamResult> denied = checkRole(team, actor, TeamAction.PROMOTE);
        if (denied.isPresent()) {
            return denied.get();
        }
        Optional<TeamRole> current = team.getRole(target);
        if (current.isEmpty()) {
            return TeamResult.fail(TeamMessages.TARGET_NOT_IN_TEAM);
        }
        if (target.equals(actor)) {
            return TeamResult.fail(TeamMessages.CANNOT_TARGET_SELF);
        }

        // Leadership is handed over explicitly, never reached by promotion, so a
        // team can never end up with two leaders.
        if (current.get().isAtLeast(TeamRole.CO_LEADER)) {
            return TeamResult.fail(TeamMessages.PROMOTE_ALREADY_HIGHEST, "role", current.get().name());
        }
        TeamRole next = current.get().promoted().orElseThrow();

        int maxCoLeaders = config().maxCoLeaders();
        if (next == TeamRole.CO_LEADER && maxCoLeaders > 0
                && team.getMembersWithRole(TeamRole.CO_LEADER).size() >= maxCoLeaders) {
            return TeamResult.fail(TeamMessages.PROMOTE_CO_LEADER_LIMIT, "max", String.valueOf(maxCoLeaders));
        }

        team.setRole(target, next);
        events.callRoleChange(team, target, current.get(), next);
        return TeamResult.ok(TeamMessages.PROMOTE_SUCCESS, team, "role", next.name());
    }

    /** @param actor {@code null} for {@code forcedemote} */
    public TeamResult demote(Team team, UUID actor, UUID target) {
        Optional<TeamResult> denied = checkRole(team, actor, TeamAction.DEMOTE);
        if (denied.isPresent()) {
            return denied.get();
        }
        Optional<TeamRole> current = team.getRole(target);
        if (current.isEmpty()) {
            return TeamResult.fail(TeamMessages.TARGET_NOT_IN_TEAM);
        }
        if (target.equals(actor)) {
            return TeamResult.fail(TeamMessages.CANNOT_TARGET_SELF);
        }
        if (current.get() == TeamRole.LEADER) {
            return TeamResult.fail(TeamMessages.DEMOTE_CANNOT_DEMOTE_LEADER);
        }
        Optional<TeamRole> next = current.get().demoted();
        if (next.isEmpty()) {
            return TeamResult.fail(TeamMessages.DEMOTE_ALREADY_LOWEST, "role", current.get().name());
        }
        if (actor != null) {
            TeamRole actorRole = team.getRole(actor).orElse(TeamRole.MEMBER);
            if (!actorRole.outranks(current.get())) {
                return TeamResult.fail(TeamMessages.INSUFFICIENT_ROLE,
                        "required", TeamRole.LEADER.name(), "role", actorRole.name());
            }
        }

        team.setRole(target, next.get());
        events.callRoleChange(team, target, current.get(), next.get());
        return TeamResult.ok(TeamMessages.DEMOTE_SUCCESS, team, "role", next.get().name());
    }

    /**
     * Hands leadership to another member. The outgoing leader drops to the
     * configured {@code role-after-leadership-transfer}.
     *
     * @param actor {@code null} for a staff override
     */
    public TeamResult transferLeadership(Team team, UUID actor, UUID target) {
        Objects.requireNonNull(team, "team");
        Optional<TeamResult> denied = checkRole(team, actor, TeamAction.TRANSFER_LEADERSHIP);
        if (denied.isPresent()) {
            return denied.get();
        }
        if (!team.isMember(target)) {
            return TeamResult.fail(TeamMessages.TARGET_NOT_IN_TEAM);
        }
        if (target.equals(actor)) {
            return TeamResult.fail(TeamMessages.CANNOT_TARGET_SELF);
        }

        Optional<UUID> previousLeader = team.getLeader();
        TeamRole targetPrevious = team.getRole(target).orElse(TeamRole.MEMBER);

        previousLeader.ifPresent(leader -> {
            if (!leader.equals(target)) {
                team.setRole(leader, config().roleAfterLeadershipTransfer());
                events.callRoleChange(team, leader, TeamRole.LEADER, config().roleAfterLeadershipTransfer());
            }
        });
        team.setRole(target, TeamRole.LEADER);
        events.callRoleChange(team, target, targetPrevious, TeamRole.LEADER);
        return TeamResult.ok(TeamMessages.TRANSFER_SUCCESS, team, "team", team.getName());
    }

    // ------------------------------------------------------------------
    // Alliances
    // ------------------------------------------------------------------

    /**
     * Offers an alliance to {@code other}, or accepts theirs if they already
     * offered one - the usual two-way handshake, expressed as a single command.
     *
     * @param actor {@code null} for a staff override, which still needs both sides
     *              to have room under their ally limit
     */
    public TeamResult ally(Team team, UUID actor, Team other) {
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(other, "other");
        TeamSettings.AllianceRules rules = config().alliances();
        if (!rules.enabled()) {
            return TeamResult.fail(TeamMessages.ALLY_DISABLED);
        }
        if (team.getId().equals(other.getId())) {
            return TeamResult.fail(TeamMessages.ALLY_SAME_TEAM);
        }
        if (team.getType().isSystem() || other.getType().isSystem()) {
            return TeamResult.fail(TeamMessages.SYSTEM_TEAM_IMMUTABLE, "team", other.getName());
        }
        Optional<TeamResult> denied = checkRole(team, actor, TeamAction.ALLY);
        if (denied.isPresent()) {
            return denied.get();
        }
        if (team.isAlliedWith(other.getId())) {
            return TeamResult.fail(TeamMessages.ALLY_ALREADY_ALLIED, "team", other.getName());
        }
        if (rules.maxAllies() > 0 && team.getAllyCount() >= rules.maxAllies()) {
            return TeamResult.fail(TeamMessages.ALLY_LIMIT_REACHED, "max", String.valueOf(rules.maxAllies()));
        }
        if (rules.maxAllies() > 0 && other.getAllyCount() >= rules.maxAllies()) {
            return TeamResult.fail(TeamMessages.ALLY_TARGET_LIMIT_REACHED,
                    "team", other.getName(), "max", String.valueOf(rules.maxAllies()));
        }

        if (other.hasRequestedAllianceWith(team.getId())) {
            team.addAlly(other.getId());
            other.addAlly(team.getId());
            team.removeAllyRequest(other.getId());
            events.callAllianceChange(team, other, true);
            return TeamResult.ok(TeamMessages.ALLY_NOW_ALLIED, team, "team", other.getName());
        }
        if (team.hasRequestedAllianceWith(other.getId())) {
            return TeamResult.fail(TeamMessages.ALLY_REQUEST_ALREADY_SENT, "team", other.getName());
        }

        team.addAllyRequest(other.getId());
        return TeamResult.ok(TeamMessages.ALLY_REQUEST_SENT, team, "team", other.getName());
    }

    public TeamResult unally(Team team, UUID actor, Team other) {
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(other, "other");
        Optional<TeamResult> denied = checkRole(team, actor, TeamAction.UNALLY);
        if (denied.isPresent()) {
            return denied.get();
        }
        boolean hadAlliance = team.isAlliedWith(other.getId());
        boolean hadRequest = team.hasRequestedAllianceWith(other.getId());
        if (!hadAlliance && !hadRequest) {
            return TeamResult.fail(TeamMessages.UNALLY_NOT_ALLIED, "team", other.getName());
        }

        team.removeAlly(other.getId());
        other.removeAlly(team.getId());
        team.removeAllyRequest(other.getId());
        other.removeAllyRequest(team.getId());
        if (hadAlliance) {
            events.callAllianceChange(team, other, false);
        }
        return TeamResult.ok(TeamMessages.UNALLY_SUCCESS, team, "team", other.getName());
    }

    // ------------------------------------------------------------------
    // Focus
    // ------------------------------------------------------------------

    public TeamResult focusTeam(Team team, UUID actor, Team target) {
        Optional<TeamResult> denied = validateFocus(team, actor, target.getId());
        if (denied.isPresent()) {
            return denied.get();
        }
        if (team.getId().equals(target.getId())) {
            return TeamResult.fail(TeamMessages.FOCUS_CANNOT_FOCUS_OWN_TEAM);
        }
        if (team.isFocusingTeam(target.getId())) {
            return TeamResult.fail(TeamMessages.FOCUS_ALREADY_FOCUSED, "target", target.getName());
        }
        team.addFocusedTeam(target.getId());
        return TeamResult.ok(TeamMessages.FOCUS_TEAM_SUCCESS, team, "target", target.getName());
    }

    /** @param targetName the target's display name, only used to build the feedback message */
    public TeamResult focusPlayer(Team team, UUID actor, UUID target, String targetName) {
        Optional<TeamResult> denied = validateFocus(team, actor, target);
        if (denied.isPresent()) {
            return denied.get();
        }
        if (team.isMember(target)) {
            return TeamResult.fail(TeamMessages.FOCUS_CANNOT_FOCUS_OWN_TEAM);
        }
        if (team.isFocusingPlayer(target)) {
            return TeamResult.fail(TeamMessages.FOCUS_ALREADY_FOCUSED, "target", targetName);
        }
        team.addFocusedPlayer(target);
        return TeamResult.ok(TeamMessages.FOCUS_PLAYER_SUCCESS, team, "target", targetName);
    }

    private Optional<TeamResult> validateFocus(Team team, UUID actor, UUID target) {
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(target, "target");
        TeamSettings.FocusRules rules = config().focus();
        if (!rules.enabled()) {
            return Optional.of(TeamResult.fail(TeamMessages.FOCUS_DISABLED));
        }
        Optional<TeamResult> denied = checkRole(team, actor, TeamAction.FOCUS);
        if (denied.isPresent()) {
            return denied;
        }
        if (rules.maxTargets() > 0 && team.getFocusCount() >= rules.maxTargets()) {
            return Optional.of(TeamResult.fail(TeamMessages.FOCUS_LIMIT_REACHED,
                    "max", String.valueOf(rules.maxTargets())));
        }
        return Optional.empty();
    }

    /** Removes a focus marker on either a team or a player; {@code targetName} is for the message only. */
    public TeamResult unfocus(Team team, UUID actor, UUID targetId, String targetName) {
        Objects.requireNonNull(team, "team");
        Optional<TeamResult> denied = checkRole(team, actor, TeamAction.FOCUS);
        if (denied.isPresent()) {
            return denied.get();
        }
        boolean removed = team.isFocusingTeam(targetId) || team.isFocusingPlayer(targetId);
        if (!removed) {
            return TeamResult.fail(TeamMessages.UNFOCUS_NOT_FOCUSED, "target", targetName);
        }
        team.removeFocusedTeam(targetId);
        team.removeFocusedPlayer(targetId);
        return TeamResult.ok(TeamMessages.UNFOCUS_SUCCESS, team, "target", targetName);
    }

    // ------------------------------------------------------------------
    // Rally
    // ------------------------------------------------------------------

    public TeamResult setRally(Team team, UUID actor, WorldPosition position) {
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(position, "position");
        TeamSettings.RallyRules rules = config().rally();
        if (!rules.enabled()) {
            return TeamResult.fail(TeamMessages.RALLY_DISABLED);
        }
        Optional<TeamResult> denied = checkRole(team, actor, TeamAction.RALLY);
        if (denied.isPresent()) {
            return denied.get();
        }
        long expiresAt = rules.durationSeconds() > 0
                ? clock.getAsLong() + rules.durationSeconds() * 1000L
                : 0L;
        team.setRally(position, expiresAt);
        // %duration% is the name older language files still use: they are never overwritten.
        String seconds = String.valueOf(rules.durationSeconds());
        return TeamResult.ok(TeamMessages.RALLY_SET, team,
                "team", team.getName(), "seconds", seconds, "duration", seconds);
    }

    public TeamResult clearRally(Team team, UUID actor) {
        Objects.requireNonNull(team, "team");
        Optional<TeamResult> denied = checkRole(team, actor, TeamAction.RALLY);
        if (denied.isPresent()) {
            return denied.get();
        }
        if (team.getRally().isEmpty()) {
            return TeamResult.fail(TeamMessages.RALLY_NOT_SET);
        }
        team.setRally(null, 0L);
        return TeamResult.ok(TeamMessages.RALLY_CLEARED, team, "team", team.getName());
    }

    /** @return the rally point, or empty if unset or expired (expiry clears it lazily). */
    public Optional<WorldPosition> getRally(Team team) {
        Optional<Rally> rally = team.getRallyPoint();
        if (rally.isPresent() && !rally.get().isActiveAt(clock.getAsLong())) {
            team.setRally(null, 0L);
            return Optional.empty();
        }
        return rally.map(Rally::position);
    }

    // ------------------------------------------------------------------
    // Bank, points, KOTH captures
    // ------------------------------------------------------------------

    /**
     * Credits the team bank.
     *
     * <p>Debiting the depositing player is the {@code economy/} module's job
     * (CONTRIBUTING.md section 4, priority 5); this only moves the team side, so the
     * {@code /team deposit} command is intentionally not wired up yet.
     */
    public TeamResult depositToBank(Team team, UUID actor, double amount) {
        Objects.requireNonNull(team, "team");
        if (!config().bank().enabled()) {
            return TeamResult.fail(TeamMessages.BANK_DISABLED);
        }
        Optional<TeamResult> denied = checkRole(team, actor, TeamAction.BANK_DEPOSIT);
        if (denied.isPresent()) {
            return denied.get();
        }
        if (!(amount > 0) || !Double.isFinite(amount) || !Double.isFinite(team.getBalance() + amount)) {
            // The last one: a bank holding nearly the largest double would reach
            // Infinity, which MySQL refuses to store (see EconomyManager#deposit).
            return TeamResult.fail(TeamMessages.BANK_INVALID_AMOUNT);
        }
        double balance = team.addBalance(amount);
        return TeamResult.ok(TeamMessages.BANK_DEPOSIT_SUCCESS, team,
                "amount", formatAmount(amount), "balance", formatAmount(balance));
    }

    public TeamResult withdrawFromBank(Team team, UUID actor, double amount) {
        Objects.requireNonNull(team, "team");
        if (!config().bank().enabled()) {
            return TeamResult.fail(TeamMessages.BANK_DISABLED);
        }
        Optional<TeamResult> denied = checkRole(team, actor, TeamAction.BANK_WITHDRAW);
        if (denied.isPresent()) {
            return denied.get();
        }
        if (!(amount > 0) || !Double.isFinite(amount)) {
            return TeamResult.fail(TeamMessages.BANK_INVALID_AMOUNT);
        }
        synchronized (team) {
            if (team.getBalance() < amount) {
                return TeamResult.fail(TeamMessages.BANK_INSUFFICIENT_FUNDS,
                        "balance", formatAmount(team.getBalance()));
            }
            double balance = team.addBalance(-amount);
            return TeamResult.ok(TeamMessages.BANK_WITHDRAW_SUCCESS, team,
                    "amount", formatAmount(amount), "balance", formatAmount(balance));
        }
    }

    /**
     * Takes the price of a claim out of a team's bank - all of it, or nothing when the
     * bank holds less. No role check: the claim module has already checked who may
     * claim, and a claim is paid by the team, not by the member drawing it.
     *
     * @return whether it was paid
     */
    public boolean payForClaim(Team team, double amount) {
        Objects.requireNonNull(team, "team");
        if (!(amount >= 0) || !Double.isFinite(amount)) {
            return false;
        }
        synchronized (team) {
            if (team.getBalance() < amount) {
                return false;
            }
            if (amount > 0) {
                team.addBalance(-amount);
            }
            return true;
        }
    }

    /** Gives a team back part of what a claim cost, when it gives the land up. */
    public void refundClaim(Team team, double amount) {
        Objects.requireNonNull(team, "team");
        if (amount > 0 && Double.isFinite(amount) && Double.isFinite(team.getBalance() + amount)) {
            team.addBalance(amount);
        }
    }

    /**
     * Puts money back into a bank after a half-completed movement, unconditionally.
     *
     * <p>The counterpart of {@code EconomyManager#restore}: when the second leg of a
     * withdrawal fails, the first leg has to be undone, and no rule may refuse that
     * undo. Going back through {@link #withdrawFromBank}'s sibling would consult the
     * live settings, so a reload switching the bank off between the two legs would
     * destroy the money instead of returning it.
     *
     * <p>Never call this to grant money to a team.
     */
    public void restoreToBank(Team team, double amount) {
        Objects.requireNonNull(team, "team");
        if (!(amount > 0) || !Double.isFinite(amount)) {
            return;
        }
        team.addBalance(amount);
    }

    /**
     * How an amount reads in a message: {@code 1234.50} until {@code economy/} installs
     * its own format ({@code $1,234.50}), so a team bank reads in the same currency as
     * a balance.
     */
    private volatile java.util.function.DoubleFunction<String> moneyFormat =
            amount -> String.format(Locale.ROOT, "%.2f", amount);

    public void setMoneyFormat(java.util.function.DoubleFunction<String> moneyFormat) {
        this.moneyFormat = Objects.requireNonNull(moneyFormat, "moneyFormat");
    }

    public String formatAmount(double amount) {
        return moneyFormat.apply(amount);
    }

    /** Staff override of a team's score (FEATURES.md section 1, "Team Points"). */
    public TeamResult setPoints(Team team, long points) {
        Objects.requireNonNull(team, "team");
        long clamped = Math.max(config().points().minimum(), points);
        team.setPoints(clamped);
        return TeamResult.ok(TeamMessages.POINTS_SET, team,
                "team", team.getName(), "points", String.valueOf(clamped));
    }

    /**
     * @return {@code a + b}, held at the largest or smallest {@code long} rather than
     *         wrapping round: {@code /team addpoints} with a huge number turned a
     *         team's points negative, then clamped them to the floor (found in the
     *         command review, 15/09/2026)
     */
    static long saturatedAdd(long a, long b) {
        long sum = a + b;
        // Overflow exactly when both operands share a sign the sum does not have.
        if (((a ^ sum) & (b ^ sum)) < 0) {
            return a < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        return sum;
    }

    /** Adds (or, with a negative amount, removes) points, never dropping below the configured floor. */
    public TeamResult addPoints(Team team, long amount) {
        Objects.requireNonNull(team, "team");
        synchronized (team) {
            long updated = Math.max(config().points().minimum(), saturatedAdd(team.getPoints(), amount));
            team.setPoints(updated);
            return TeamResult.ok(TeamMessages.POINTS_ADDED, team,
                    "team", team.getName(), "amount", String.valueOf(amount), "points", String.valueOf(updated));
        }
    }

    /**
     * Records a KOTH capture for {@code team}, honouring the configured cap
     * (FEATURES.md section 1, "Team KOTH caps").
     *
     * <p>Only the mechanism lives here: how many points a capture is worth and
     * where the cap sits are config values, because FEATURES.md still lists the
     * scoring scale as an open question. The {@code events/} module calls this
     * when a KOTH is won.
     *
     * @return a failure carrying {@code team.koth.cap-reached} once the team is
     *         capped - the capture still happened in-game, it simply stops counting
     */
    public TeamResult recordKothCapture(Team team) {
        return recordKothCapture(team, false);
    }

    /**
     * @param citadel whether the capture was a Citadel's: counted like a KOTH's toward
     *                the cap, paid {@code points.per-citadel-capture} instead
     */
    public TeamResult recordKothCapture(Team team, boolean citadel) {
        Objects.requireNonNull(team, "team");
        TeamSettings.KothRules rules = config().koth();
        synchronized (team) {
            if (rules.maxCountedCaptures() > 0 && team.getKothCaptures() >= rules.maxCountedCaptures()) {
                return TeamResult.fail(TeamMessages.KOTH_CAP_REACHED,
                        "team", team.getName(), "max", String.valueOf(rules.maxCountedCaptures()));
            }
            int captures = team.incrementKothCaptures();
            long points = citadel ? config().points().perCitadelCapture() : rules.pointsPerCapture();
            if (points != 0) {
                addPoints(team, points);
            }
            return TeamResult.ok(TeamMessages.KOTH_CAPTURE_COUNTED, team,
                    "team", team.getName(), "captures", String.valueOf(captures));
        }
    }

    /**
     * Resets every team's counted KOTH captures, for the start of a new map or ranking
     * period ({@code /team resetkoth}).
     *
     * @return how many teams had captures to reset
     */
    public int resetKothCaptures() {
        int reset = 0;
        for (Team team : teamsById.values()) {
            synchronized (team) {
                if (team.getKothCaptures() != 0) {
                    team.setKothCaptures(0);
                    reset++;
                }
            }
        }
        return reset;
    }

    // ------------------------------------------------------------------
    // The Team Points scale
    // ------------------------------------------------------------------

    /**
     * A player died: {@code per-death} to their team, and {@code per-kill} to the
     * killer's - unless they are the same team, or the same player.
     *
     * @param killer who is credited with the kill, or {@code null} for none
     */
    public void recordDeath(UUID killer, UUID victim) {
        Objects.requireNonNull(victim, "victim");
        TeamSettings.PointsRules rules = config().points();
        Team victimTeam = getTeamOf(victim).orElse(null);
        if (victimTeam != null && rules.perDeath() != 0) {
            addPoints(victimTeam, rules.perDeath());
        }
        if (killer == null || killer.equals(victim) || rules.perKill() == 0) {
            return;
        }
        Team killerTeam = getTeamOf(killer).orElse(null);
        if (killerTeam != null && (victimTeam == null || !victimTeam.getId().equals(killerTeam.getId()))) {
            addPoints(killerTeam, rules.perKill());
        }
    }

    /** A team's DTR has just made it raidable: {@code per-raidable}. */
    public void recordRaidable(Team team) {
        award(team, config().points().perRaidable());
    }

    public void recordConquestWin(Team team) {
        award(team, config().points().perConquestWin());
    }

    public void recordKingWin(Team team) {
        award(team, config().points().perKingWin());
    }

    public void recordDtcWin(Team team) {
        award(team, config().points().perDtcWin());
    }

    public void recordLastBreakWin(Team team) {
        award(team, config().points().perLastBreakWin());
    }

    public void recordSlideWin(Team team) {
        award(team, config().points().perSlideWin());
    }

    /** Awards {@code per-totem-win} to the team that broke a whole Totem. */
    public void recordTotemWin(Team team) {
        award(team, config().points().perTotemWin());
    }

    /**
     * Awards the team that broke a whole column: {@code per-mini-totem-win} for one of
     * {@link TeamSettings.PointsRules#MINI_TOTEM_HEIGHT} blocks or fewer, otherwise
     * {@code per-totem-win}.
     */
    public void recordTotemWin(Team team, int height) {
        award(team, height <= TeamSettings.PointsRules.MINI_TOTEM_HEIGHT
                ? config().points().perMiniTotemWin() : config().points().perTotemWin());
    }

    private void award(Team team, long amount) {
        if (team != null && amount != 0 && !team.getType().isSystem()) {
            addPoints(team, amount);
        }
    }

    // ------------------------------------------------------------------
    // Chat channel
    // ------------------------------------------------------------------

    public ChatChannel getChatChannel(UUID player) {
        return chatChannels.getOrDefault(player, ChatChannel.PUBLIC);
    }

    public void setChatChannel(UUID player, ChatChannel channel) {
        if (channel == null || channel == ChatChannel.PUBLIC) {
            chatChannels.remove(player);
        } else {
            chatChannels.put(player, channel);
        }
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    /**
     * Loads every team from the store into the cache. Blocking - call from an
     * async task during startup.
     *
     * <p>It starts by clearing the cache, so nothing may change the cache until it
     * returns: a change made before then is lost. This class cannot enforce that
     * on its own; on the server, {@code StartupGate} keeps players and data
     * commands out until every module's load has landed.
     */
    public void loadAll() throws Exception {
        store.initSchema();
        teamsById.clear();
        teamIdsByName.clear();
        teamIdByPlayer.clear();
        pendingDeletions.clear();
        for (Team team : store.loadAll()) {
            register(team);
            team.clearDirty();
        }
    }

    /**
     * Writes out every team modified since the last flush, plus any deletions.
     * Blocking - call from the async persistence task and once more, inline, at
     * {@code onDisable} (ARCHITECTURE.md section 3).
     *
     * @return the number of teams written
     */
    public int flush() throws Exception {
        int written = 0;
        for (UUID deleted : Set.copyOf(pendingDeletions)) {
            store.delete(deleted);
            pendingDeletions.remove(deleted);
        }
        for (Team team : teamsById.values()) {
            if (!team.isDirty()) {
                continue;
            }
            // Cleared before the write: a change made during the write leaves the
            // flag set again and is picked up by the next flush, rather than lost.
            team.clearDirty();
            try {
                store.save(team);
                written++;
            } catch (Exception e) {
                team.markDirty();
                throw e;
            }
        }
        return written;
    }

    /** Marks every cached team dirty, forcing a full write on the next flush. */
    public void markAllDirty() {
        teamsById.values().forEach(Team::markDirty);
    }
}
