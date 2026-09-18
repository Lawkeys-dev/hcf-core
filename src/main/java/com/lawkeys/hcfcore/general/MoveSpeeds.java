package com.lawkeys.hcfcore.general;

/**
 * {@code /flyspeed} and {@code /walkspeed}: a speed from 0 to 10 as players know it
 * from essentials plugins - {@code 1} the game's own, {@code 10} the fastest there is -
 * turned into the game's value, where the fastest is 1.0.
 */
public final class MoveSpeeds {

    /** The game's own walking speed (Bukkit {@code Player#getWalkSpeed} default). */
    public static final float DEFAULT_WALK = 0.2f;
    /** The game's own flying speed (Bukkit {@code Player#getFlySpeed} default). */
    public static final float DEFAULT_FLY = 0.1f;
    public static final float MAX_SPEED = 1.0f;
    public static final float MAX_USER_SPEED = 10f;

    private MoveSpeeds() {
    }

    /**
     * @param userSpeed 0 to 10: below 1 slows down from the game's own, 1 to 10 rises
     *                  evenly from it to the fastest
     * @param defaultSpeed the game's own speed for this movement
     */
    public static float toGame(float userSpeed, float defaultSpeed) {
        float speed = Math.max(0f, Math.min(MAX_USER_SPEED, userSpeed));
        if (speed < 1f) {
            return defaultSpeed * speed;
        }
        return defaultSpeed + (speed - 1f) / (MAX_USER_SPEED - 1f) * (MAX_SPEED - defaultSpeed);
    }
}
