package com.lawkeys.hcfcore.dtr;

import java.util.Objects;

/**
 * Immutable snapshot of {@code dtr.yml}.
 *
 * <p>Held through a {@code Supplier} so {@code /hcf reload} applies at once, like
 * every other module (ARCHITECTURE.md section 2).
 *
 * <p>None of these numbers are hardcoded anywhere else: the defaults below are
 * the classic HCF starting point, meant to be tuned per server.
 */
public record DtrSettings(
        boolean enabled,
        MaximumRules maximum,
        double lossPerDeath,
        double minimum,
        RegenerationRules regeneration,
        AnnouncementRules announcements) {

    public DtrSettings {
        Objects.requireNonNull(maximum, "maximum");
        Objects.requireNonNull(regeneration, "regeneration");
        Objects.requireNonNull(announcements, "announcements");
    }

    /**
     * How high a team's DTR can climb.
     *
     * @param base      DTR every team has regardless of size
     * @param perMember DTR added per member
     * @param cap       hard ceiling whatever the member count; {@code 0} means uncapped
     */
    public record MaximumRules(double base, double perMember, double cap) {
    }

    /**
     * How DTR comes back.
     *
     * @param freezeSeconds  how long regeneration is frozen after a death
     * @param amount         DTR granted per interval
     * @param intervalSeconds length of one regeneration step
     */
    public record RegenerationRules(long freezeSeconds, double amount, long intervalSeconds) {
    }

    /**
     * @param onDeath          tell the team how much DTR a death cost
     * @param onRaidableChange announce when a team becomes raidable, and when it stops being
     * @param pollSeconds      how often to check for teams that regenerated back above zero;
     *                         this only drives announcements, never the DTR value itself
     */
    public record AnnouncementRules(boolean onDeath, boolean onRaidableChange, long pollSeconds) {
    }

    /**
     * The DTR ceiling for a team of {@code memberCount} members.
     *
     * <p>Note this is evaluated live: a team that loses members sees its ceiling
     * drop, and a current value above the new ceiling is clamped on read rather
     * than being silently kept.
     */
    public double maximumFor(int memberCount) {
        double allowance = maximum.base() + maximum.perMember() * memberCount;
        if (maximum.cap() > 0) {
            allowance = Math.min(allowance, maximum.cap());
        }
        return allowance;
    }

    /**
     * Built-in fallback, mirroring {@code resources/dtr.yml}. The values are the
     * long-standing HCF convention: 1.1 DTR per player capped at 6.6, one death
     * costs 1.0, and regeneration resumes 45 minutes after the last death at
     * 0.1 every 3 minutes - so a full point takes half an hour.
     */
    public static DtrSettings defaults() {
        return new DtrSettings(
                true,
                new MaximumRules(0.0, 1.1, 6.6),
                1.0,
                -5.0,
                new RegenerationRules(2700L, 0.1, 180L),
                new AnnouncementRules(true, true, 60L));
    }
}
