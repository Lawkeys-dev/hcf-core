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
        for (Map.Entry<Location, BlockData> was : before.entrySet()) {
            if (!blocks.containsKey(was.getKey())) {
                revert(player, was.getKey());
            }
        }
        for (Map.Entry<Location, BlockData> block : blocks.entrySet()) {
            if (!block.getValue().equals(before.get(block.getKey()))) {
                player.sendBlockChange(block.getKey(), block.getValue());
            }
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
        if (before != null) {
            before.keySet().forEach(at -> revert(player, at));
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
        if (at.getWorld() != null && at.getWorld().equals(player.getWorld())) {
            player.sendBlockChange(at, at.getBlock().getBlockData());
        }
    }
}
