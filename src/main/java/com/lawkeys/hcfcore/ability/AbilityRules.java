package com.lawkeys.hcfcore.ability;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** The rules of the abilities that need no server. */
public final class AbilityRules {

    private AbilityRules() {
    }

    /**
     * Lucky Mode: what a hit is multiplied by, somewhere from {@code minPercent} to
     * {@code maxPercent} more - a negative percent is less.
     *
     * @param random from 0 (inclusive) to 1 (exclusive)
     */
    public static double luckyMultiplier(double minPercent, double maxPercent, double random) {
        double low = Math.min(minPercent, maxPercent);
        double high = Math.max(minPercent, maxPercent);
        return Math.max(0.0, 1.0 + (low + (high - low) * random) / 100.0);
    }

    /**
     * Anti-build: whether a block is one a player under it may not open. An entry
     * that is a block's own name ({@code CHEST}) is that block alone - not
     * {@code TRAPPED_CHEST}; one that is no block's name is the end of a family's
     * ({@code FENCE_GATE} covers {@code OAK_FENCE_GATE}, {@code TRAPDOOR} every
     * trapdoor).
     *
     * @param isBlockName whether a name, upper case, is a block's own
     */
    public static boolean blocked(List<String> entries, String material, Predicate<String> isBlockName) {
        String name = material.toUpperCase(Locale.ROOT);
        for (String entry : entries) {
            String wanted = entry.trim().toUpperCase(Locale.ROOT);
            if (name.equals(wanted) || (!isBlockName.test(wanted) && name.endsWith("_" + wanted))) {
                return true;
            }
        }
        return false;
    }

    /** Thunderbolt and the others: whether a roll of {@code random} (0 to 1) falls within {@code percent}. */
    public static boolean chance(double percent, double random) {
        return random * 100.0 < percent;
    }

    /**
     * Rocket, Hulk Smash: the upward speed that lifts a player about {@code 3 * height}
     * blocks - a height grows with the square of the speed.
     */
    public static double launchSpeed(double height) {
        return 0.8 * Math.sqrt(Math.max(0.0, height));
    }

    /**
     * Grappling Hook, Grabber: the velocity that carries a player from one point to
     * another, arcing as a thrown thing does; {@code pull} scales it, {@code lift} adds
     * to its upward part - a higher arc - and it is capped at {@code max} blocks a tick.
     *
     * @return {x, y, z}
     */
    public static double[] pullVelocity(double dx, double dy, double dz, double pull, double lift, double max) {
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1e-6) {
            return new double[] {0, 0, 0};
        }
        double x = (1.0 + 0.07 * distance) * dx / distance * pull;
        double y = ((1.0 + 0.03 * distance) * dy / distance + 0.04 * distance) * pull + lift;
        double z = (1.0 + 0.07 * distance) * dz / distance * pull;
        double speed = Math.sqrt(x * x + y * y + z * z);
        if (speed > max) {
            double scale = max / speed;
            return new double[] {x * scale, y * scale, z * scale};
        }
        return new double[] {x, y, z};
    }

    /** Sun: the damage, in health points, each enemy takes when {@code caught} enemies are in range. */
    public static double sunDamage(int caught, int maxPlayers, double heartsPerPlayer) {
        return Math.min(Math.max(0, caught), Math.max(0, maxPlayers)) * heartsPerPlayer * 2.0;
    }

    /** Shotgun: each projectile's turn from where the player looks, spread evenly across the fan. */
    public static double[] fan(int projectiles, double spreadDegrees) {
        int count = Math.max(1, projectiles);
        double[] out = new double[count];
        for (int i = 0; i < count; i++) {
            out[i] = count == 1 ? 0.0 : -spreadDegrees / 2.0 + spreadDegrees * i / (count - 1);
        }
        return out;
    }

    /**
     * Baguette: the Hunger amplifier that takes {@code foodLost} food points over
     * {@code seconds}. The game's Hunger adds 0.005 exhaustion a tick per level, and
     * 4 exhaustion is one food point, once saturation is gone.
     */
    public static int hungerAmplifier(long foodLost, long seconds) {
        if (foodLost <= 0) {
            return 0;
        }
        long ticks = Math.max(1L, seconds) * 20L;
        long levels = (long) Math.ceil(foodLost * 4.0 / (0.005 * ticks));
        return (int) Math.max(0L, Math.min(254L, levels - 1));
    }
}
