package com.lawkeys.hcfcore.ui.tab;

import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * Starts the grid if PacketEvents runs - a soft dependency, like Apollo
 * (ARCHITECTURE.md section 11). Without it, the HCF style falls back to the classic
 * list and says so once.
 */
public final class GridTabs {

    private GridTabs() {
    }

    /** @return the grid, or {@code null} without PacketEvents */
    public static GridTab start(Plugin plugin) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("packetevents")) {
            return null;
        }
        try {
            GridTab grid = PacketGridTab.start();
            plugin.getLogger().info("Hooked PacketEvents for the HCF tab list.");
            return grid;
        } catch (LinkageError | RuntimeException e) {
            // A PacketEvents whose API is not the one compiled against: an optional
            // look is not worth the server.
            plugin.getLogger().log(Level.WARNING, "PacketEvents is installed but could not be used; "
                    + "the tab list stays classic.", e);
            return null;
        }
    }
}
