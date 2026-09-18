package com.lawkeys.hcfcore.pvpclass;

/**
 * The Archer's mark: a player hit by the class's arrow takes more damage, from
 * everybody, for a while.
 *
 * @param seconds          how long the mark lasts; a new hit starts it over
 * @param damageMultiplier what the damage the marked player takes is multiplied by -
 *                         {@code 1.25} is 25% more
 */
public record ArcherTag(int seconds, double damageMultiplier) {

    public ArcherTag {
        if (seconds <= 0) {
            throw new IllegalArgumentException("seconds must be above 0, was " + seconds);
        }
        if (!(damageMultiplier >= 1.0)) {
            throw new IllegalArgumentException("damage-multiplier must be 1.0 or more, was " + damageMultiplier);
        }
    }

    /** @return the extra damage as a whole percentage: 25 for 1.25 */
    public long percent() {
        return Math.round((damageMultiplier - 1.0) * 100.0);
    }
}
