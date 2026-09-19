package com.lawkeys.hcfcore.claim;

import com.lawkeys.hcfcore.team.TeamRole;
import com.lawkeys.hcfcore.util.ChunkPosition;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable snapshot of {@code claims.yml}.
 *
 * <p>Held by {@link ClaimManager} through a {@code Supplier}, so {@code /hcf
 * reload} swaps it and every later decision uses the new values
 * (ARCHITECTURE.md section 2). A limit of {@code 0} means "no limit"
 * throughout, consistent with {@code teams.yml}.
 *
 * @param claimableWorlds worlds where claiming is allowed; empty means all
 * @param warzone         the server land around each world's centre that no player
 *                        team may claim
 */
public record ClaimSettings(
        boolean enabled,
        LimitRules limits,
        PlacementRules placement,
        ProtectionRules protection,
        HomeRules homes,
        Set<String> claimableWorlds,
        Map<ClaimAction, TeamRole> requiredRoles,
        WarzoneRules warzone,
        StuckRules stuck) {

    public ClaimSettings {
        Objects.requireNonNull(warzone, "warzone");
        Objects.requireNonNull(stuck, "stuck");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(protection, "protection");
        Objects.requireNonNull(homes, "homes");
        claimableWorlds = Set.copyOf(Objects.requireNonNull(claimableWorlds, "claimableWorlds"));
        requiredRoles = Map.copyOf(Objects.requireNonNull(requiredRoles, "requiredRoles"));
    }

    /**
     * How many chunks a team may own.
     *
     * @param base           chunks every team may claim regardless of size
     * @param perMember      extra chunks granted per member; {@code 0} disables scaling
     * @param maximum        hard cap whatever the member count; {@code 0} means uncapped
     * @param maxPerCommand  chunks a single command may claim at once, to stop a
     *                       stray radius from swallowing the map in one keystroke
     */
    public record LimitRules(int base, int perMember, int maximum, int maxPerCommand) {
    }

    /**
     * Where a claim may be placed.
     *
     * @param requireConnected      new chunks must touch the team's existing territory in that world
     * @param minimumDistanceToOthers minimum chunk distance to another team's claims; {@code 0} disables
     * @param allowDisconnecting    whether unclaiming may split a team's territory in two
     */
    public record PlacementRules(boolean requireConnected, int minimumDistanceToOthers,
                                 boolean allowDisconnecting) {
    }

    /**
     * @param allowAllyBuild    allies may build in the team's territory
     * @param allowRaidBuilding attackers may build/break inside a raidable team's
     *                          territory - the pillage window of FEATURES.md section 3
     * @param announceTerritory tell players when they cross a territory border
     * @param blockExplosions   explosions cannot break blocks in protected
     *                          territory. On by default: without it every rule
     *                          above is optional, since a stick of TNT does what
     *                          the player was refused
     */
    public record ProtectionRules(boolean allowAllyBuild, boolean allowRaidBuilding,
                                  boolean announceTerritory, boolean blockExplosions) {
    }

    /**
     * @param requireInsideTerritory a home must sit on a chunk the team owns
     * @param warmupSeconds          the countdown before {@code /team hq} or
     *                               {@code /team base} teleports, which damage or
     *                               movement cancels; {@code 0} is immediate
     */
    public record HomeRules(boolean enabled, boolean requireInsideTerritory, long warmupSeconds) {
    }

    /**
     * @param warmupSeconds the countdown before {@code /team stuck} moves a player,
     *                      which damage or movement cancels. It is the one way out of
     *                      a base short of breaking it, so without a countdown it
     *                      would be a free exit from any trap
     */
    public record StuckRules(long warmupSeconds) {
    }

    /**
     * The warzone: a square of land around each world's centre that no player team
     * may claim, and that nobody builds on unless the operator allows it. PvP is on
     * there, as anywhere that is not a safe zone.
     *
     * <p><strong>Applied per chunk.</strong> The radius is written in blocks, as
     * HCF operators think of it, and the square is rounded outwards to whole
     * chunks. Territory is chunk-based everywhere else - claims, {@code /team map},
     * border announcements - so a chunk-aligned warzone has one border, the same
     * whether a claim is refused, a block is protected or a map is drawn.
     *
     * <p><strong>Claimed land wins.</strong> The warzone only governs land nobody
     * owns. A server team claimed inside it - spawn at its centre, a road across it
     * - keeps its own rules, and so does a player team that held land there before
     * the warzone was configured: ownership never changes.
     *
     * @param displayName   what players see when they cross into it
     * @param allowBuilding whether its unclaimed land may be built on
     * @param areas         per world; a world with no entry has no warzone
     */
    public record WarzoneRules(String displayName, boolean allowBuilding, Map<String, Area> areas) {

        public WarzoneRules {
            Objects.requireNonNull(displayName, "displayName");
            areas = Map.copyOf(Objects.requireNonNull(areas, "areas"));
        }

        /** No warzone anywhere: the default, so installing the plugin reserves nothing. */
        public static WarzoneRules none() {
            return new WarzoneRules("{error}Warzone", false, Map.of());
        }

        /**
         * @return whether any block of {@code chunk} lies within its world's warzone.
         *         Allocation-free: asked for unclaimed land on block events
         */
        public boolean covers(ChunkPosition chunk) {
            Area area = areas.get(chunk.world());
            if (area == null) {
                return false;
            }
            int minX = chunk.minBlockX();
            int minZ = chunk.minBlockZ();
            // A chunk is 16 blocks: [min, min + 15] on each axis.
            return minX + 15 >= area.centerX() - area.radius() && minX <= area.centerX() + area.radius()
                    && minZ + 15 >= area.centerZ() - area.radius() && minZ <= area.centerZ() + area.radius();
        }

        /**
         * @param radius blocks from the centre to each edge of the square, at least 1
         */
        public record Area(int centerX, int centerZ, int radius) {

            public Area {
                if (radius <= 0) {
                    throw new IllegalArgumentException("a warzone radius must be positive: " + radius);
                }
            }
        }
    }

    public TeamRole requiredRole(ClaimAction action) {
        return requiredRoles.getOrDefault(action, TeamRole.LEADER);
    }

    /** @return whether {@code world} may be claimed; an empty allow-list means every world. */
    public boolean isClaimable(String world) {
        return claimableWorlds.isEmpty() || claimableWorlds.contains(world);
    }

    /**
     * Resolves a team's claim allowance from its member count.
     *
     * @return the maximum number of chunks, or {@code 0} for unlimited
     */
    public int maxClaimsFor(int memberCount) {
        if (limits.perMember() <= 0) {
            return limits.maximum() > 0 ? Math.min(limits.base(), limits.maximum()) : limits.base();
        }
        long allowance = (long) limits.base() + (long) limits.perMember() * memberCount;
        if (limits.maximum() > 0) {
            allowance = Math.min(allowance, limits.maximum());
        }
        return (int) Math.min(allowance, Integer.MAX_VALUE);
    }

    /** Built-in fallback, mirroring the defaults shipped in {@code resources/claims.yml}. */
    public static ClaimSettings defaults() {
        Map<ClaimAction, TeamRole> roles = new EnumMap<>(ClaimAction.class);
        roles.put(ClaimAction.CLAIM, TeamRole.CO_LEADER);
        roles.put(ClaimAction.UNCLAIM, TeamRole.LEADER);
        roles.put(ClaimAction.SET_HOME, TeamRole.CO_LEADER);
        roles.put(ClaimAction.LOCK_CLAIM, TeamRole.CO_LEADER);

        return new ClaimSettings(
                true,
                new LimitRules(16, 4, 0, 64),
                new PlacementRules(true, 2, false),
                new ProtectionRules(false, true, true, true),
                new HomeRules(true, true, 10L),
                Set.of(),
                roles,
                WarzoneRules.none(),
                new StuckRules(60L));
    }
}
