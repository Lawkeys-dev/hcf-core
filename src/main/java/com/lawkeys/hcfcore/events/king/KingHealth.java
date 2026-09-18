package com.lawkeys.hcfcore.events.king;

/** The King's health as the chat announces it: a whole percentage of their maximum. */
public final class KingHealth {

    private KingHealth() {
    }

    /** @return {@code health} out of {@code maximum}, rounded, from 0 to 100; 0 when the maximum is unknown */
    public static int percent(double health, double maximum) {
        if (!(maximum > 0) || !(health > 0)) {
            return 0;
        }
        return (int) Math.max(0, Math.min(100, Math.round(health / maximum * 100.0)));
    }
}
