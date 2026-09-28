package com.lawkeys.hcfcore.phase;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of {@code phases.yml}.
 *
 * @param sotwDurationSeconds    how long a SOTW lasts when staff give no duration,
 *                               and how long a scheduled one lasts
 * @param sotwScheduledAt        epoch millis at which a SOTW starts by itself, or 0
 * @param eotwScheduledAt        epoch millis at which EOTW starts by itself, or 0
 * @param eotwStartWindowSeconds how late a scheduled EOTW may still start - after
 *                               a server that was down at the time comes back - before
 *                               it is treated as a date left over from another map
 * @param sotwAnnounceAtSeconds  SOTW remaining-time marks to broadcast
 * @param timeZone               the zone the scheduled dates are written in
 * @param purge                  when the Purge starts by itself, and for how long
 * @param land                   what EOTW and the Purge open on somebody else's land
 */
public record PhaseSettings(long sotwDurationSeconds,
                            long sotwScheduledAt,
                            long eotwScheduledAt,
                            long eotwStartWindowSeconds,
                            List<Long> sotwAnnounceAtSeconds,
                            ZoneId timeZone,
                            PurgeRules purge,
                            LandRules land) {

    /**
     * What a phase opens, whatever a team's DTR (the owner's rules of 28/09/2026).
     *
     * @param purgeUseEnemyBlocks during the Purge, anybody may use the blocks of
     *                            other teams' claims - doors, chests, buttons - but
     *                            never build or break there
     * @param eotwOpenServerLand  during EOTW, server land is open to building and
     *                            breaking too, as every team's is
     * @param eotwKeepSafeZones   ...except the safe zones - spawn
     * @param eotwProtectedTeams  ...and the server teams named here, lower case
     */
    public record LandRules(boolean purgeUseEnemyBlocks, boolean eotwOpenServerLand, boolean eotwKeepSafeZones,
                            java.util.Set<String> eotwProtectedTeams) {

        public LandRules {
            eotwProtectedTeams = java.util.Set.copyOf(Objects.requireNonNull(eotwProtectedTeams, "eotwProtectedTeams"));
        }

        public static LandRules defaults() {
            return new LandRules(true, true, true, java.util.Set.of());
        }
    }

    public PhaseSettings(long sotwDurationSeconds, long sotwScheduledAt, long eotwScheduledAt,
                         long eotwStartWindowSeconds, List<Long> sotwAnnounceAtSeconds, ZoneId timeZone,
                         PurgeRules purge) {
        this(sotwDurationSeconds, sotwScheduledAt, eotwScheduledAt, eotwStartWindowSeconds, sotwAnnounceAtSeconds,
                timeZone, purge, LandRules.defaults());
    }

    /**
     * @param durationSeconds how long a Purge lasts: a scheduled one, and
     *                        {@code /purge start} without a duration
     * @param times           times of day, in {@link #timeZone}, at which it starts by
     *                        itself; empty for none
     */
    public record PurgeRules(long durationSeconds, List<LocalTime> times) {

        /** No schedule, and half an hour for {@code /purge start} with no duration. */
        public static final PurgeRules NONE = new PurgeRules(1_800L, List.of());

        public PurgeRules {
            times = List.copyOf(Objects.requireNonNull(times, "times"));
            if (durationSeconds <= 0) {
                throw new IllegalArgumentException("durationSeconds must be positive: " + durationSeconds);
            }
        }
    }

    public PhaseSettings {
        Objects.requireNonNull(timeZone, "timeZone");
        Objects.requireNonNull(purge, "purge");
        Objects.requireNonNull(land, "land");
        sotwAnnounceAtSeconds = List.copyOf(Objects.requireNonNull(sotwAnnounceAtSeconds, "sotwAnnounceAtSeconds"));
        if (sotwDurationSeconds <= 0) {
            throw new IllegalArgumentException("sotwDurationSeconds must be positive: " + sotwDurationSeconds);
        }
        if (eotwStartWindowSeconds <= 0) {
            throw new IllegalArgumentException("eotwStartWindowSeconds must be positive: " + eotwStartWindowSeconds);
        }
    }

    public static PhaseSettings defaults() {
        return new PhaseSettings(7_200L, 0L, 0L, 3_600L, List.of(3_600L, 1_800L, 600L, 300L, 60L, 10L),
                ZoneId.systemDefault(), PurgeRules.NONE, LandRules.defaults());
    }
}
