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

/**
 * The wall around a locked claim: red glass on its border, shown to the players the
 * lock refuses and to nobody else - the project owner's choice, 22/09/2026, in place
 * of a claim that only pushed players back with a message.
 *
 * <p>Drawn like the claiming wand's columns: sent to that one player, never placed,
 * and only the part near them, so a lock on a large claim costs no more than a lock
 * on a small one. Members of the locking team see nothing: the wall is what the lock
 * means to everybody else.
 */
public final class LockWalls {

    private final ClaimModule module;
    private final ClientBlocks view;
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
        this.task = Bukkit.getScheduler().runTaskTimer(module.getPlugin(), this::draw, ticks, ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        view.clearAll(Bukkit.getOnlinePlayers());
    }

    private void draw() {
        ClaimSettings.LockRules rules = module.getSettings().lock();
        ClaimManager manager = module.getManager();
        if (manager == null || !rules.wallEnabled()) {
            return;
        }
        Material material = Material.matchMaterial(rules.material());
        BlockData glass = (material == null || !material.isBlock() ? Material.RED_STAINED_GLASS : material)
                .createBlockData();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<Location, BlockData> wall = wallFor(manager, rules, player, glass);
            if (!wall.isEmpty() || view.isShowing(player.getUniqueId())) {
                view.show(player, wall);
            }
        }
    }

    /** @return the wall this player should see now: the border of every locked claim near them */
    private Map<Location, BlockData> wallFor(ClaimManager manager, ClaimSettings.LockRules rules,
                                             Player player, BlockData glass) {
        Map<Location, BlockData> wall = new HashMap<>();
        World world = player.getWorld();
        Location at = player.getLocation();
        int radius = rules.radiusBlocks();
        for (ClaimArea claim : nearbyClaims(manager, world.getName(), at.getBlockX(), at.getBlockZ(), radius)) {
            if (manager.lockedAgainst(world.getName(), claim.minX(), claim.minZ(), player.getUniqueId()).isEmpty()) {
                continue;
            }
            for (int[] column : BorderColumns.outline(claim, at.getBlockX(), at.getBlockZ(), radius)) {
                int[] range = ColumnHeights.range(world.getHighestBlockYAt(column[0], column[1]),
                        rules.topY(), rules.minimumHeight(), world.getMaxHeight());
                if (range == null) {
                    continue;
                }
                for (int y = range[0]; y <= range[1]; y++) {
                    Location block = new Location(world, column[0], y, column[1]);
                    if (block.getBlock().getType().isAir()) {
                        wall.put(block, glass);
                    }
                }
            }
        }
        return wall;
    }

    /** @return every claim with a block in the square around the player, each once */
    static List<ClaimArea> nearbyClaims(ClaimManager manager, String world, int x, int z, int radius) {
        Set<java.util.UUID> seen = new HashSet<>();
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
