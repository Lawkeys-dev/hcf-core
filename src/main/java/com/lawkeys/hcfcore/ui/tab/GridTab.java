package com.lawkeys.hcfcore.ui.tab;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shows the HCF grid to a player. The one implementation talks to PacketEvents and
 * is loaded only once that plugin is known to run, as {@code ApolloBridge} is for
 * Apollo: the rest of the plugin never names a PacketEvents type.
 */
public interface GridTab {

    /**
     * @param latency   the connection bars of every cell, in milliseconds; below 0 for none
     * @param texture   a skin for every cell's head, empty for the game's default heads
     * @param signature that skin's signature, as Mojang gave it
     */
    record Look(int latency, String texture, String signature) {
    }

    /** Takes the players off the viewer's list and puts the {@link TabGrid#SIZE} cells in. */
    void show(Player viewer, List<Component> cells, Look look);

    /** Rewrites the cells that changed, by index. */
    void update(Player viewer, Map<Integer, Component> changed, Look look);

    /** Takes the cells away and gives the viewer the players back. */
    void hide(Player viewer);

    /** A viewer left: nothing to send, only to forget. */
    void forget(UUID viewer);

    /** Hides the grid from everybody and stops listening. */
    void stop();
}
