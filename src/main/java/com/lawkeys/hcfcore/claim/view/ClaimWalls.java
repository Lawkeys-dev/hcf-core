package com.lawkeys.hcfcore.claim.view;

import com.lawkeys.hcfcore.claim.ClaimArea;
import com.lawkeys.hcfcore.claim.ClaimManager;
import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.claim.ClaimSettings;
import com.lawkeys.hcfcore.util.ChunkPosition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The walls a player sees around land they may not enter: a locked claim (red glass,
 * to the players the lock refuses) and, while they are in combat, a safe zone - the
 * project owner's choices of 22/09/2026, in place of claims that only pushed players
 * back with a message.
 *
 * <p>Drawn like the claiming wand's columns: sent to that one player, never placed,
 * and only the part near them, so a lock on a large claim costs no more than a lock
 * on a small one. Members of the locking team see nothing.
 *
 * <p><strong>Why it is not drawn off the main thread.</strong> A wall is read from the
 * world - the ground's height, and which blocks up the column are air - and the
 * Bukkit API only allows that on the main thread. What was slow was doing it again
 * for every claim on every tick of a one-second timer: the wall lagged a step behind
 * the player walking into it (the project owner's report, 22/09/2026). So each
 * column of a wall is worked out once and <em>kept</em> ({@link Column}), only the
 * columns near the player are ever read - the far side of a large spawn is never
 * touched, nor its chunks loaded (found in review, 23/09/2026) - and a step sends
 * the difference. Which makes it cheap enough to redraw as they move rather than
 * once a second.
 */
public final class ClaimWalls {

    /** How long a claim's wall is kept before the world is read again for it. */
    private static final long CACHE_MILLIS = 30_000L;

    /** One column of a wall, worked out once: the blocks it is drawn in. */
    private record Column(Map<Location, BlockData> blocks, long builtAt) {
    }

    private final ClaimModule module;
    private final ClientBlocks view;
    private final Map<String, Column> columns = new ConcurrentHashMap<>();
    /** The block each player was last drawn for: a step that stays in it changes nothing. */
    private final Map<UUID, Long> drawnAt = new ConcurrentHashMap<>();
    private BukkitTask task;

    public ClaimWalls(ClaimModule module, ClientBlocks view) {
        this.module = Objects.requireNonNull(module, "module");
        this.view = Objects.requireNonNull(view, "view");
    }

    /** Starts drawing, or restarts it with the settings as they now are. */
    public void start() {
        stop();
        ClaimSettings.LockRules rules = module.getSettings().lock();
        long ticks = Math.max(1L, rules.refreshSeconds() * 20L);
        // The timer is the fallback: it catches a lock taken or released, and a claim
        // that changed shape. Walking is answered by redraw(), as it happens.
        this.task = Bukkit.getScheduler().runTaskTimer(module.getPlugin(), this::drawAll, ticks, ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        columns.clear();
        drawnAt.clear();
        view.clearAll(Bukkit.getOnlinePlayers());
    }

    private void drawAll() {
        // Old columns go: a wall is only kept for as long as somebody stands by it.
        long cutoff = System.currentTimeMillis() - CACHE_MILLIS;
        columns.values().removeIf(column -> column.builtAt() < cutoff);
        drawnAt.clear(); // The timer redraws everybody, whether they moved or not.
        for (Player player : Bukkit.getOnlinePlayers()) {
            draw(player);
        }
    }

    /**
     * A player moved: redraw them, unless they are still in the block they were last
     * drawn for. Called from the move listener, so the wall is there before they are.
     */
    public void redraw(Player player) {
        if (task == null) {
            return;
        }
        Location at = player.getLocation();
        long block = (long) at.getBlockX() << 32 | (at.getBlockZ() & 0xffffffffL);
        if (Objects.equals(drawnAt.get(player.getUniqueId()), block)) {
            return;
        }
        drawnAt.put(player.getUniqueId(), block);
        draw(player);
    }

    private void draw(Player player) {
        ClaimSettings.LockRules rules = module.getSettings().lock();
        ClaimManager manager = module.getManager();
        if (manager == null) {
            return;
        }
        Map<Location, BlockData> wall = wallFor(manager, rules, player);
        if (!wall.isEmpty() || view.isShowing(player.getUniqueId())) {
            view.show(player, wall);
        }
    }

    /**
     * @return the walls this player should see now: the border of every locked claim
     *         near them, and - while the PvP module says so - of the safe zones they
     *         may not enter
     */
    private Map<Location, BlockData> wallFor(ClaimManager manager, ClaimSettings.LockRules rules, Player player) {
        Map<Location, BlockData> wall = new HashMap<>();
        World world = player.getWorld();
        Location at = player.getLocation();
        java.util.Optional<SafeZoneWallPolicy.Wall> safeZone = module.getSafeZoneWallPolicy().wallFor(player);
        int look = Math.max(rules.showWithinBlocks(), safeZone.map(SafeZoneWallPolicy.Wall::widthBlocks).orElse(0));
        for (ClaimArea claim : nearbyClaims(manager, world.getName(), at.getBlockX(), at.getBlockZ(), look)) {
            int distance = BorderColumns.distanceTo(claim, at.getBlockX(), at.getBlockZ());
            boolean locked = rules.wallEnabled() && distance <= rules.showWithinBlocks()
                    && manager.lockedAgainst(world.getName(), claim.minX(), claim.minZ(),
                            player.getUniqueId()).isPresent();
            boolean safe = safeZone.isPresent() && distance <= safeZone.get().widthBlocks()
                    && module.getTeams().getManager().getTeam(claim.teamId())
                            .map(team -> team.isSafeZone()).orElse(false);
            if (!locked && !safe) {
                continue;
            }
            // A claim that is both keeps the lock's look: it is the stricter refusal.
            String material = locked ? rules.material() : safeZone.get().material();
            int topY = locked ? rules.topY() : safeZone.get().topY();
            int minimum = locked ? rules.minimumHeight() : safeZone.get().minimumHeight();
            int radius = locked ? rules.radiusBlocks() : safeZone.get().widthBlocks();
            // Only the border near the player is read from the world, column by column:
            // the far side of a large spawn is never touched, nor its chunks loaded.
            for (int[] column : BorderColumns.outline(claim, at.getBlockX(), at.getBlockZ(), radius)) {
                wall.putAll(columnOf(world, column[0], column[1], material, topY, minimum));
            }
        }
        return wall;
    }

    /**
     * @return one column of a wall: every air block from above the ground to the top,
     *         read from the world once and kept for {@link #CACHE_MILLIS}. A column
     *         whose ground is dug out draws its old wall until then - a wall a block
     *         too low, never a way in: the refusal is a movement rule
     */
    private Map<Location, BlockData> columnOf(World world, int x, int z, String materialName, int topY, int minimum) {
        String key = world.getName() + ':' + x + ':' + z + ':' + materialName + ':' + topY + ':' + minimum;
        long now = System.currentTimeMillis();
        Column kept = columns.get(key);
        if (kept != null && now - kept.builtAt() < CACHE_MILLIS) {
            return kept.blocks();
        }
        Material material = Material.matchMaterial(materialName);
        BlockData glass = (material == null || !material.isBlock() ? Material.RED_STAINED_GLASS : material)
                .createBlockData();
        Map<Location, BlockData> blocks = new HashMap<>();
        int[] range = ColumnHeights.range(world.getHighestBlockYAt(x, z), topY, minimum, world.getMaxHeight());
        if (range != null) {
            for (int y = range[0]; y <= range[1]; y++) {
                Location block = new Location(world, x, y, z);
                if (block.getBlock().getType().isAir()) {
                    blocks.put(block, glass);
                }
            }
        }
        columns.put(key, new Column(blocks, now));
        return blocks;
    }

    /** A player gone: their wall is nobody's, and the claims they were near stay cached. */
    public void forget(UUID playerId) {
        drawnAt.remove(playerId);
    }

    /** @return every claim with a block in the square around the player, each once */
    static List<ClaimArea> nearbyClaims(ClaimManager manager, String world, int x, int z, int radius) {
        Set<UUID> seen = new HashSet<>();
        List<ClaimArea> claims = new ArrayList<>();
        for (int cx = ChunkPosition.toChunk(x - radius); cx <= ChunkPosition.toChunk(x + radius); cx++) {
            for (int cz = ChunkPosition.toChunk(z - radius); cz <= ChunkPosition.toChunk(z + radius); cz++) {
                for (ClaimArea claim : manager.getClaimsIn(new ChunkPosition(world, cx, cz))) {
                    if (seen.add(claim.id())) {
                        claims.add(claim);
                    }
                }
            }
        }
        return claims;
    }
}
