package com.lawkeys.hcfcore.team;

import com.lawkeys.hcfcore.util.WorldPosition;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A team (faction) and everything the team module itself owns about it.
 *
 * <p><strong>This class contains no rule enforcement.</strong> It is a mutable
 * state holder; every "can this player do that" decision lives in
 * {@link TeamManager}, which is the module's single entry point (CONTRIBUTING.md
 * section 3: no business logic outside a Manager/Service).
 *
 * <p><strong>Threading.</strong> Per ARCHITECTURE.md section 3 the in-memory
 * cache is the runtime source of truth and persistence runs on an async task,
 * so a {@code Team} is read from the async writer thread while the main server
 * thread mutates it. Collections are therefore concurrent, scalars are
 * {@code volatile}, and the numeric fields that need read-modify-write
 * (balance, points, KOTH captures) are only mutated under {@code this} lock.
 *
 * <p><strong>Deliberately absent: DTR.</strong> The suggested schema in
 * ARCHITECTURE.md section 4 lists {@code dtr} on the teams table, but DTR is
 * priority 3 in CONTRIBUTING.md section 4 and owned by the {@code dtr/} module. A
 * field nothing maintains would be exactly the "phantom behaviour" that
 * ARCHITECTURE.md section 2 forbids, so the DTR module will add its own column
 * through its own versioned migration when it lands.
 */
public final class Team {

    private final UUID id;
    private final TeamType type;
    private final long createdAt;

    /** Safe or combat, for a system team; {@code null} for a player team, which has no such thing. */
    private volatile SystemZone systemZone;

    private volatile String name;
    private volatile UUID leader;

    private final Map<UUID, TeamRole> members = new ConcurrentHashMap<>();

    /** Team ids this team has an accepted alliance with (kept symmetric by {@link TeamManager}). */
    private final Set<UUID> allies = new CopyOnWriteArraySet<>();
    /** Team ids this team has offered an alliance to, awaiting their answer. */
    private final Set<UUID> outgoingAllyRequests = new CopyOnWriteArraySet<>();

    private final Set<UUID> focusedTeams = new CopyOnWriteArraySet<>();
    private final Set<UUID> focusedPlayers = new CopyOnWriteArraySet<>();

    private volatile Rally rally;

    private double balance;
    private long points;
    private int kothCaptures;

    /** Set whenever mutable state changes; consumed and cleared by the async persistence flush. */
    private volatile boolean dirty;

    public Team(UUID id, String name, TeamType type, UUID leader, long createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.leader = leader;
        this.createdAt = createdAt;
        // A safe zone unless told otherwise: that is what a system team meant before
        // the choice existed, so no existing team changes behaviour.
        this.systemZone = type.isSystem() ? SystemZone.SAFE : null;
        if (leader != null) {
            this.members.put(leader, TeamRole.LEADER);
        }
    }

    public UUID getId() {
        return id;
    }

    public TeamType getType() {
        return type;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    /** @return whether this system team's land is safe or a combat zone; empty for a player team */
    public Optional<SystemZone> getSystemZone() {
        return Optional.ofNullable(systemZone);
    }

    /**
     * @return whether this is server land where PvP is off. Allocation-free: the
     *         PvP module asks it on every hit
     */
    public boolean isSafeZone() {
        return systemZone == SystemZone.SAFE;
    }

    void setSystemZone(SystemZone zone) {
        if (!type.isSystem()) {
            throw new IllegalStateException("only a system team has a zone kind: " + name);
        }
        this.systemZone = Objects.requireNonNull(zone, "zone");
        markDirty();
    }

    public String getName() {
        return name;
    }

    void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
        markDirty();
    }

    /** @return the leader's uuid, or empty for a system team. */
    public Optional<UUID> getLeader() {
        return Optional.ofNullable(leader);
    }

    // --- Membership -------------------------------------------------------

    /** @return an immutable snapshot of members to their role. */
    public Map<UUID, TeamRole> getMembers() {
        return Map.copyOf(members);
    }

    public Set<UUID> getMemberIds() {
        return Set.copyOf(members.keySet());
    }

    public int getMemberCount() {
        return members.size();
    }

    public boolean isMember(UUID player) {
        return player != null && members.containsKey(player);
    }

    public Optional<TeamRole> getRole(UUID player) {
        return player == null ? Optional.empty() : Optional.ofNullable(members.get(player));
    }

    /** @return members holding exactly {@code role}, in no particular order. */
    public Set<UUID> getMembersWithRole(TeamRole role) {
        Set<UUID> result = new LinkedHashSet<>();
        members.forEach((uuid, memberRole) -> {
            if (memberRole == role) {
                result.add(uuid);
            }
        });
        return Collections.unmodifiableSet(result);
    }

    /** @return how many members hold each role; roles with nobody are present with a count of 0. */
    public Map<TeamRole, Integer> countByRole() {
        Map<TeamRole, Integer> counts = new EnumMap<>(TeamRole.class);
        for (TeamRole role : TeamRole.values()) {
            counts.put(role, 0);
        }
        members.values().forEach(role -> counts.merge(role, 1, Integer::sum));
        return counts;
    }

    void addMember(UUID player, TeamRole role) {
        members.put(Objects.requireNonNull(player, "player"), Objects.requireNonNull(role, "role"));
        if (role == TeamRole.LEADER) {
            this.leader = player;
        }
        markDirty();
    }

    void removeMember(UUID player) {
        members.remove(player);
        if (Objects.equals(this.leader, player)) {
            this.leader = null;
        }
        markDirty();
    }

    void setRole(UUID player, TeamRole role) {
        if (!members.containsKey(player)) {
            return;
        }
        members.put(player, role);
        if (role == TeamRole.LEADER) {
            this.leader = player;
        } else if (Objects.equals(this.leader, player)) {
            this.leader = null;
        }
        markDirty();
    }

    // --- Alliances --------------------------------------------------------

    public Set<UUID> getAllies() {
        return Set.copyOf(allies);
    }

    public boolean isAlliedWith(UUID teamId) {
        return allies.contains(teamId);
    }

    public int getAllyCount() {
        return allies.size();
    }

    void addAlly(UUID teamId) {
        allies.add(teamId);
        outgoingAllyRequests.remove(teamId);
        markDirty();
    }

    void removeAlly(UUID teamId) {
        allies.remove(teamId);
        markDirty();
    }

    public Set<UUID> getOutgoingAllyRequests() {
        return Set.copyOf(outgoingAllyRequests);
    }

    public boolean hasRequestedAllianceWith(UUID teamId) {
        return outgoingAllyRequests.contains(teamId);
    }

    /** Alliance offers are runtime-only state and are intentionally not persisted. */
    void addAllyRequest(UUID teamId) {
        outgoingAllyRequests.add(teamId);
    }

    void removeAllyRequest(UUID teamId) {
        outgoingAllyRequests.remove(teamId);
    }

    // --- Focus (tactical marking, FEATURES.md section 1) ------------------

    public Set<UUID> getFocusedTeams() {
        return Set.copyOf(focusedTeams);
    }

    public Set<UUID> getFocusedPlayers() {
        return Set.copyOf(focusedPlayers);
    }

    public boolean isFocusingTeam(UUID teamId) {
        return focusedTeams.contains(teamId);
    }

    public boolean isFocusingPlayer(UUID player) {
        return focusedPlayers.contains(player);
    }

    public int getFocusCount() {
        return focusedTeams.size() + focusedPlayers.size();
    }

    /** Focus is a live tactical marker and is intentionally not persisted. */
    void addFocusedTeam(UUID teamId) {
        focusedTeams.add(teamId);
    }

    void removeFocusedTeam(UUID teamId) {
        focusedTeams.remove(teamId);
    }

    void addFocusedPlayer(UUID player) {
        focusedPlayers.add(player);
    }

    void removeFocusedPlayer(UUID player) {
        focusedPlayers.remove(player);
    }

    void clearFocus() {
        focusedTeams.clear();
        focusedPlayers.clear();
    }

    // --- Rally ------------------------------------------------------------

    /** @return the rally point without checking expiry; use {@code TeamManager#getRally}. */
    public Optional<WorldPosition> getRally() {
        return getRallyPoint().map(Rally::position);
    }

    /** @return the epoch millis at which the rally expires, or {@code 0} if it never expires or there is none. */
    public long getRallyExpiresAt() {
        Rally current = rally;
        return current == null ? 0L : current.expiresAt();
    }

    /** @return the rally with its expiry, without checking it. */
    public Optional<Rally> getRallyPoint() {
        return Optional.ofNullable(rally);
    }

    /** @param position {@code null} clears the rally */
    void setRally(WorldPosition position, long expiresAt) {
        this.rally = position == null ? null : new Rally(position, expiresAt);
        markDirty();
    }

    // --- Bank, points, KOTH captures --------------------------------------

    public synchronized double getBalance() {
        return balance;
    }

    synchronized void setBalance(double balance) {
        this.balance = balance;
        markDirty();
    }

    /** @return the new balance. */
    synchronized double addBalance(double amount) {
        this.balance += amount;
        markDirty();
        return this.balance;
    }

    public synchronized long getPoints() {
        return points;
    }

    synchronized void setPoints(long points) {
        this.points = points;
        markDirty();
    }

    /** @return the new points total. */
    synchronized long addPoints(long amount) {
        this.points += amount;
        markDirty();
        return this.points;
    }

    public synchronized int getKothCaptures() {
        return kothCaptures;
    }

    synchronized void setKothCaptures(int kothCaptures) {
        this.kothCaptures = kothCaptures;
        markDirty();
    }

    synchronized int incrementKothCaptures() {
        this.kothCaptures++;
        markDirty();
        return this.kothCaptures;
    }

    // --- Persistence bridge -----------------------------------------------

    /**
     * @return a flat copy of the persisted state, for a {@link TeamStore} to write
     */
    public synchronized TeamSnapshot toSnapshot() {
        return new TeamSnapshot(id, name, type, systemZone, leader, createdAt, balance, points,
                kothCaptures, getRally().orElse(null), getRallyExpiresAt(), getMembers(), getAllies());
    }

    /**
     * Rebuilds a team from persisted state. Runtime-only state (alliance offers,
     * focus markers) starts empty, and the team starts clean - it has, by
     * definition, just been read from the store.
     */
    public static Team fromSnapshot(TeamSnapshot snapshot) {
        Team team = new Team(snapshot.id(), snapshot.name(), snapshot.type(), snapshot.leader(),
                snapshot.createdAt());
        team.members.clear();
        team.members.putAll(snapshot.members());
        team.leader = snapshot.leader();
        team.allies.addAll(snapshot.allies());
        team.balance = snapshot.balance();
        team.points = snapshot.points();
        team.kothCaptures = snapshot.kothCaptures();
        team.rally = snapshot.rally() == null ? null : new Rally(snapshot.rally(), snapshot.rallyExpiresAt());
        team.systemZone = snapshot.systemZone();
        team.clearDirty();
        return team;
    }

    // --- Persistence bookkeeping ------------------------------------------

    public boolean isDirty() {
        return dirty;
    }

    void markDirty() {
        this.dirty = true;
    }

    void clearDirty() {
        this.dirty = false;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Team other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Team{" + name + " (" + id + "), type=" + type + ", members=" + members.size() + '}';
    }
}
