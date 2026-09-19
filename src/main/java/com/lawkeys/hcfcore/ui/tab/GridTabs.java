package com.lawkeys.hcfcore.ui.tab;

import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * Starts the HCF grid, which needs nothing installed (ARCHITECTURE.md section 11). If
 * this server's list packets are not the ones the plugin knows - a Minecraft version
 * that reshaped them - the classic list is shown, and the console says why.
 */
public final class GridTabs {

    private GridTabs() {
    }

    /** @return the grid, or {@code null} if it cannot run on this server */
    public static GridTab start(Plugin plugin) {
        try {
            return ServerGridTab.start();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "This server's tab list packets are not the ones HCFCore knows "
                    + "(Minecraft 26.2); the HCF tab list is replaced by the classic one.", e);
            return null;
        }
    }
}
