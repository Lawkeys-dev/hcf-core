package com.lawkeys.hcfcore.claim;

import com.lawkeys.hcfcore.team.TeamRole;

import java.util.EnumMap;
import java.util.List;
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
 * @param map             how {@code /team map} is drawn: pillars in the world, or cells in the chat
 */
public record ClaimSettings(
        boolean enabled,
        SizeRules sizes,
        PriceRules price,
        PlacementRules placement,
        ProtectionRules protection,
        HomeRules homes,
        Set<String> claimableWorlds,
        Map<ClaimAction, TeamRole> requiredRoles,
        WarzoneRules warzone,
        StuckRules stuck,
        WandRules wand,
        LockRules lock,
        MapRules map) {

    public ClaimSettings {
        Objects.requireNonNull(warzone, "warzone");
        Objects.requireNonNull(stuck, "stuck");
        Objects.requireNonNull(sizes, "sizes");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(wand, "wand");
        Objects.requireNonNull(lock, "lock");
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(protection, "protection");
        Objects.requireNonNull(homes, "homes");
        claimableWorlds = Set.copyOf(Objects.requireNonNull(claimableWorlds, "claimableWorlds"));
        requiredRoles = Map.copyOf(Objects.requireNonNull(requiredRoles, "requiredRoles"));
    }

    /**
     * How big a claim may be. Since claims are drawn block by block (22/09/2026),
     * money is what limits a team's land - the project owner's choice - and these
     * only keep each claim sensible. {@code 0} means no limit.
     *
     * @param minSide      the shortest a side may be, in blocks: a 1-block-wide strip
     *                     around an enemy base is not territory
     * @param maxSide      the longest a side may be, in blocks
     * @param maxClaims    how many separate claims one team may hold
     * @param maxTotalArea how many blocks of surface one team may hold in all
     */
    public record SizeRules(int minSide, int maxSide, int maxClaims, long maxTotalArea) {
    }

    /**
     * What a claim costs, paid from the team bank when it is made.
     *
     * @param perBlock      the price of one block of surface; {@code 0} makes claiming free
     * @param refundPercent the share of what was paid that an unclaim gives back, 0 to 100
     */
    public record PriceRules(double perBlock, double refundPercent) {

        /** @return the price of a claim of that many blocks */
        public double priceOf(long area) {
            return perBlock * area;
        }

        /** @return what giving back a claim that cost {@code paid} returns */
        public double refundOf(double paid) {
            return paid * refundPercent / 100.0;
        }
    }

    /**
     * The claiming wand - the traditional HCF way of drawing a claim: left-click one
     * corner, right-click the other, sneak and left-click to confirm, drop it to give up.
     *
     * @param material       the item
     * @param name           its name
     * @param lore           its description
     * @param pillarMaterial the column shown - to its holder only - on each chosen corner
     * @param pillarHeight   how many blocks high those columns rise above the corner
     * @param pillarMarkerMaterial every {@code pillarMarkerEvery}-th block of a column is
     *                             this instead, so the column is still seen through a
     *                             resource pack that clears glass (the project owner's
     *                             report, 22/09/2026)
     */
    public record WandRules(String material, String name, List<String> lore, String pillarMaterial,
                            String pillarMarkerMaterial, int pillarMarkerEvery, int pillarHeight) {

        public WandRules {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(name, "name");
            lore = List.copyOf(Objects.requireNonNull(lore, "lore"));
            Objects.requireNonNull(pillarMaterial, "pillarMaterial");
            Objects.requireNonNull(pillarMarkerMaterial, "pillarMarkerMaterial");
            pillarMarkerEvery = Math.max(1, pillarMarkerEvery);
        }
    }

    /**
     * The wall around a locked claim ({@code /team lockclaim}).
     *
     * @param radiusBlocks how much of the border to draw around the player: the rest
     *                     is too far to be seen, and drawing it would cost for nothing
     */
    public record LockRules(boolean wallEnabled, String material, int height, int radiusBlocks,
                            long refreshSeconds) {

        public LockRules {
            Objects.requireNonNull(material, "material");
            height = Math.max(1, Math.min(32, height));
            radiusBlocks = Math.max(4, Math.min(128, radiusBlocks));
            refreshSeconds = Math.max(1L, Math.min(60L, refreshSeconds));
        }
    }

    /** How {@code /team map} is drawn. */
    public enum MapStyle {
        /** Columns on the corners of nearby claims, shown to that player only. */
        PILLARS,
        /** The grid of cells in the chat. */
        CHAT;

        public static MapStyle of(String raw) {
            for (MapStyle style : values()) {
                if (style.name().equalsIgnoreCase(raw == null ? "" : raw.trim())) {
                    return style;
                }
            }
            return null;
        }
    }

    /**
     * {@code /team map}.
     *
     * @param cellBlocks   how many blocks one cell of the chat map stands for
     * @param chatRadiusX  how many cells the chat map draws each way
     * @param radiusChunks how far the pillars look for claims, in chunks
     * @param materials    the full blocks the pillars may be made of: one is drawn at
     *                     random for each team, each time the map is asked for
     */
    public record MapRules(MapStyle style, int cellBlocks, int chatRadiusX, int chatRadiusZ,
                           int radiusChunks, int pillarHeight, long seconds, List<String> materials) {

        public MapRules {
            Objects.requireNonNull(style, "style");
            cellBlocks = Math.max(1, Math.min(64, cellBlocks));
            chatRadiusX = Math.max(1, Math.min(32, chatRadiusX));
            chatRadiusZ = Math.max(1, Math.min(32, chatRadiusZ));
            radiusChunks = Math.max(1, Math.min(16, radiusChunks));
            pillarHeight = Math.max(1, Math.min(64, pillarHeight));
            seconds = Math.max(1L, Math.min(600L, seconds));
            materials = List.copyOf(Objects.requireNonNull(materials, "materials"));
        }
    }

    /**
     * Where a claim may be placed.
     *
     * @param requireConnected      a new claim must share an edge with the team's existing
     *                              territory in that world
     * @param bufferBlocks          blocks of land to keep between a claim and another team's;
     *                              {@code 0} lets teams claim right up against each other
     * @param allowDisconnecting    whether unclaiming may split a team's territory in two
     */
    public record PlacementRules(boolean requireConnected, int bufferBlocks,
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
     * <p><strong>Block-precise</strong>, like claims since 22/09/2026: the square runs
     * exactly {@code radius} blocks from the centre to each edge. It used to be
     * rounded outwards to whole chunks, when territory was drawn by chunk.
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
         * @return whether that block column lies within its world's warzone.
         *         Allocation-free: asked for unclaimed land on block events
         */
        public boolean covers(String world, int x, int z) {
            Area area = areas.get(world);
            return area != null
                    && Math.abs((long) x - area.centerX()) <= area.radius()
                    && Math.abs((long) z - area.centerZ()) <= area.radius();
        }

        /** @return whether any block of that rectangle lies within its world's warzone */
        public boolean overlaps(ClaimArea claim) {
            Area area = areas.get(claim.world());
            return area != null
                    && claim.maxX() >= area.centerX() - area.radius() && claim.minX() <= area.centerX() + area.radius()
                    && claim.maxZ() >= area.centerZ() - area.radius() && claim.minZ() <= area.centerZ() + area.radius();
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

    /** Built-in fallback, mirroring the defaults shipped in {@code resources/claims.yml}. */
    public static ClaimSettings defaults() {
        Map<ClaimAction, TeamRole> roles = new EnumMap<>(ClaimAction.class);
        roles.put(ClaimAction.CLAIM, TeamRole.CO_LEADER);
        roles.put(ClaimAction.UNCLAIM, TeamRole.LEADER);
        roles.put(ClaimAction.SET_HOME, TeamRole.CO_LEADER);
        roles.put(ClaimAction.LOCK_CLAIM, TeamRole.CO_LEADER);

        return new ClaimSettings(
                true,
                new SizeRules(5, 128, 0, 0L),
                new PriceRules(0.25, 75.0),
                new PlacementRules(true, 8, false),
                new ProtectionRules(false, true, true, true),
                new HomeRules(true, true, 10L),
                Set.of(),
                roles,
                WarzoneRules.none(),
                new StuckRules(60L),
                new WandRules("GOLDEN_HOE", "{primary}&lClaiming Wand", List.of(
                        "{muted}Left-click {dark}{bullet} {secondary}first corner",
                        "{muted}Right-click {dark}{bullet} {secondary}second corner",
                        "{muted}Sneak + left-click {dark}{bullet} {success}claim it",
                        "{muted}Drop it {dark}{bullet} {error}give up"), "GLASS", "GLOWSTONE", 6, 12),
                new LockRules(true, "RED_STAINED_GLASS", 3, 24, 1L),
                new MapRules(MapStyle.PILLARS, 8, 12, 6, 2, 12, 20L, List.of(
                        "LIME_CONCRETE", "RED_CONCRETE", "BLUE_CONCRETE", "YELLOW_CONCRETE", "PURPLE_CONCRETE",
                        "ORANGE_CONCRETE", "PINK_CONCRETE", "CYAN_CONCRETE", "MAGENTA_CONCRETE", "BROWN_CONCRETE",
                        "LIGHT_BLUE_CONCRETE", "GREEN_CONCRETE")));
    }
}
