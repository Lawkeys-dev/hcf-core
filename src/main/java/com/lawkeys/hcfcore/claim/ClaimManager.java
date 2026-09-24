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
 * <p><strong>Territory is drawn block by block</strong> since 22/09/2026: a claim
 * is a rectangle, full height ({@link ClaimArea}), made with the claiming wand and
 * paid from the team bank.
 *
 * <p><strong>The central invariant</strong>, from FEATURES.md section 3: land's
 * owner is persistent and is never changed by raiding. Whether that
 * owner is currently protected is a separate, dynamic question, answered per
 * check by {@link RaidabilityPolicy}. Two things follow, and both are enforced
 * here and covered by tests:
 * <ul>
 *   <li>{@link #claim} refuses any land that already has an owner - including a
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

    /** Every claim, by its id: the authoritative set. */
    private final Map<UUID, ClaimArea> byId = new ConcurrentHashMap<>();
    /**
     * The hot-path lookup for protection checks: the claims covering each chunk, even
     * in part. A block's owner is found by testing the few rectangles of its chunk,
     * never by walking every claim on the server.
     */
    private final Map<ChunkPosition, List<ClaimArea>> byChunk = new ConcurrentHashMap<>();
    /** Reverse index, so per-team operations do not scan every claim on the server. */
    private final Map<UUID, List<ClaimArea>> byTeam = new ConcurrentHashMap<>();
    private final Map<UUID, Map<HomeType, TeamHome>> homesByTeam = new ConcurrentHashMap<>();

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
    public Optional<Team> lockedAgainst(String world, int x, int z, UUID player) {
        return getOwner(world, x, z).filter(owner -> isLocked(owner.getId()) && !owner.isMember(player));
    }

    // ------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------

    /** @return the claim that block column belongs to, or empty for unclaimed land */
    public Optional<ClaimArea> getClaimAt(String world, int x, int z) {
        List<ClaimArea> here = byChunk.get(ChunkPosition.fromBlock(world, x, z));
        if (here != null) {
            for (ClaimArea area : here) {
                if (area.contains(world, x, z)) {
                    return Optional.of(area);
                }
            }
        }
        return Optional.empty();
    }

    /** @return the id of the team owning that block column, or empty for unclaimed land */
    public Optional<UUID> getOwnerId(String world, int x, int z) {
        UUID owner = ownerAt(world, x, z);
        return Optional.ofNullable(owner);
    }

    /** Allocation-free owner lookup for the hot paths: {@code null} for unclaimed land. */
    private UUID ownerAt(String world, int x, int z) {
        if (world == null) {
            return null;
        }
        List<ClaimArea> here = byChunk.get(ChunkPosition.fromBlock(world, x, z));
        if (here == null) {
            return null;
        }
        for (int i = 0; i < here.size(); i++) {
            ClaimArea area = here.get(i);
            if (area.contains(world, x, z)) {
                return area.teamId();
            }
        }
        return null;
    }

    /** @return the team owning that block column, or empty for unclaimed land */
    public Optional<Team> getOwner(String world, int x, int z) {
        return getOwnerId(world, x, z).flatMap(teams::getTeam);
    }

    public boolean isClaimed(String world, int x, int z) {
        return ownerAt(world, x, z) != null;
    }

    /** @return the claims of {@code teamId}, oldest first */
    public List<ClaimArea> getClaims(UUID teamId) {
        return byTeam.getOrDefault(teamId, List.of());
    }

    /** @return the claims covering that chunk, even in part */
    public List<ClaimArea> getClaimsIn(ChunkPosition chunk) {
        return byChunk.getOrDefault(chunk, List.of());
    }

    /** @return every chunk the claims of {@code teamId} cover, even in part */
    public Set<ChunkPosition> getClaimChunks(UUID teamId) {
        Set<ChunkPosition> chunks = new LinkedHashSet<>();
        for (ClaimArea area : getClaims(teamId)) {
            chunks.addAll(area.chunks());
        }
        return chunks;
    }

    /** @return how many separate claims {@code teamId} holds */
    public int getClaimCount(UUID teamId) {
        return getClaims(teamId).size();
    }

    /** @return how many blocks of surface {@code teamId} holds */
    public long getClaimedArea(UUID teamId) {
        long total = 0;
        for (ClaimArea area : getClaims(teamId)) {
            total += area.area();
        }
        return total;
    }

    public int getTotalClaimCount() {
        return byId.size();
    }

    // ------------------------------------------------------------------
    // Protection - the reclaim rule
    // ------------------------------------------------------------------

    /**
     * Decides whether {@code actorTeam} may modify blocks in that block column.
     *
     * <p>This is the check FEATURES.md section 3 specifies: it reads current
     * raidability, never a notion of unowned land, so a team regains protection
     * the moment its DTR goes back above zero without anything being re-claimed.
     *
     * @param actorTeam the actor's team, or {@code null} if they have none
     */
    public ProtectionResult checkProtection(Team actorTeam, String world, int x, int z) {
        if (!isEnforced()) {
            return ProtectionResult.ALLOWED;
        }
        return protectionOf(actorTeam, ownerAt(world, x, z));
    }

    private ProtectionResult protectionOf(Team actorTeam, UUID ownerId) {
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
    public ProtectionResult checkProtection(UUID actor, String world, int x, int z) {
        return checkProtection(teams.getTeamOf(actor).orElse(null), world, x, z);
    }

    /**
     * Decides whether {@code actorTeam} may place or break one particular block.
     *
     * <p>Claimed land follows its owner's rules, warzone or not. Unclaimed land is
     * where the warzone applies: inside it, building is refused unless the operator
     * allows it - except for the blocks of a region another system governs, such as
     * a Mountain, which must stay minable and enforces its own rules.
     *
     * <p>Interactions - doors, chests, buttons - are not building and keep using
     * {@link #checkProtection}: the warzone is public land, not somebody's property.
     *
     * @param actorTeam the actor's team, or {@code null} if they have none
     */
    public ProtectionResult checkBuild(Team actorTeam, String world, int x, int y, int z) {
        if (!isEnforced()) {
            return ProtectionResult.ALLOWED;
        }
        UUID owner = ownerAt(world, x, z);
        if (owner != null) {
            ProtectionResult owned = protectionOf(actorTeam, owner);
            // protectionOf drops an owner whose team no longer exists; what is left
            // is unclaimed land, which falls through to the warzone below.
            if (ownerAt(world, x, z) != null) {
                return owned;
            }
        }
        ClaimSettings.WarzoneRules warzone = config().warzone();
        if (!warzone.allowBuilding() && warzone.covers(world, x, z)
                && !reservedRegions.isInReservedRegion(world, x, y, z)) {
            return ProtectionResult.DENIED_WARZONE;
        }
        return ProtectionResult.ALLOWED;
    }

    /**
     * @return whether that block column is unclaimed warzone land. The warzone owns
     *         nothing - it is only a set of rules - so while the module is off there
     *         is none, and the map and border messages show wilderness instead
     */
    public boolean isWarzone(String world, int x, int z) {
        ClaimSettings config = config();
        return config.enabled() && ownerAt(world, x, z) == null && config.warzone().covers(world, x, z);
    }

    /**
     * @return whether two block columns of one world are the same territory, which is
     *         when a border announcement stays silent: the same owner, or both
     *         unclaimed and both warzone or both wilderness
     *
     * <p>Compared by identity, never by the names shown: the warzone's display name
     * is free text and may well be a team's name - a server team called Warzone,
     * typically - and crossing between the two would then read as no border at all.
     */
    public boolean isSameTerritory(String world, int ax, int az, int bx, int bz) {
        UUID ownerA = ownerAt(world, ax, az);
        UUID ownerB = ownerAt(world, bx, bz);
        if (ownerA != null || ownerB != null) {
            return Objects.equals(ownerA, ownerB);
        }
        return isWarzone(world, ax, az) == isWarzone(world, bx, bz);
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
     * <p>On unclaimed warzone, a charge must not do what a pickaxe is refused - on the
     * same blocks. The blocks of a region another system governs, such as a Mountain,
     * follow that system's rules for explosions as they do for mining.
     *
     * <p>The rule sits here rather than in the listener so that "does TNT break
     * this?" is answerable in a unit test with no server.
     */
    public boolean isExplosionProtected(String world, int x, int y, int z) {
        if (!isEnforced() || !config().protection().blockExplosions()) {
            return false;
        }
        UUID owner = ownerAt(world, x, z);
        if (owner == null) {
            ClaimSettings.WarzoneRules warzone = config().warzone();
            return !warzone.allowBuilding() && warzone.covers(world, x, z)
                    && !reservedRegions.isInReservedRegion(world, x, y, z);
        }
        return !protectionOf(null, owner).isAllowed();
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
     * <p><strong>Hot path.</strong> Every flowing liquid asks this. Within one block
     * column the answer is always yes; otherwise two lookups, and the build check
     * only across a border.
     *
     * @param world the world both blocks are in
     */
    public boolean mayReach(String world, int fromX, int fromZ, int toX, int toY, int toZ) {
        if (fromX == toX && fromZ == toZ) {
            return true;
        }
        if (!isEnforced()) {
            return true;
        }
        if (isSameTerritory(world, fromX, fromZ, toX, toZ)) {
            return true;
        }
        UUID sourceOwner = ownerAt(world, fromX, fromZ);
        Team source = sourceOwner == null ? null : teams.getTeam(sourceOwner).orElse(null);
        if (source != null && source.getType().isSystem() && !isPlayerLand(world, toX, toZ)) {
            return true;
        }
        return checkBuild(source, world, toX, toY, toZ).isAllowed();
    }

    /** @return whether a player team - not the server - owns that block column */
    private boolean isPlayerLand(String world, int x, int z) {
        UUID ownerId = ownerAt(world, x, z);
        return ownerId != null && teams.getTeam(ownerId).map(owner -> !owner.getType().isSystem()).orElse(false);
    }

    // ------------------------------------------------------------------
    // Claiming
    // ------------------------------------------------------------------

    /**
     * @return what claiming that rectangle would cost {@code team}: nothing for server
     *         land or a staff override ({@code actor} {@code null})
     */
    public double priceOf(Team team, UUID actor, long area) {
        if (actor == null || team.getType().isSystem()) {
            return 0.0;
        }
        return config().price().priceOf(area);
    }

    /**
     * Claims a rectangle for {@code team}, between two corners given in any order,
     * paid from its bank. Every rule is checked before anything changes, so a
     * refused claim costs nothing and changes nothing.
     *
     * @param actor {@code null} for a staff override, which skips the role check,
     *              the phase, the size rules and the price, but still cannot claim
     *              over anybody's land
     */
    public TeamResult claim(Team team, UUID actor, String world, int x1, int z1, int x2, int z2) {
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(world, "world");
        ClaimSettings config = config();
        ClaimArea area = ClaimArea.between(team.getId(), world, x1, z1, x2, z2, 0.0, clock.getAsLong());

        Optional<TeamResult> refused = refusal(team, actor, area, config);
        if (refused.isPresent()) {
            return refused.get();
        }
        double price = priceOf(team, actor, area.area());
        if (price > 0 && !teams.payForClaim(team, price)) {
            return TeamResult.fail(ClaimMessages.CLAIM_CANNOT_AFFORD,
                    "cost", teams.formatAmount(price), "balance", teams.formatAmount(team.getBalance()));
        }
        ClaimArea paid = new ClaimArea(area.id(), area.teamId(), area.world(), area.minX(), area.minZ(),
                area.maxX(), area.maxZ(), price, area.claimedAt());
        add(paid);
        markDirty(team.getId());
        return TeamResult.ok(ClaimMessages.CLAIM_SUCCESS, team,
                "size", area.width() + "x" + area.length(),
                "area", String.valueOf(area.area()),
                "cost", teams.formatAmount(price),
                "claims", String.valueOf(getClaimCount(team.getId())));
    }

    /**
     * @return why that rectangle may not be claimed for {@code team}, or empty when it
     *         may - asked by the wand before it shows a price, and by {@link #claim}
     */
    public Optional<TeamResult> refusal(Team team, UUID actor, String world, int x1, int z1, int x2, int z2) {
        return refusal(team, actor, ClaimArea.between(team.getId(), world, x1, z1, x2, z2, 0.0, 0L), config());
    }

    private Optional<TeamResult> refusal(Team team, UUID actor, ClaimArea area, ClaimSettings config) {
        if (!config.enabled()) {
            return Optional.of(TeamResult.fail(ClaimMessages.CLAIM_DISABLED));
        }
        // Players only: staff claiming server land is never closed by a phase.
        if (actor != null) {
            Optional<String> closed = claiming.refusal();
            if (closed.isPresent()) {
                return Optional.of(TeamResult.fail(closed.get()));
            }
        }
        if (team.getType().isSystem() && actor != null) {
            return Optional.of(TeamResult.fail(ClaimMessages.CLAIM_SYSTEM_TEAM, "team", team.getName()));
        }
        Optional<TeamResult> denied = checkRole(team, actor, ClaimAction.CLAIM);
        if (denied.isPresent()) {
            return denied;
        }
        if (!config.isClaimable(area.world())) {
            return Optional.of(TeamResult.fail(ClaimMessages.CLAIM_WORLD_DISABLED, "world", area.world()));
        }
        // Sizes are how players share the map; staff drawing server land - a road a
        // few blocks wide, a whole spawn - follow none of them.
        if (actor != null && !team.getType().isSystem()) {
            ClaimSettings.SizeRules sizes = config.sizes();
            if (area.width() < sizes.minSide() || area.length() < sizes.minSide()) {
                return Optional.of(TeamResult.fail(ClaimMessages.CLAIM_TOO_SMALL,
                        "min", String.valueOf(sizes.minSide()), "size", area.width() + "x" + area.length()));
            }
            if (sizes.maxSide() > 0 && (area.width() > sizes.maxSide() || area.length() > sizes.maxSide())) {
                return Optional.of(TeamResult.fail(ClaimMessages.CLAIM_TOO_BIG,
                        "max", String.valueOf(sizes.maxSide()), "size", area.width() + "x" + area.length()));
            }
            if (sizes.maxClaims() > 0 && getClaimCount(team.getId()) >= sizes.maxClaims()) {
                return Optional.of(TeamResult.fail(ClaimMessages.CLAIM_TOO_MANY_CLAIMS,
                        "max", String.valueOf(sizes.maxClaims())));
            }
            if (sizes.maxTotalArea() > 0 && getClaimedArea(team.getId()) + area.area() > sizes.maxTotalArea()) {
                return Optional.of(TeamResult.fail(ClaimMessages.CLAIM_LIMIT_REACHED,
                        "max", String.valueOf(sizes.maxTotalArea()),
                        "current", String.valueOf(getClaimedArea(team.getId()))));
            }
        }
        // Asked before ownership, because reserved land is not land anybody can hold:
        // an event region is out of the claim system entirely.
        Optional<String> reserved = reservedRegions.reservedRegionIn(
                area.world(), area.minX(), area.minZ(), area.maxX(), area.maxZ());
        if (reserved.isPresent()) {
            return Optional.of(TeamResult.fail(ClaimMessages.CLAIM_RESERVED_REGION,
                    "claim", area.toString(), "region", reserved.get()));
        }
        // Over-claiming is refused here, before anything else about the target team is
        // considered - being raidable never makes land available.
        for (ClaimArea other : overlapping(area)) {
            if (other.teamId().equals(team.getId())) {
                return Optional.of(TeamResult.fail(ClaimMessages.CLAIM_ALREADY_YOURS, "claim", other.toString()));
            }
            String ownerName = teams.getTeam(other.teamId()).map(Team::getName).orElse(other.teamId().toString());
            return Optional.of(TeamResult.fail(ClaimMessages.CLAIM_ALREADY_OWNED,
                    "claim", other.toString(), "team", ownerName));
        }
        // The warzone is server land nobody claims - staff included, for a player team.
        // A server team may: spawn sits at its centre, roads cross it.
        if (!team.getType().isSystem() && config.warzone().overlaps(area)) {
            return Optional.of(TeamResult.fail(ClaimMessages.CLAIM_WARZONE,
                    "claim", area.toString(), "warzone", config.warzone().displayName()));
        }
        // Placement rules - connected territory, a buffer to other teams - are how
        // players share the map. Server land is drawn by staff and follows neither.
        if (!team.getType().isSystem()) {
            return checkPlacement(team, area, config);
        }
        return Optional.empty();
    }

    /** Connectivity to the team's existing territory, and the buffer to other teams' claims. */
    private Optional<TeamResult> checkPlacement(Team team, ClaimArea area, ClaimSettings config) {
        if (config.placement().requireConnected()) {
            List<ClaimArea> sameWorld = getClaims(team.getId()).stream()
                    .filter(own -> own.world().equals(area.world())).toList();
            // A team may open a territory in a world it holds nothing in.
            if (!sameWorld.isEmpty() && sameWorld.stream().noneMatch(area::touches)) {
                return Optional.of(TeamResult.fail(ClaimMessages.CLAIM_NOT_CONNECTED));
            }
        }
        int buffer = config.placement().bufferBlocks();
        if (buffer > 0) {
            for (ClaimArea other : near(area, buffer)) {
                if (other.teamId().equals(team.getId()) || area.gapTo(other) >= buffer) {
                    continue;
                }
                Optional<Team> owner = teams.getTeam(other.teamId());
                // Server land is not a neighbour to keep away from: a base may sit on
                // the edge of a road.
                if (owner.isPresent() && owner.get().getType().isSystem()) {
                    continue;
                }
                return Optional.of(TeamResult.fail(ClaimMessages.CLAIM_TOO_CLOSE,
                        "team", owner.map(Team::getName).orElse(other.teamId().toString()),
                        "distance", String.valueOf(buffer)));
            }
        }
        return Optional.empty();
    }

    /** @return the claims sharing at least one block with {@code area} */
    private List<ClaimArea> overlapping(ClaimArea area) {
        return near(area, 0).stream().filter(area::overlaps).toList();
    }

    /**
     * @return the claims found in the chunks within {@code margin} blocks of
     *         {@code area} - a candidate list, bounded by the size of the area and
     *         not by how much of the map is claimed
     */
    private List<ClaimArea> near(ClaimArea area, int margin) {
        Set<ClaimArea> found = new LinkedHashSet<>();
        for (int cx = ChunkPosition.toChunk(area.minX() - margin); cx <= ChunkPosition.toChunk(area.maxX() + margin); cx++) {
            for (int cz = ChunkPosition.toChunk(area.minZ() - margin); cz <= ChunkPosition.toChunk(area.maxZ() + margin); cz++) {
                List<ClaimArea> here = byChunk.get(new ChunkPosition(area.world(), cx, cz));
                if (here != null) {
                    found.addAll(here);
                }
            }
        }
        return List.copyOf(found);
    }

    /**
     * Gives up the claim that block column belongs to - the whole claim - and refunds
     * part of what it cost.
     *
     * @param actor {@code null} for a staff override, which refunds nothing
     */
    public TeamResult unclaim(Team team, UUID actor, String world, int x, int z) {
        Objects.requireNonNull(team, "team");
        Optional<TeamResult> denied = checkRole(team, actor, ClaimAction.UNCLAIM);
        if (denied.isPresent()) {
            return denied.get();
        }
        Optional<ClaimArea> found = getClaimAt(world, x, z);
        if (found.isEmpty()) {
            return TeamResult.fail(ClaimMessages.UNCLAIM_NOT_CLAIMED);
        }
        ClaimArea area = found.get();
        if (!area.teamId().equals(team.getId())) {
            String ownerName = teams.getTeam(area.teamId()).map(Team::getName).orElse(area.teamId().toString());
            return TeamResult.fail(ClaimMessages.UNCLAIM_NOT_YOURS, "team", ownerName);
        }
        if (!config().placement().allowDisconnecting() && actor != null && wouldDisconnect(team.getId(), area)) {
            return TeamResult.fail(ClaimMessages.UNCLAIM_WOULD_DISCONNECT, "claim", area.toString());
        }
        remove(area);
        markDirty(team.getId());
        double refund = actor == null ? 0.0 : config().price().refundOf(area.pricePaid());
        teams.refundClaim(team, refund);
        return TeamResult.ok(ClaimMessages.UNCLAIM_SUCCESS, team,
                "claim", area.toString(), "size", area.width() + "x" + area.length(),
                "refund", teams.formatAmount(refund), "claims", String.valueOf(getClaimCount(team.getId())));
    }

    /**
     * @return {@code true} if giving up {@code area} would split the team's territory
     *         in that world into disconnected pieces
     */
    private boolean wouldDisconnect(UUID teamId, ClaimArea area) {
        List<ClaimArea> remaining = new ArrayList<>();
        for (ClaimArea own : getClaims(teamId)) {
            if (!own.id().equals(area.id()) && own.world().equals(area.world())) {
                remaining.add(own);
            }
        }
        if (remaining.size() <= 1) {
            return false;
        }
        // Flood fill from any remaining claim; if it does not reach them all, the
        // removal would have cut the territory in two.
        Set<ClaimArea> reached = new LinkedHashSet<>();
        List<ClaimArea> queue = new ArrayList<>();
        queue.add(remaining.get(0));
        reached.add(remaining.get(0));
        while (!queue.isEmpty()) {
            ClaimArea current = queue.remove(queue.size() - 1);
            for (ClaimArea other : remaining) {
                if (!reached.contains(other) && current.touches(other)) {
                    reached.add(other);
                    queue.add(other);
                }
            }
        }
        return reached.size() != remaining.size();
    }

    /**
     * Gives up every claim, refunding part of what each cost.
     *
     * @param actor {@code null} for a staff override, which refunds nothing
     */
    public TeamResult unclaimAll(Team team, UUID actor) {
        Objects.requireNonNull(team, "team");
        Optional<TeamResult> denied = checkRole(team, actor, ClaimAction.UNCLAIM);
        if (denied.isPresent()) {
            return denied.get();
        }
        List<ClaimArea> owned = getClaims(team.getId());
        if (owned.isEmpty()) {
            return TeamResult.fail(ClaimMessages.UNCLAIM_NOTHING);
        }
        double refund = 0;
        for (ClaimArea area : owned) {
            remove(area);
            if (actor != null) {
                refund += config().price().refundOf(area.pricePaid());
            }
        }
        markDirty(team.getId());
        teams.refundClaim(team, refund);
        return TeamResult.ok(ClaimMessages.UNCLAIM_ALL_SUCCESS, team,
                "count", String.valueOf(owned.size()), "refund", teams.formatAmount(refund));
    }

    /**
     * Drops every claim and home held by a team, with no permission check and no
     * refund.
     *
     * <p>Called when a team is disbanded, which is the one case where territory
     * disappears without anyone unclaiming it.
     *
     * @return the number of claims released
     */
    public int releaseAll(UUID teamId) {
        List<ClaimArea> owned = byTeam.getOrDefault(teamId, List.of());
        for (ClaimArea area : owned) {
            remove(area);
        }
        byTeam.remove(teamId);
        homesByTeam.remove(teamId);
        dirtyTeams.remove(teamId);
        deletedTeams.add(teamId);
        return owned.size();
    }

    /** Indexes a claim. Lists are replaced, never changed, so a lookup never sees one half-written. */
    private synchronized void add(ClaimArea area) {
        byId.put(area.id(), area);
        List<ClaimArea> own = new ArrayList<>(byTeam.getOrDefault(area.teamId(), List.of()));
        own.add(area);
        byTeam.put(area.teamId(), List.copyOf(own));
        for (ChunkPosition chunk : area.chunks()) {
            List<ClaimArea> here = new ArrayList<>(byChunk.getOrDefault(chunk, List.of()));
            here.add(area);
            byChunk.put(chunk, List.copyOf(here));
        }
    }

    private synchronized void remove(ClaimArea area) {
        byId.remove(area.id());
        List<ClaimArea> own = new ArrayList<>(byTeam.getOrDefault(area.teamId(), List.of()));
        own.removeIf(other -> other.id().equals(area.id()));
        if (own.isEmpty()) {
            byTeam.remove(area.teamId());
        } else {
            byTeam.put(area.teamId(), List.copyOf(own));
        }
        for (ChunkPosition chunk : area.chunks()) {
            List<ClaimArea> here = new ArrayList<>(byChunk.getOrDefault(chunk, List.of()));
            here.removeIf(other -> other.id().equals(area.id()));
            if (here.isEmpty()) {
                byChunk.remove(chunk);
            } else {
                byChunk.put(chunk, List.copyOf(here));
            }
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
            if (!team.getId().equals(ownerAt(position.world(),
                    (int) Math.floor(position.x()), (int) Math.floor(position.z())))) {
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
        // The team's own choice where it may make one (/team settings), else the server's.
        TeamRole required = teams.requiredRole(team, action.configKey(), config().requiredRole(action));
        if (!role.get().isAtLeast(required)) {
            return Optional.of(TeamResult.fail(ClaimMessages.INSUFFICIENT_ROLE,
                    "required", required.name(), "role", role.get().name()));
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    /**
     * Loads claims and homes into the cache. Blocking - async task only.
     *
     * <p>A database an earlier version wrote holds chunk claims: they are turned into
     * block claims here, once, saved, and the chunk rows forgotten
     * ({@link LegacyChunkClaims}).
     *
     * @return how many chunk claims were converted, {@code 0} on every later start
     */
    public int loadAll() throws Exception {
        store.initSchema();
        byId.clear();
        byChunk.clear();
        byTeam.clear();
        homesByTeam.clear();
        dirtyTeams.clear();
        deletedTeams.clear();

        for (ClaimArea area : store.loadClaims()) {
            add(area);
        }
        int converted = 0;
        Collection<Claim> legacy = store.loadLegacyChunkClaims();
        if (!legacy.isEmpty()) {
            Map<UUID, List<ChunkPosition>> chunksByTeam = new LinkedHashMap<>();
            Map<UUID, Long> since = new LinkedHashMap<>();
            for (Claim claim : legacy) {
                chunksByTeam.computeIfAbsent(claim.teamId(), ignored -> new ArrayList<>()).add(claim.chunk());
                since.merge(claim.teamId(), claim.claimedAt(), Math::min);
            }
            for (Map.Entry<UUID, List<ChunkPosition>> entry : chunksByTeam.entrySet()) {
                for (ClaimArea area : LegacyChunkClaims.toAreas(entry.getKey(), entry.getValue(),
                        since.get(entry.getKey()))) {
                    add(area);
                }
                store.saveClaims(entry.getKey(), getClaims(entry.getKey()));
            }
            store.clearLegacyChunkClaims();
            converted = legacy.size();
        }
        for (TeamHome home : store.loadHomes()) {
            homesByTeam.computeIfAbsent(home.teamId(), ignored -> new ConcurrentHashMap<>())
                    .put(home.type(), home);
        }
        return converted;
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
                store.saveClaims(teamId, getClaims(teamId));

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
        dirtyTeams.addAll(byTeam.keySet());
        dirtyTeams.addAll(homesByTeam.keySet());
    }

    /** @return every claim, for the map, Lunar Client and tests */
    public List<ClaimArea> getAllClaims() {
        return List.copyOf(byId.values());
    }

    /** @return how many claims each team holds */
    public Map<UUID, Integer> getClaimCounts() {
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        byTeam.forEach((teamId, areas) -> counts.put(teamId, areas.size()));
        return Map.copyOf(counts);
    }
}
