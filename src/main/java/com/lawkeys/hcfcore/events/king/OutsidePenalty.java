package com.lawkeys.hcfcore.events.king;

/**
 * What leaving the warzone costs the King, as a function of how long they have
 * been out (FEATURES.md section 6: damage, and a Wither that grows with the time spent
 * outside - a deterrent that builds up, not a kick or a teleport back).
 *
 * <p>The count restarts each time the King comes back in: it measures one excursion,
 * not a total, so a King pushed over the border by a hit and stepping straight
 * back is not still paying for it ten minutes later.
 *
 * @param graceSeconds      seconds outside before anything happens. A King knocked
 *                          across the border by a single hit is not punished for it
 * @param damagePerSecond   plain damage per second outside, once the grace is over
 * @param witherStartLevel  Wither level once the grace is over, as players see it
 *                          (1 is Wither I)
 * @param witherStepSeconds every this many further seconds outside, one level more
 * @param witherMaxLevel    the level it stops growing at
 */
public record OutsidePenalty(long graceSeconds,
                             double damagePerSecond,
                             int witherStartLevel,
                             long witherStepSeconds,
                             int witherMaxLevel) {

    public OutsidePenalty {
        if (graceSeconds < 0) {
            throw new IllegalArgumentException("graceSeconds cannot be negative: " + graceSeconds);
        }
        if (damagePerSecond < 0 || !Double.isFinite(damagePerSecond)) {
            throw new IllegalArgumentException("damagePerSecond must be a non-negative number: " + damagePerSecond);
        }
        if (witherStartLevel < 1) {
            throw new IllegalArgumentException("witherStartLevel must be at least 1: " + witherStartLevel);
        }
        if (witherStepSeconds < 1) {
            throw new IllegalArgumentException("witherStepSeconds must be at least 1: " + witherStepSeconds);
        }
        if (witherMaxLevel < witherStartLevel) {
            throw new IllegalArgumentException("witherMaxLevel (" + witherMaxLevel
                    + ") is below witherStartLevel (" + witherStartLevel + ")");
        }
    }

    public static OutsidePenalty defaults() {
        return new OutsidePenalty(3L, 1.0, 1, 10L, 5);
    }

    /** @return whether the King is being punished after this many seconds outside */
    public boolean appliesAfter(long secondsOutside) {
        return secondsOutside >= graceSeconds;
    }

    /** @return the Wither level after this many seconds outside, or 0 within the grace */
    public int witherLevelAfter(long secondsOutside) {
        if (!appliesAfter(secondsOutside)) {
            return 0;
        }
        long level = witherStartLevel + (secondsOutside - graceSeconds) / witherStepSeconds;
        return (int) Math.min(witherMaxLevel, level);
    }
}
