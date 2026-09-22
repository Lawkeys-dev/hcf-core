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
 * The wall around a locked claim: red glass on its border, shown to the players the
 * lock refuses and to nobody else - the project owner's choice, 22/09/2026, in place
 * of a claim that only pushed players back with a message.
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
 * claim's wall is worked out once and <em>kept</em> ({@link Wall}), and a step only
 * cuts that to what is near the player and sends the difference - no world reads at
 * all. Which makes it cheap enough to redraw as they move, every few ticks, rather
 * than once a second.
 */
public final class LockWalls {

    /** How long a claim's wall is kept before the world is read again for it. */
    private static final long CACHE_MILLIS = 30_000L;

    /** One claim's wall, worked out once: every block of it, whatever the player. */
    private record Wall(Map<Location, BlockData> blocks, long builtAt, String material) {
    }

    private final ClaimModule module;
    private final ClientBlocks view;
    private final Map<UUID, Wall> walls = new ConcurrentHashMap<>();
    /** The block each player was last drawn for: a step that stays in it changes nothing. */
    private final Map<UUID, Long> drawnAt = new ConcurrentHashMap<>();
    private BukkitTask task;

    public LockWalls(ClaimModule module, ClientBlocks view) {
        this.module = Objects.requireNonNull(module, "module");
        this.view = Objects.requireNonNull(view, "view");
    }

    /** Starts drawing, or restarts it with the settings as they now are. */
    public void start() {
        stop();
        ClaimSettings.LockRules rules = module.getSettings().lock();
        if (!rules.wallEnabled()) {
            return;
        }
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
        walls.clear();
        drawnAt.clear();
        view.clearAll(Bukkit.getOnlinePlayers());
    }

    private void drawAll() {
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
        if (manager == null || !rules.wallEnabled()) {
            return;
        }
        Map<Location, BlockData> wall = wallFor(manager, rules, player);
        if (!wall.isEmpty() || view.isShowing(player.getUniqueId())) {
            view.show(player, wall);
        }
    }

    /** @return the wall this player should see now: the border of every locked claim near them */
    private Map<Location, BlockData> wallFor(ClaimManager manager, ClaimSettings.LockRules rules, Player player) {
        Map<Location, BlockData> wall = new HashMap<>();
        World world = player.getWorld();
        Location at = player.getLocation();
        int radius = rules.radiusBlocks();
        for (ClaimArea claim : nearbyClaims(manager, world.getName(), at.getBlockX(), at.getBlockZ(),
                rules.showWithinBlocks())) {
            if (BorderColumns.distanceTo(claim, at.getBlockX(), at.getBlockZ()) > rules.showWithinBlocks()) {
                continue; // Too far to be about to walk in: the wall appears as they come near.
            }
            if (manager.lockedAgainst(world.getName(), claim.minX(), claim.minZ(), player.getUniqueId()).isEmpty()) {
                continue;
            }
            for (Map.Entry<Location, BlockData> block : wallOf(claim, world, rules).entrySet()) {
                Location where = block.getKey();
                if (Math.abs(where.getBlockX() - at.getBlockX()) <= radius
                        && Math.abs(where.getBlockZ() - at.getBlockZ()) <= radius) {
                    wall.put(where, block.getValue());
                }
            }
        }
        return wall;
    }

    /**
     * @return every block of this claim's wall, read from the world once and kept for
     *         {@link #CACHE_MILLIS}; a claim whose ground is dug out draws its old
     *         wall until then, which is a wall standing a block too low, not a wall
     *         that lets anybody through - the lock itself is a movement rule
     */
    private Map<Location, BlockData> wallOf(ClaimArea claim, World world, ClaimSettings.LockRules rules) {
        Wall kept = walls.get(claim.id());
        long now = System.currentTimeMillis();
        if (kept != null && now - kept.builtAt() < CACHE_MILLIS && kept.material().equals(rules.material())) {
            return kept.blocks();
        }
        Material material = Material.matchMaterial(rules.material());
        BlockData glass = (material == null || !material.isBlock() ? Material.RED_STAINED_GLASS : material)
                .createBlockData();
        Map<Location, BlockData> blocks = new HashMap<>();
        // The whole border: cropped to the player afterwards, so one read serves
        // every player walking around the claim.
        for (int[] column : BorderColumns.outline(claim, claim.centreX(), claim.centreZ(), Integer.MAX_VALUE / 4)) {
            int[] range = ColumnHeights.range(world.getHighestBlockYAt(column[0], column[1]),
                    rules.topY(), rules.minimumHeight(), world.getMaxHeight());
            if (range == null) {
                continue;
            }
            for (int y = range[0]; y <= range[1]; y++) {
                Location block = new Location(world, column[0], y, column[1]);
                if (block.getBlock().getType().isAir()) {
                    blocks.put(block, glass);
                }
            }
        }
        walls.put(claim.id(), new Wall(blocks, now, rules.material()));
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
