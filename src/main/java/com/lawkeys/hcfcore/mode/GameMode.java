package com.lawkeys.hcfcore.mode;

import com.lawkeys.hcfcore.config.ConfigManager;

/**
 * Represents the active game mode this server instance is running.
 *
 * <p>Decision (28/08/2026, see FEATURES.md / ARCHITECTURE.md section 7-8):
 * the same core codebase runs on two independent server deployments (HCF and
 * Kitmap), distinguished purely by configuration - never by a separate build
 * or plugin variant.
 *
 * <p>Any module whose behavior differs between modes (e.g. the Lives system,
 * which Kitmap does not use) must check this enum at plugin startup and
 * skip its own initialization entirely when disabled for the active mode -
 * not just hide its commands.
 */
public enum GameMode {
    HCF,
    KITMAP;

    /**
     * Resolves the active game mode from {@code config.yml}'s {@code kitmap-mode}
     * boolean flag.
     */
    public static GameMode fromConfig(ConfigManager configManager) {
        boolean kitmapMode = configManager.getConfig().getBoolean("kitmap-mode", false);
        return kitmapMode ? KITMAP : HCF;
    }

    public boolean isKitmap() {
        return this == KITMAP;
    }

    public boolean isHcf() {
        return this == HCF;
    }
}
