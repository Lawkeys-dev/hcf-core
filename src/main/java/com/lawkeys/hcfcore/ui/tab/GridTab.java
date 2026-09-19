package com.lawkeys.hcfcore.ui.tab;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shows the HCF grid to a player. The one implementation, {@code ServerGridTab}, builds
 * the server's list packets by reflection - the only code of the plugin past Paper's
 * API - so nothing else ever touches the server's internals.
 */
public interface GridTab {

    /**
     * @param latency   the connection bars of every cell, in milliseconds; below 0 for none
     * @param texture   a skin for every cell's head, empty for the game's default heads
     * @param signature that skin's signature, as Mojang gave it
     */
    record Look(int latency, String texture, String signature) {
    }

    /** A signed skin texture, as Mojang gives it. */
    record Skin(String texture, String signature) {
    }

    /** @param skin its head; {@code null} for the look's default */
    record Cell(Component text, Skin skin) {
    }

    /** Takes the players off the viewer's list and puts the {@link TabGrid#SIZE} cells in. */
    void show(Player viewer, List<Cell> cells, Look look);

    /**
     * Rewrites the cells that changed, by index: {@code texts} only their text,
     * {@code heads} their head too - which the game reads only when an entry is
     * added, so those are taken out and put back.
     */
    void update(Player viewer, Map<Integer, Cell> texts, Map<Integer, Cell> heads, Look look);

    /** Takes the cells away and gives the viewer the players back. */
    void hide(Player viewer);

    /** A viewer left: nothing to send, only to forget. */
    void forget(UUID viewer);

    /** Hides the grid from everybody and stops listening. */
    void stop();
}
