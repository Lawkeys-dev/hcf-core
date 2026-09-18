package com.lawkeys.hcfcore.pvpclass;

/**
 * A class's energy: it fills over time while the class is active, and click effects
 * spend it. The Bard's is the classic one.
 *
 * @param max       the most it holds
 * @param perSecond how much it gains each second
 */
public record Energy(double max, double perSecond) {

    public Energy {
        if (!(max > 0)) {
            throw new IllegalArgumentException("max must be above 0, was " + max);
        }
        perSecond = Math.max(0.0, perSecond);
    }

    /** @return the energy after {@code elapsedMillis} from {@code from}, never above {@link #max} */
    public double after(double from, long elapsedMillis) {
        double gained = perSecond * Math.max(0L, elapsedMillis) / 1000.0;
        return Math.min(max, Math.max(0.0, from) + gained);
    }
}
