package com.lawkeys.hcfcore.claim.view;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blocks shown to one player and to nobody else: the claiming wand's corner columns,
 * {@code /team map}'s pillars, a locked claim's wall.
 *
 * <p>Nothing is ever placed in the world - each block is a packet to that player, and
 * the real block is sent back when it is no longer shown, so a resource pack, a
 * restart or a reconnect all come back to what is really there. What is shown is
 * remembered per player, and only the difference is sent, so a wall that has not
 * moved costs nothing to keep up.
 */
public final class ClientBlocks {

    private final Map<UUID, Map<Location, BlockData>> shown = new ConcurrentHashMap<>();

    /**
     * Shows exactly these blocks to this player: whatever was shown before and is not
     * here again is sent back as it really is.
     */
    public void show(Player player, Map<Location, BlockData> blocks) {
        Map<Location, BlockData> before = shown.getOrDefault(player.getUniqueId(), Map.of());
        Map<io.papermc.paper.math.Position, BlockData> changes = new HashMap<>();
        for (Map.Entry<Location, BlockData> was : before.entrySet()) {
            if (!blocks.containsKey(was.getKey()) && sameWorld(player, was.getKey())) {
                changes.put(io.papermc.paper.math.Position.block(was.getKey()), was.getKey().getBlock().getBlockData());
            }
        }
        for (Map.Entry<Location, BlockData> block : blocks.entrySet()) {
            if (!block.getValue().equals(before.get(block.getKey()))) {
                changes.put(io.papermc.paper.math.Position.block(block.getKey()), block.getValue());
            }
        }
        // One packet per chunk section rather than one per block: a wall up to layer
        // 128 is thousands of blocks, and they are sent again whenever it moves.
        if (!changes.isEmpty()) {
            player.sendMultiBlockChange(changes);
        }
        if (blocks.isEmpty()) {
            shown.remove(player.getUniqueId());
        } else {
            shown.put(player.getUniqueId(), new HashMap<>(blocks));
        }
    }

    /** @return whether this player is being shown anything */
    public boolean isShowing(UUID playerId) {
        return shown.containsKey(playerId);
    }

    /** Sends every block back as it really is. */
    public void clear(Player player) {
        Map<Location, BlockData> before = shown.remove(player.getUniqueId());
        if (before == null) {
            return;
        }
        Map<io.papermc.paper.math.Position, BlockData> real = new HashMap<>();
        for (Location at : before.keySet()) {
            if (sameWorld(player, at)) {
                real.put(io.papermc.paper.math.Position.block(at), at.getBlock().getBlockData());
            }
        }
        if (!real.isEmpty()) {
            player.sendMultiBlockChange(real);
        }
    }

    /** A player gone: nothing to send them, only to forget. */
    public void forget(UUID playerId) {
        shown.remove(playerId);
    }

    /** Server stopping or module reloading: everybody sees what is really there again. */
    public void clearAll(Iterable<? extends Player> players) {
        for (Player player : players) {
            clear(player);
        }
        shown.clear();
    }

    private static void revert(Player player, Location at) {
        if (sameWorld(player, at)) {
            player.sendBlockChange(at, at.getBlock().getBlockData());
        }
    }

    private static boolean sameWorld(Player player, Location at) {
        return at.getWorld() != null && at.getWorld().equals(player.getWorld());
    }
}
