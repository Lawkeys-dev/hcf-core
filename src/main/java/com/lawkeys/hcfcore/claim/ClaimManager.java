package com.lawkeys.hcfcore.claim;

import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamManager;
import com.lawkeys.hcfcore.team.TeamMessages;
import com.lawkeys.hcfcore.team.TeamResult;
import com.lawkeys.hcfcore.team.TeamRole;
import com.lawkeys.hcfcore.util.ChunkPosition;
import com.lawkeys.hcfcore.util.WorldPosition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The claim module's entry point and rule engine.
 *
 * <p>Like {@code TeamManager} this is pure Java with no server API, so the
 * territory rules are unit-testable without a running server (ARCHITECTURE.md
 * section 13).
 *
 * <p><strong>The central invariant</strong>, from FEATURES.md section 3: a
 * chunk's owner is persistent and is never changed by raiding. Whether that
 * owner is currently protected is a separate, dynamic question, answered per
 * check by {@link RaidabilityPolicy}. Two things follow, and both are enforced
 * here and covered by tests:
 * <ul>
 *   <li>{@link #claim} refuses any chunk that already has an owner - including a
 *       raidable one. Over-claiming is impossible, so a raid can never transfer
 *       land, only open it to pillage.</li>
 *   <li>{@link #checkProtection} derives access from raidability, never from a
 *       notion of "free land".</li>
 * </ul>
 *
 * <p><strong>Threading.</strong> {@link #checkProtection} runs on the main thread
 * for every block event and is allocation-free; the caches are concurrent so the
 * async persistence flush can read them safely.
 */
public final class ClaimManager {

    private final Supplier<ClaimSettings> settings;
    private final TeamManager teams;
    private final ClaimStore store;
    private final LongSupplier clock;

    private volatile RaidabilityPolicy raidability = RaidabilityPolicy.NEVER;
    private volatile ReservedRegionPolicy reservedRegions = ReservedRegionPolicy.NONE;
    private volatile ClaimingPolicy claiming = ClaimingPolicy.OPEN;
    private volatile LockWindow lockWindow = LockWindow.CLOSED;
    /** Teams whose claim is locked. Memory only: a lock lasts one SOTW. */
    private final Set<UUID> lockedTeams = ConcurrentHashMap.newKeySet();

    /** The authoritative ownership index, and the hot-path lookup for protection checks. */
    private final Map<ChunkPosition, UUID> ownerByChunk = new ConcurrentHashMap<>();
    /** Reverse index, so per-team operations do not scan every chunk on the server. */
    private final Map<UUID, Set<ChunkPosition>> chunksByTeam = new ConcurrentHashMap<>();
    private final Map<UUID, Map<HomeType, TeamHome>> homesByTeam = new ConcurrentHashMap<>();

    private final Map<UUID, Long> claimedAt = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyTeams = ConcurrentHashMap.newKeySet();
    private final Set<UUID> deletedTeams = ConcurrentHashMap.newKeySet();

    public ClaimManager(Supplier<ClaimSettings> settings, TeamManager teams, ClaimStore store) {
        this(settings, teams, store, System::currentTimeMillis);
    }

    public ClaimManager(Supplier<ClaimSettings> settings, TeamManager teams, ClaimStore store,
                        LongSupplier clock) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private ClaimSettings config() {
        return settings.get();
    }

    /**
     * @return whether territory rules are in force at all
     *
     * <p>{@code enabled: false} in claims.yml turns off claiming <em>and</em>
     * protection, not one of the two: a module that refuses claims while still
     * refusing block breaks tells its operator one thing and its players another.
     * So every question that decides - may this be claimed, built on, blown up -
     * asks this first. Who owns what is untouched, and so are the lookups that only
     * report it: turning the module back on restores every rule as it was.
     */
    public boolean isEnforced() {
        return config().enabled();
    }

    /**
     * Installs the raid policy. Called by the {@code dtr/} module at startup;
     * until then {@link RaidabilityPolicy#NEVER} keeps every claim protected.
     */
    public void setRaidabilityPolicy(RaidabilityPolicy policy) {
        this.raidability = Objects.requireNonNull(policy, "policy");
    }

    public RaidabilityPolicy getRaidabilityPolicy() {
        return raidability;
    }

    /**
     * Installs the reserved-region policy. Called by the {@code resourcenode/}
     * module at startup; until then {@link ReservedRegionPolicy#NONE} leaves every
     * chunk claimable.
     */
    public void setReservedRegionPolicy(ReservedRegionPolicy policy) {
        this.reservedRegions = Objects.requireNonNull(policy, "policy");
    }

    public ReservedRegionPolicy getReservedRegionPolicy() {
        return reservedRegions;
    }

    /**
     * Installs the claiming policy. Called by the {@code phase/} module at startup
     * (EOTW closes claiming); until then {@link ClaimingPolicy#OPEN} changes nothing.
     */
    public void setClaimingPolicy(ClaimingPolicy policy) {
        this.claiming = Objects.requireNonNull(policy, "policy");
    }

    /**
     * Installs the lock window. Called by the {@code phase/} module at startup
     * (open during SOTW); until then {@link LockWindow#CLOSED} means no claim can be
     * locked.
     */
    public void setLockWindow(LockWindow window) {
        this.lockWindow = Objects.requireNonNull(window, "window");
    }

    // ------------------------------------------------------------------
    // Locked claims (SOTW)
    // ------------------------------------------------------------------

    /**
     * Locks or unlocks a team's claim: while it is locked, nobody but the team's
     * members may enter it - allies included.
     *
     * @param actor who asked; the role for {@link ClaimAction#LOCK_CLAIM} applies
     */
    public TeamResult toggleLock(Team team, UUID actor) {
        Objects.requireNonNull(team, "team");
        if (!lockWindow.isOpen()) {
            return TeamResult.fail(ClaimMessages.LOCK_NOT_NOW);
        }
        Optional<TeamResult> denied = checkRole(team, actor, ClaimAction.LOCK_CLAIM);
        if (denied.isPresent()) {
            return denied.get();
        }
        if (lockedTeams.remove(team.getId())) {
            return TeamResult.ok(ClaimMessages.LOCK_OFF, team);
        }
        lockedTeams.add(team.getId());
        return TeamResult.ok(ClaimMessages.LOCK_ON, team);
    }

    /**
     * @return whether this team's claim is locked. Once the window has closed -
     *         SOTW over - every lock is dropped, so the next SOTW starts open
     */
    public boolean isLocked(UUID teamId) {
        if (!lockWindow.isOpen()) {
            lockedTeams.clear();
            return false;
        }
        return lockedTeams.contains(teamId);
    }

    /** @return the team whose locked claim this player may not stand in, if any */
    public Optional<Team> lockedAgainst(ChunkPosition chunk, UUID player) {
        return getOwner(chunk).filter(owner -> isLocked(owner.getId()) && !owner.isMember(player));
    }

    // ------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------

    /** @return the id of the team owning {@code chunk}, or empty for wilderness. */
    public Optional<UUID> getOwnerId(ChunkPosition chunk) {
        return chunk == null ? Optional.empty() : Optional.ofNullable(ownerByChunk.get(chunk));
    }

    /** @return the team owning {@code chunk}, or empty for wilderness. */
    public Optional<Team> getOwner(ChunkPosition chunk) {
        return getOwnerId(chunk).flatMap(teams::getTeam);
    }

    public boolean isClaimed(ChunkPosition chunk) {
        return ownerByChunk.containsKey(chunk);
    }

    /** @return an immutable snapshot of the chunks owned by {@code teamId}. */
    public Set<ChunkPosition> getClaims(UUID teamId) {
        Set<ChunkPosition> chunks = chunksByTeam.get(teamId);
        return chunks == null ? Set.of() : Set.copyOf(chunks);
    }

    public int getClaimCount(UUID teamId) {
        Set<ChunkPosition> chunks = chunksByTeam.get(teamId);
        return chunks == null ? 0 : chunks.size();
    }

    public int getTotalClaimCount() {
        return ownerByChunk.size();
    }

    /**
     * @return how many chunks {@code team} may own in total; {@code 0} means
     *         unlimited. Server land is unlimited: the allowance scales with members,
     *         and a system team has none, so it would otherwise be held to the
     *         base allowance - sixteen chunks for a whole warzone
     */
    public int getMaxClaims(Team team) {
        if (team.getType().isSystem()) {
            return 0;
        }
        return config().maxClaimsFor(team.getMemberCount());
    }

    // ------------------------------------------------------------------
    // Protection - the reclaim rule
    // ------------------------------------------------------------------

    /**
     * Decides whether {@code actor} may modify blocks on {@code chunk}.
     *
     * <p>This is the check FEATURES.md section 3 specifies: it reads current
     * raidability, never a notion of unowned land, so a team regains protection
     * the moment its DTR goes back above zero without anything being re-claimed.
     *
     * @param actorTeam the actor's team, or {@code null} if they have none
     */
    public ProtectionResult checkProtection(Team actorTeam, ChunkPosition chunk) {
        if (!isEnforced()) {
            return ProtectionResult.ALLOWED;
        }
        UUID ownerId = ownerByChunk.get(chunk);
        if (ownerId == null) {
            return ProtectionResult.ALLOWED;
        }
        if (actorTeam != null && ownerId.equals(actorTeam.getId())) {
            return ProtectionResult.ALLOWED;
        }

        Team owner = teams.getTeam(ownerId).orElse(null);
        if (owner == null) {
            // The owning team is gone but its rows outlived it. Treat the land as
            // wilderness rather than as permanently locked, and drop the stale entry.
            releaseAll(ownerId);
            return ProtectionResult.ALLOWED;
        }
        if (owner.getType().isSystem()) {
            return ProtectionResult.DENIED_SYSTEM;
        }
        if (actorTeam != null && actorTeam.isAlliedWith(ownerId)) {
            return config().protection().allowAllyBuild()
                    ? ProtectionResult.ALLOWED
                    : ProtectionResult.DENIED_ALLY;
        }
        if (config().protection().allowRaidBuilding() && raidability.isRaidable(ownerId)) {
            return ProtectionResult.ALLOWED_RAID;
        }
        return ProtectionResult.DENIED_CLAIMED;
    }

    /** Convenience overload resolving the actor's team first. */
    public ProtectionResult checkProtection(UUID actor, ChunkPosition chunk) {
        return checkProtection(teams.getTeamOf(actor).orElse(null), chunk);
    }

    /**
     * Decides whether {@code actorTeam} may place or break one particular block.
     *
     * <p>Claimed land follows its owner's rules through
     * {@link #checkProtection(Team, ChunkPosition)}, warzone or not. Unclaimed land
     * is where the warzone applies: inside it, building is refused unless the
     * operator allows it - except for the blocks of a region another system
     * governs, such as a Mountain, which must stay minable and enforces its own
     * rules.
     *
     * <p>Interactions - doors, chests, buttons - are not building and keep using
     * the chunk-level check: the warzone is public land, not somebody's property.
     *
     * @param actorTeam the actor's team, or {@code null} if they have none
     */
    public ProtectionResult checkBuild(Team actorTeam, String world, int x, int y, int z) {
        if (!isEnforced()) {
            return ProtectionResult.ALLOWED;
        }
        ChunkPosition chunk = ChunkPosition.fromBlock(world, x, z);
        if (ownerByChunk.containsKey(chunk)) {
            ProtectionResult owned = checkProtection(actorTeam, chunk);
            // checkProtection drops an owner whose team no longer exists; what is left
            // is unclaimed land, which falls through to the warzone below.
            if (ownerByChunk.containsKey(chunk)) {
                return owned;
            }
        }
        ClaimSettings.WarzoneRules warzone = config().warzone();
        if (!warzone.allowBuilding() && warzone.covers(chunk)
                && !reservedRegions.isInReservedRegion(world, x, y, z)) {
            return ProtectionResult.DENIED_WARZONE;
        }
        return ProtectionResult.ALLOWED;
    }

    /**
     * @return whether {@code chunk} is unclaimed warzone land. The warzone owns
     *         nothing - it is only a set of rules - so while the module is off there
     *         is none, and the map and border messages show wilderness instead
     */
    public boolean isWarzone(ChunkPosition chunk) {
        ClaimSettings config = config();
        return config.enabled() && !ownerByChunk.containsKey(chunk) && config.warzone().covers(chunk);
    }

    /**
     * @return whether two chunks are the same territory, which is when a border
     *         announcement stays silent: the same owner, or both unclaimed and both
     *         warzone or both wilderness
     *
     * <p>Compared by identity, never by the names shown: the warzone's display name
     * is free text and may well be a team's name - a server team called Warzone,
     * typically - and crossing between the two would then read as no border at all.
     */
    public boolean isSameTerritory(ChunkPosition a, ChunkPosition b) {
        UUID ownerA = ownerByChunk.get(a);
        UUID ownerB = ownerByChunk.get(b);
        if (ownerA != null || ownerB != null) {
            return Objects.equals(ownerA, ownerB);
        }
        return isWarzone(a) == isWarzone(b);
    }

    /**
     * @return whether an explosion must be stopped from breaking that block
     *
     * <p>An explosion has no actor: TNT does not belong to a team by the time the
     * blast is resolved, and attributing it to whoever placed it would be a guess.
     * So the question is asked as a stranger would ask it - which is the strict
     * reading, and the right one here: it means a raidable team's territory still
     * blows up (that is what the pillage window of FEATURES.md section 3 is for),
     * while a protected team's does not.
     *
     * <p>Asked per block, like {@link #checkBuild}, and for the same reason: on
     * unclaimed warzone, a charge must not do what a pickaxe is refused - on the
     * same blocks. The blocks of a region another system governs, such as a
     * Mountain, follow that system's rules for explosions as they do for mining; a
     * chunk-wide answer here would strip them from the blast before that system is
     * asked, whatever its own setting says.
     *
     * <p>The rule sits here rather than in the listener so that "does TNT break
     * this?" is answerable in a unit test with no server.
     */
    public boolean isExplosionProtected(String world, int x, int y, int z) {
        if (!isEnforced() || !config().protection().blockExplosions()) {
            return false;
        }
        ChunkPosition chunk = ChunkPosition.fromBlock(world, x, z);
        if (!ownerByChunk.containsKey(chunk)) {
            ClaimSettings.WarzoneRules warzone = config().warzone();
            return !warzone.allowBuilding() && warzone.covers(chunk)
                    && !reservedRegions.isInReservedRegion(world, x, y, z);
        }
        return !checkProtection((Team) null, chunk).isAllowed();
    }

    /**
     * Decides whether something set off on one block may change another: a piston
     * moving it, a liquid flowing into it, fire spreading to it, a dispenser acting
     * on it, a tree growing into it.
     *
     * <p>The project owner's rule, 15/09/2026: <strong>what comes from another
     * territory is judged as if its owner did it.</strong> None of these carries a
     * player, so without it a sticky piston in the wilderness pulled a protected
     * team's wall down. A machine on wilderness now reaches a claim the way a
     * teamless player would build in it - refused on a protected team's land,
     * allowed on a raidable one - and a team's own machines work anywhere its
     * members may build.
     *
     * <p>Server land is the server's own: what starts on it may reach unclaimed land
     * and other server land - a fountain on the edge of spawn keeps running into the
     * warzone - while a player team's land is judged as it would be for anybody.
     *
     * <p><strong>Hot path.</strong> Every flowing liquid asks this. Within one chunk
     * the answer is always yes, territory being by chunk, and costs two shifts;
     * across a border, two lookups and the build check.
     *
     * @param world the world both blocks are in
     */
    public boolean mayReach(String world, int fromX, int fromZ, int toX, int toY, int toZ) {
        if (ChunkPosition.toChunk(fromX) == ChunkPosition.toChunk(toX)
                && ChunkPosition.toChunk(fromZ) == ChunkPosition.toChunk(toZ)) {
            return true;
        }
        if (!isEnforced()) {
            return true;
        }
        ChunkPosition from = ChunkPosition.fromBlock(world, fromX, fromZ);
        ChunkPosition to = ChunkPosition.fromBlock(world, toX, toZ);
        if (isSameTerritory(from, to)) {
            return true;
        }
        UUID sourceOwner = ownerByChunk.get(from);
        Team source = sourceOwner == null ? null : teams.getTeam(sourceOwner).orElse(null);
        if (source != null && source.getType().isSystem() && !isPlayerLand(to)) {
            return true;
        }
        return checkBuild(source, world, toX, toY, toZ).isAllowed();
    }

    /** @return whether a player team - not the server - owns that chunk */
    private boolean isPlayerLand(ChunkPosition chunk) {
        UUID ownerId = ownerByChunk.get(chunk);
        return ownerId != null && teams.getTeam(ownerId).map(owner -> !owner.getType().isSystem()).orElse(false);
    }

    // ------------------------------------------------------------------
    // Claiming
    // ------------------------------------------------------------------

    /**
     * Claims a set of chunks for {@code team}, all or nothing.
     *
     * <p>All-or-nothing on purpose: a partially applied claim would leave the
     * player with a territory they did not ask for and a bill they cannot undo in
     * one command.
     *
     * @param actor {@code null} for a staff override, which skips the role check
     *              and the per-command cap but still cannot over-claim
     */
    public TeamResult claim(Team team, UUID actor, Collection<ChunkPosition> chunks) {
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(chunks, "chunks");
        ClaimSettings config = config();

        if (!config.enabled()) {
            return TeamResult.fail(ClaimMessages.CLAIM_DISABLED);
        }
        // Players only: staff claiming server land is never closed by a phase.
        if (actor != null) {
            Optional<String> closed = claiming.refusal();
            if (closed.isPresent()) {
                return TeamResult.fail(closed.get());
            }
        }
        if (team.getType().isSystem() && actor != null) {
            return TeamResult.fail(ClaimMessages.CLAIM_SYSTEM_TEAM, "team", team.getName());
        }
        Optional<TeamResult> denied = checkRole(team, actor, ClaimAction.CLAIM);
        if (denied.isPresent()) {
            return denied.get();
        }

        Set<ChunkPosition> requested = new LinkedHashSet<>(chunks);
        if (requested.isEmpty()) {
            return TeamResult.fail(ClaimMessages.CLAIM_NOTHING_SELECTED);
        }
        if (actor != null && config.limits().maxPerCommand() > 0
                && requested.size() > config.limits().maxPerCommand()) {
            return TeamResult.fail(ClaimMessages.CLAIM_TOO_MANY_AT_ONCE,
                    "max", String.valueOf(config.limits().maxPerCommand()),
                    "count", String.valueOf(requested.size()));
        }

        for (ChunkPosition chunk : requested) {
            if (!config.isClaimable(chunk.world())) {
                return TeamResult.fail(ClaimMessages.CLAIM_WORLD_DISABLED, "world", chunk.world());
            }
            // Asked before ownership, because a reserved chunk is not land anybody
            // can hold: an event region is out of the claim system entirely, not a
            // chunk that merely happens to be taken.
            Optional<String> reserved = reservedRegions.reservedRegionAt(chunk);
            if (reserved.isPresent()) {
                return TeamResult.fail(ClaimMessages.CLAIM_RESERVED_REGION,
                        "chunk", chunk.toString(), "region", reserved.get());
            }
            // Over-claiming is refused here, before anything else about the target
            // team is considered - being raidable never makes land available.
            UUID owner = ownerByChunk.get(chunk);
            if (owner != null) {
                if (owner.equals(team.getId())) {
                    return TeamResult.fail(ClaimMessages.CLAIM_ALREADY_YOURS, "chunk", chunk.toString());
                }
                String ownerName = teams.getTeam(owner).map(Team::getName).orElse(owner.toString());
                return TeamResult.fail(ClaimMessages.CLAIM_ALREADY_OWNED,
                        "chunk", chunk.toString(), "team", ownerName);
            }
            // The warzone is server land nobody claims - staff included, for a player
            // team. A server team may: spawn sits at its centre, roads cross it.
            if (!team.getType().isSystem() && config.warzone().covers(chunk)) {
                return TeamResult.fail(ClaimMessages.CLAIM_WARZONE,
                        "chunk", chunk.toString(), "warzone", config.warzone().displayName());
            }
        }

        int max = getMaxClaims(team);
        if (max > 0 && getClaimCount(team.getId()) + requested.size() > max) {
            return TeamResult.fail(ClaimMessages.CLAIM_LIMIT_REACHED,
                    "max", String.valueOf(max), "current", String.valueOf(getClaimCount(team.getId())));
        }

        // Placement rules - connected territory, a buffer to other teams - are how
        // players share the map. Server land is drawn by staff and follows neither:
        // a road is not connected to spawn by definition, and spawn must be allowed
        // to border the warzone around it.
        if (!team.getType().isSystem()) {
            Optional<TeamResult> placement = checkPlacement(team, requested, config);
            if (placement.isPresent()) {
                return placement.get();
            }
        }

        long now = clock.getAsLong();
        Set<ChunkPosition> owned = chunksByTeam.computeIfAbsent(team.getId(),
                ignored -> ConcurrentHashMap.newKeySet());
        for (ChunkPosition chunk : requested) {
            ownerByChunk.put(chunk, team.getId());
            owned.add(chunk);
        }
        claimedAt.putIfAbsent(team.getId(), now);
        markDirty(team.getId());

        return TeamResult.ok(ClaimMessages.CLAIM_SUCCESS, team,
                "count", String.valueOf(requested.size()),
                "total", String.valueOf(owned.size()),
                "max", max > 0 ? String.valueOf(max) : "∞");
    }

    /**
     * Checks the placement rules for a batch: connectivity to the team's existing
     * territory, and the buffer to other teams' claims.
     */
    private Optional<TeamResult> checkPlacement(Team team, Set<ChunkPosition> requested,
                                                ClaimSettings config) {
        Set<ChunkPosition> existing = chunksByTeam.getOrDefault(team.getId(), Set.of());

        if (config.placement().requireConnected()) {
            Optional<TeamResult> disconnected = checkConnectivity(requested, existing);
            if (disconnected.isPresent()) {
                return disconnected;
            }
        }

        int buffer = config.placement().minimumDistanceToOthers();
        if (buffer > 0) {
            Optional<TeamResult> tooClose = checkBuffer(team, requested, buffer);
            if (tooClose.isPresent()) {
                return tooClose;
            }
        }
        return Optional.empty();
    }

    /**
     * Connectivity is judged per world: a team already holding land in a world must
     * extend it there, but may open a new territory in a world it has never claimed
     * in. Within the batch, chunks connect through each other, so a square claimed
     * in one command is valid as long as the square as a whole touches the border.
     */
    private Optional<TeamResult> checkConnectivity(Set<ChunkPosition> requested,
                                                   Set<ChunkPosition> existing) {
        Set<String> worlds = new LinkedHashSet<>();
        for (ChunkPosition chunk : requested) {
            worlds.add(chunk.world());
        }

        for (String world : worlds) {
            boolean hasTerritoryHere = existing.stream().anyMatch(c -> c.world().equals(world));
            if (!hasTerritoryHere) {
                continue;
            }
            boolean touches = false;
            for (ChunkPosition chunk : requested) {
                if (!chunk.world().equals(world)) {
                    continue;
                }
                for (ChunkPosition neighbour : chunk.neighbours()) {
                    if (existing.contains(neighbour)) {
                        touches = true;
                        break;
                    }
                }
                if (touches) {
                    break;
                }
            }
            if (!touches) {
                return Optional.of(TeamResult.fail(ClaimMessages.CLAIM_NOT_CONNECTED));
            }
        }
        return Optional.empty();
    }

    /**
     * Enforces the buffer between teams by scanning the square around each
     * requested chunk, rather than walking every claim on the server: the cost is
     * bounded by the buffer size, not by how much of the map is claimed.
     */
    private Optional<TeamResult> checkBuffer(Team team, Set<ChunkPosition> requested, int buffer) {
        for (ChunkPosition chunk : requested) {
            for (int dx = -buffer + 1; dx < buffer; dx++) {
                for (int dz = -buffer + 1; dz < buffer; dz++) {
                    UUID owner = ownerByChunk.get(
                            new ChunkPosition(chunk.world(), chunk.x() + dx, chunk.z() + dz));
                    if (owner == null || owner.equals(team.getId())) {
                        continue;
                    }
                    String ownerName = teams.getTeam(owner).map(Team::getName).orElse(owner.toString());
                    return Optional.of(TeamResult.fail(ClaimMessages.CLAIM_TOO_CLOSE,
                            "team", ownerName, "distance", String.valueOf(buffer)));
                }
            }
        }
        return Optional.empty();
    }

    /** @param actor {@code null} for a staff override */
    public TeamResult unclaim(Team team, UUID actor, ChunkPosition chunk) {
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(chunk, "chunk");
        Optional<TeamResult> denied = checkRole(team, actor, ClaimAction.UNCLAIM);
        if (denied.isPresent()) {
            return denied.get();
        }

        UUID owner = ownerByChunk.get(chunk);
        if (owner == null) {
            return TeamResult.fail(ClaimMessages.UNCLAIM_NOT_CLAIMED, "chunk", chunk.toString());
        }
        if (!owner.equals(team.getId())) {
            String ownerName = teams.getTeam(owner).map(Team::getName).orElse(owner.toString());
            return TeamResult.fail(ClaimMessages.UNCLAIM_NOT_YOURS, "team", ownerName);
        }

        if (!config().placement().allowDisconnecting() && actor != null
                && wouldDisconnect(team.getId(), chunk)) {
            return TeamResult.fail(ClaimMessages.UNCLAIM_WOULD_DISCONNECT, "chunk", chunk.toString());
        }

        removeClaim(team.getId(), chunk);
        markDirty(team.getId());
        return TeamResult.ok(ClaimMessages.UNCLAIM_SUCCESS, team,
                "chunk", chunk.toString(), "total", String.valueOf(getClaimCount(team.getId())));
    }

    /**
     * @return {@code true} if removing {@code chunk} would split the team's
     *         territory in that world into disconnected pieces
     */
    private boolean wouldDisconnect(UUID teamId, ChunkPosition chunk) {
        Set<ChunkPosition> owned = chunksByTeam.getOrDefault(teamId, Set.of());
        Set<ChunkPosition> remaining = new LinkedHashSet<>();
        for (ChunkPosition owns : owned) {
            if (!owns.equals(chunk) && owns.world().equals(chunk.world())) {
                remaining.add(owns);
            }
        }
        if (remaining.size() <= 1) {
            return false;
        }

        // Flood fill from any remaining chunk; if it does not reach them all, the
        // removal would have cut the territory in two.
        ChunkPosition start = remaining.iterator().next();
        Set<ChunkPosition> reached = new LinkedHashSet<>();
        List<ChunkPosition> queue = new ArrayList<>();
        queue.add(start);
        reached.add(start);
        while (!queue.isEmpty()) {
            ChunkPosition current = queue.remove(queue.size() - 1);
            for (ChunkPosition neighbour : current.neighbours()) {
                if (remaining.contains(neighbour) && reached.add(neighbour)) {
                    queue.add(neighbour);
                }
            }
        }
        return reached.size() != remaining.size();
    }

    /** @param actor {@code null} for a staff override */
    public TeamResult unclaimAll(Team team, UUID actor) {
        Objects.requireNonNull(team, "team");
        Optional<TeamResult> denied = checkRole(team, actor, ClaimAction.UNCLAIM);
        if (denied.isPresent()) {
            return denied.get();
        }
        int removed = releaseAll(team.getId());
        if (removed == 0) {
            return TeamResult.fail(ClaimMessages.UNCLAIM_NOT_CLAIMED, "chunk", "-");
        }
        return TeamResult.ok(ClaimMessages.UNCLAIM_ALL_SUCCESS, team, "count", String.valueOf(removed));
    }

    /**
     * Drops every claim and home held by a team, with no permission check.
     *
     * <p>Called when a team is disbanded, which is the one case where territory
     * disappears without anyone unclaiming it.
     *
     * @return the number of chunks released
     */
    public int releaseAll(UUID teamId) {
        Set<ChunkPosition> owned = chunksByTeam.remove(teamId);
        int removed = 0;
        if (owned != null) {
            for (ChunkPosition chunk : owned) {
                ownerByChunk.remove(chunk, teamId);
                removed++;
            }
        }
        homesByTeam.remove(teamId);
        claimedAt.remove(teamId);
        dirtyTeams.remove(teamId);
        deletedTeams.add(teamId);
        return removed;
    }

    private void removeClaim(UUID teamId, ChunkPosition chunk) {
        ownerByChunk.remove(chunk, teamId);
        Set<ChunkPosition> owned = chunksByTeam.get(teamId);
        if (owned != null) {
            owned.remove(chunk);
        }
    }

    // ------------------------------------------------------------------
    // Homes
    // ------------------------------------------------------------------

    /** @param actor {@code null} for a staff override */
    public TeamResult setHome(Team team, UUID actor, HomeType type, WorldPosition position) {
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(position, "position");
        ClaimSettings config = config();

        if (!config.homes().enabled()) {
            return TeamResult.fail(ClaimMessages.HOME_DISABLED);
        }
        Optional<TeamResult> denied = checkRole(team, actor, ClaimAction.SET_HOME);
        if (denied.isPresent()) {
            return denied.get();
        }
        if (config.homes().requireInsideTerritory()) {
            ChunkPosition chunk = ChunkPosition.fromBlock(position.world(),
                    (int) Math.floor(position.x()), (int) Math.floor(position.z()));
            if (!team.getId().equals(ownerByChunk.get(chunk))) {
                return TeamResult.fail(ClaimMessages.HOME_NOT_IN_TERRITORY, "type", type.name());
            }
        }

        homesByTeam.computeIfAbsent(team.getId(), ignored -> new ConcurrentHashMap<>())
                .put(type, new TeamHome(team.getId(), type, position, clock.getAsLong()));
        markDirty(team.getId());
        return TeamResult.ok(ClaimMessages.HOME_SET, team, "type", type.name());
    }

    public Optional<TeamHome> getHome(UUID teamId, HomeType type) {
        Map<HomeType, TeamHome> homes = homesByTeam.get(teamId);
        return homes == null ? Optional.empty() : Optional.ofNullable(homes.get(type));
    }

    /** @return every home held by {@code teamId}, keyed by type. */
    public Map<HomeType, TeamHome> getHomes(UUID teamId) {
        Map<HomeType, TeamHome> homes = homesByTeam.get(teamId);
        return homes == null ? Map.of() : Map.copyOf(homes);
    }

    // ------------------------------------------------------------------
    // Role checks
    // ------------------------------------------------------------------

    /** @param actor {@code null} bypasses the check, as it does in {@code TeamManager} */
    private Optional<TeamResult> checkRole(Team team, UUID actor, ClaimAction action) {
        if (actor == null) {
            return Optional.empty();
        }
        Optional<TeamRole> role = team.getRole(actor);
        if (role.isEmpty()) {
            return Optional.of(TeamResult.fail(TeamMessages.NOT_A_MEMBER, "team", team.getName()));
        }
        TeamRole required = config().requiredRole(action);
        if (!role.get().isAtLeast(required)) {
            return Optional.of(TeamResult.fail(ClaimMessages.INSUFFICIENT_ROLE,
                    "required", required.name(), "role", role.get().name()));
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    /** Loads claims and homes into the cache. Blocking - async task only. */
    public void loadAll() throws Exception {
        store.initSchema();
        ownerByChunk.clear();
        chunksByTeam.clear();
        homesByTeam.clear();
        dirtyTeams.clear();
        deletedTeams.clear();

        for (Claim claim : store.loadClaims()) {
            ownerByChunk.put(claim.chunk(), claim.teamId());
            chunksByTeam.computeIfAbsent(claim.teamId(), ignored -> ConcurrentHashMap.newKeySet())
                    .add(claim.chunk());
            claimedAt.putIfAbsent(claim.teamId(), claim.claimedAt());
        }
        for (TeamHome home : store.loadHomes()) {
            homesByTeam.computeIfAbsent(home.teamId(), ignored -> new ConcurrentHashMap<>())
                    .put(home.type(), home);
        }
    }

    /**
     * Writes out every team whose territory changed since the last flush.
     * Blocking - async task only.
     *
     * @return the number of teams written
     */
    public int flush() throws Exception {
        for (UUID deleted : Set.copyOf(deletedTeams)) {
            store.deleteTeam(deleted);
            deletedTeams.remove(deleted);
        }

        int written = 0;
        for (UUID teamId : Set.copyOf(dirtyTeams)) {
            // Cleared first: a change made during the write leaves the team dirty
            // again for the next flush rather than being lost.
            dirtyTeams.remove(teamId);
            try {
                long since = claimedAt.getOrDefault(teamId, clock.getAsLong());
                List<Claim> claims = new ArrayList<>();
                for (ChunkPosition chunk : chunksByTeam.getOrDefault(teamId, Set.of())) {
                    claims.add(new Claim(teamId, chunk, since));
                }
                store.saveClaims(teamId, claims);

                Map<HomeType, TeamHome> homes = homesByTeam.getOrDefault(teamId, Map.of());
                for (HomeType type : HomeType.values()) {
                    TeamHome home = homes.get(type);
                    if (home == null) {
                        store.deleteHome(teamId, type);
                    } else {
                        store.saveHome(home);
                    }
                }
                written++;
            } catch (Exception e) {
                dirtyTeams.add(teamId);
                throw e;
            }
        }
        return written;
    }

    private void markDirty(UUID teamId) {
        dirtyTeams.add(teamId);
        deletedTeams.remove(teamId);
    }

    /** Marks every team with territory dirty, forcing a full write on the next flush. */
    public void markAllDirty() {
        dirtyTeams.addAll(chunksByTeam.keySet());
        dirtyTeams.addAll(homesByTeam.keySet());
    }

    /** @return an immutable snapshot of every claim, for the map renderer and tests. */
    public Map<ChunkPosition, UUID> getAllClaims() {
        return Map.copyOf(ownerByChunk);
    }

    /** @return how many chunks each team owns, for leaderboards and the info command. */
    public Map<UUID, Integer> getClaimCounts() {
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        chunksByTeam.forEach((teamId, chunks) -> counts.put(teamId, chunks.size()));
        return Map.copyOf(counts);
    }
}
