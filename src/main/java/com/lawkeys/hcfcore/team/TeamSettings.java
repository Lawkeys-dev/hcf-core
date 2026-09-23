package com.lawkeys.hcfcore.team;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Immutable snapshot of everything in {@code teams.yml}.
 *
 * <p>Every tunable of the team module lives here rather than as a constant in
 * the manager, per ARCHITECTURE.md section 2 ("no gameplay value may ever need a
 * recompile"). {@link TeamManager} holds a
 * {@code Supplier<TeamSettings>} rather than an instance, so {@code /hcf reload}
 * swaps the snapshot and every subsequent decision uses the new values without a
 * restart.
 *
 * <p>Throughout, a limit of {@code 0} means "no limit".
 */
public record TeamSettings(
        NameRules names,
        int maxMembers,
        int maxCoLeaders,
        long inviteExpirySeconds,
        boolean disbandOnLastMemberLeave,
        TeamRole roleAfterLeadershipTransfer,
        Map<TeamAction, TeamRole> requiredRoles,
        AllianceRules alliances,
        FocusRules focus,
        RallyRules rally,
        BankRules bank,
        PointsRules points,
        KothRules koth) {

    public TeamSettings {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(roleAfterLeadershipTransfer, "roleAfterLeadershipTransfer");
        requiredRoles = Map.copyOf(Objects.requireNonNull(requiredRoles, "requiredRoles"));
        Objects.requireNonNull(alliances, "alliances");
        Objects.requireNonNull(focus, "focus");
        Objects.requireNonNull(rally, "rally");
        Objects.requireNonNull(bank, "bank");
        Objects.requireNonNull(points, "points");
        Objects.requireNonNull(koth, "koth");
    }

    /**
     * @param pattern regex a team name must match in full; kept as a compiled
     *                {@link Pattern} so an invalid regex is rejected once at load
     *                time instead of on every command
     */
    public record NameRules(int minLength, int maxLength, Pattern pattern, Set<String> blacklist) {

        public NameRules {
            Objects.requireNonNull(pattern, "pattern");
            blacklist = Set.copyOf(Objects.requireNonNull(blacklist, "blacklist"));
        }
    }

    public record AllianceRules(boolean enabled, int maxAllies) {
    }

    public record FocusRules(boolean enabled, int maxTargets) {
    }

    /** @param durationSeconds how long a rally point stays active; {@code 0} means it never expires */
    public record RallyRules(boolean enabled, long durationSeconds) {
    }

    public record BankRules(boolean enabled) {
    }

    /**
     * The Team Points scale (FEATURES.md section 1). What earns or costs points is all
     * configuration, shipped at {@code 0}: the scale is the project owner's to set.
     *
     * @param minimum        floor the points score can never go below
     * @param perKill        to the killer's team, for a player of any other team or none
     * @param perDeath       to the victim's team, for any death - negative to take points
     * @param perRaidable    to a team whose DTR makes it raidable - negative to take points;
     *                       used when {@code raidableLossPercent} is 0
     * @param perConquestWin to the team that wins a Conquest
     * @param perKingWin     to the team of the player who wins Kill the King
     * @param perDtcWin      to the team that wins a DTC
     * @param perLastBreakWin to the team that wins a Last Break
     * @param perSlideWin    to the team that wins a Slide
     * @param perTotemWin    to the team that wins a Totem
     * @param perCitadelCapture to the team that captures a Citadel - worth more than a
     *                       KOTH's {@code koth.points-per-capture}, being three times as long
     * @param perMiniTotemWin to the team that wins a Mini Totem: a Totem of
     *                       {@link #MINI_TOTEM_HEIGHT} blocks or fewer
     * @param raidableLossPercent the share of its points a team loses when its DTR makes
     *                       it raidable, 0 to 100 - rather than {@code perRaidable}'s fixed
     *                       number, which it replaces when above 0 (the project owner's
     *                       choice of 23/09/2026: half, as shipped)
     */
    public record PointsRules(long starting, long minimum, long perKill, long perDeath, long perRaidable,
                              long perConquestWin, long perKingWin, long perDtcWin, long perLastBreakWin,
                              long perSlideWin, long perTotemWin, long perCitadelCapture, long perMiniTotemWin,
                              double raidableLossPercent) {

        public PointsRules {
            raidableLossPercent = Double.isFinite(raidableLossPercent)
                    ? Math.max(0.0, Math.min(100.0, raidableLossPercent)) : 0.0;
        }

        /** @return what a team holding {@code points} loses at becoming raidable, as a delta */
        public long raidableDelta(long points) {
            if (raidableLossPercent > 0) {
                return -(long) Math.floor(Math.max(0L, points) * raidableLossPercent / 100.0);
            }
            return perRaidable;
        }

        /** The tallest column that is a Mini Totem rather than a Totem. */
        public static final int MINI_TOTEM_HEIGHT = 3;

        /** A scale that awards nothing. */
        public PointsRules(long starting, long minimum) {
            this(starting, minimum, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0.0);
        }

        /** The scale before a Citadel and a Mini Totem had values of their own: nothing for them. */
        public PointsRules(long starting, long minimum, long perKill, long perDeath, long perRaidable,
                           long perConquestWin, long perKingWin, long perDtcWin, long perLastBreakWin,
                           long perSlideWin, long perTotemWin) {
            this(starting, minimum, perKill, perDeath, perRaidable, perConquestWin, perKingWin, perDtcWin,
                    perLastBreakWin, perSlideWin, perTotemWin, 0L, 0L, 0.0);
        }
    }

    /**
     * KOTH capture accounting (FEATURES.md section 1, "Team KOTH caps").
     *
     * <p>The scoring scale itself is still an open question in FEATURES.md, so
     * only the mechanism is implemented here - the numbers stay in config.
     *
     * @param maxCountedCaptures cap on captures counted toward the ranking; {@code 0} means uncapped
     */
    public record KothRules(int maxCountedCaptures, long pointsPerCapture) {
    }

    /** @return the minimum role required for {@code action}, defaulting to LEADER if unmapped. */
    public TeamRole requiredRole(TeamAction action) {
        return requiredRoles.getOrDefault(action, TeamRole.LEADER);
    }

    /**
     * Built-in fallback used when {@code teams.yml} is missing or unreadable, and
     * as the baseline the loader overlays configured values onto. Values mirror
     * the defaults shipped in {@code resources/teams.yml}.
     */
    public static TeamSettings defaults() {
        Map<TeamAction, TeamRole> roles = new EnumMap<>(TeamAction.class);
        roles.put(TeamAction.DISBAND, TeamRole.LEADER);
        roles.put(TeamAction.RENAME, TeamRole.LEADER);
        roles.put(TeamAction.INVITE, TeamRole.CO_LEADER);
        roles.put(TeamAction.REVOKE_INVITE, TeamRole.CO_LEADER);
        roles.put(TeamAction.KICK, TeamRole.CO_LEADER);
        roles.put(TeamAction.PROMOTE, TeamRole.LEADER);
        roles.put(TeamAction.DEMOTE, TeamRole.LEADER);
        roles.put(TeamAction.TRANSFER_LEADERSHIP, TeamRole.LEADER);
        roles.put(TeamAction.ALLY, TeamRole.LEADER);
        roles.put(TeamAction.UNALLY, TeamRole.LEADER);
        roles.put(TeamAction.FOCUS, TeamRole.CO_LEADER);
        roles.put(TeamAction.RALLY, TeamRole.CO_LEADER);
        roles.put(TeamAction.BANK_DEPOSIT, TeamRole.MEMBER);
        roles.put(TeamAction.BANK_WITHDRAW, TeamRole.LEADER);

        return new TeamSettings(
                new NameRules(3, 16, Pattern.compile("^[A-Za-z0-9_]+$"), Set.of()),
                20,
                2,
                300L,
                true,
                TeamRole.CO_LEADER,
                roles,
                new AllianceRules(true, 1),
                new FocusRules(true, 0),
                new RallyRules(true, 300L),
                new BankRules(true),
                // The owner's scale of 23/09/2026: a kill +1, a death -2, and an event
                // worth far more than the kills fought for it along the way - a KOTH
                // 100, a Citadel 300 - so the event, not the farm, decides the ranking.
                new PointsRules(0L, 0L, 1L, -2L, 0L, 250L, 150L, 200L, 150L, 200L, 150L, 300L, 80L, 50.0),
                new KothRules(0, 100L));
    }
}
