package com.lawkeys.hcfcore.team;

import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Turns {@code teams.yml} into an immutable {@link TeamSettings}.
 *
 * <p>This is the only class in the team module that knows the config file's
 * shape, which keeps the rule engine free of the server API and testable.
 *
 * <p>Invalid values never take the server down: each one is reported through the
 * supplied warning sink and the built-in default is used instead
 * (ARCHITECTURE.md section 6: an invalid value is logged clearly and falls back to
 * its default, never a crash).
 */
public final class TeamSettingsLoader {

    private TeamSettingsLoader() {
    }

    /**
     * @param section the root of {@code teams.yml}, or {@code null} if the file is
     *                missing - in which case the built-in defaults are returned
     * @param warn    receives one line per invalid value
     */
    public static TeamSettings load(ConfigurationSection section, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        TeamSettings defaults = TeamSettings.defaults();
        if (section == null) {
            warn.accept("teams.yml is missing or empty - using built-in defaults for the team module.");
            return defaults;
        }

        return new TeamSettings(
                loadNames(section.getConfigurationSection("names"), defaults.names(), warn),
                section.getInt("max-members", defaults.maxMembers()),
                section.getInt("max-co-leaders", defaults.maxCoLeaders()),
                Durations.capSeconds(section.getLong("invite-expiry-seconds", defaults.inviteExpirySeconds()), "invite-expiry-seconds", warn),
                section.getBoolean("disband-on-last-member-leave", defaults.disbandOnLastMemberLeave()),
                role(section.getString("role-after-leadership-transfer"),
                        defaults.roleAfterLeadershipTransfer(), "role-after-leadership-transfer", warn),
                loadRequiredRoles(section.getConfigurationSection("required-roles"),
                        defaults.requiredRoles(), warn),
                loadAlliances(section.getConfigurationSection("alliances"), defaults.alliances()),
                loadFocus(section.getConfigurationSection("focus"), defaults.focus()),
                loadRally(section.getConfigurationSection("rally"), defaults.rally(), warn),
                loadBank(section.getConfigurationSection("bank"), defaults.bank()),
                loadPoints(section.getConfigurationSection("points"), defaults.points()),
                loadKoth(section.getConfigurationSection("koth"), defaults.koth()));
    }

    private static TeamSettings.NameRules loadNames(ConfigurationSection section,
                                                    TeamSettings.NameRules defaults,
                                                    Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        int min = section.getInt("min-length", defaults.minLength());
        int max = section.getInt("max-length", defaults.maxLength());
        if (min < 1) {
            warn.accept("names.min-length must be at least 1; using " + defaults.minLength() + ".");
            min = defaults.minLength();
        }
        if (max < min) {
            warn.accept("names.max-length (" + max + ") is below names.min-length (" + min
                    + "); using " + defaults.maxLength() + ".");
            max = Math.max(min, defaults.maxLength());
        }

        Pattern pattern = defaults.pattern();
        String rawPattern = section.getString("pattern");
        if (rawPattern != null && !rawPattern.isBlank()) {
            try {
                pattern = Pattern.compile(rawPattern);
            } catch (PatternSyntaxException e) {
                warn.accept("names.pattern is not a valid regex (" + e.getDescription()
                        + "); using the default " + defaults.pattern().pattern() + ".");
            }
        }

        Set<String> blacklist = new LinkedHashSet<>();
        for (String entry : section.getStringList("blacklist")) {
            if (entry != null && !entry.isBlank()) {
                blacklist.add(entry.trim().toLowerCase(Locale.ROOT));
            }
        }
        return new TeamSettings.NameRules(min, max, pattern, blacklist);
    }

    private static Map<TeamAction, TeamRole> loadRequiredRoles(ConfigurationSection section,
                                                              Map<TeamAction, TeamRole> defaults,
                                                              Consumer<String> warn) {
        Map<TeamAction, TeamRole> roles = new EnumMap<>(TeamAction.class);
        roles.putAll(defaults);
        if (section == null) {
            return roles;
        }
        for (String key : section.getKeys(false)) {
            TeamAction action = TeamAction.fromConfigKey(key).orElse(null);
            if (action == null) {
                warn.accept("required-roles." + key + " is not a known team action; ignored.");
                continue;
            }
            TeamRole fallback = defaults.getOrDefault(action, TeamRole.LEADER);
            roles.put(action, role(section.getString(key), fallback, "required-roles." + key, warn));
        }
        return roles;
    }

    private static TeamRole role(String raw, TeamRole fallback, String path, Consumer<String> warn) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return TeamRole.fromId(raw).orElseGet(() -> {
            warn.accept(path + ": '" + raw + "' is not a valid role (leader, co-leader, member); using "
                    + fallback.name().toLowerCase(Locale.ROOT) + ".");
            return fallback;
        });
    }

    private static TeamSettings.AllianceRules loadAlliances(ConfigurationSection section,
                                                           TeamSettings.AllianceRules defaults) {
        if (section == null) {
            return defaults;
        }
        return new TeamSettings.AllianceRules(
                section.getBoolean("enabled", defaults.enabled()),
                Math.max(0, section.getInt("max-allies", defaults.maxAllies())));
    }

    private static TeamSettings.FocusRules loadFocus(ConfigurationSection section,
                                                    TeamSettings.FocusRules defaults) {
        if (section == null) {
            return defaults;
        }
        return new TeamSettings.FocusRules(
                section.getBoolean("enabled", defaults.enabled()),
                Math.max(0, section.getInt("max-targets", defaults.maxTargets())));
    }

    private static TeamSettings.RallyRules loadRally(ConfigurationSection section,
                                                    TeamSettings.RallyRules defaults, Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        return new TeamSettings.RallyRules(
                section.getBoolean("enabled", defaults.enabled()),
                Math.max(0L, Durations.capSeconds(section.getLong("duration-seconds", defaults.durationSeconds()), "duration-seconds", warn)));
    }

    private static TeamSettings.BankRules loadBank(ConfigurationSection section,
                                                  TeamSettings.BankRules defaults) {
        if (section == null) {
            return defaults;
        }
        return new TeamSettings.BankRules(section.getBoolean("enabled", defaults.enabled()));
    }

    private static TeamSettings.PointsRules loadPoints(ConfigurationSection section,
                                                      TeamSettings.PointsRules defaults) {
        if (section == null) {
            return defaults;
        }
        return new TeamSettings.PointsRules(
                section.getLong("starting", defaults.starting()),
                section.getLong("minimum", defaults.minimum()),
                section.getLong("per-kill", defaults.perKill()),
                section.getLong("per-death", defaults.perDeath()),
                section.getLong("per-raidable", defaults.perRaidable()),
                section.getLong("per-conquest-win", defaults.perConquestWin()),
                section.getLong("per-king-win", defaults.perKingWin()));
    }

    private static TeamSettings.KothRules loadKoth(ConfigurationSection section,
                                                  TeamSettings.KothRules defaults) {
        if (section == null) {
            return defaults;
        }
        return new TeamSettings.KothRules(
                Math.max(0, section.getInt("max-counted-captures", defaults.maxCountedCaptures())),
                section.getLong("points-per-capture", defaults.pointsPerCapture()));
    }

    /** Exposed for the loader's own unit test; mirrors {@code getStringList} semantics. */
    static Set<String> normalizeBlacklist(List<String> entries) {
        Set<String> blacklist = new LinkedHashSet<>();
        for (String entry : entries) {
            if (entry != null && !entry.isBlank()) {
                blacklist.add(entry.trim().toLowerCase(Locale.ROOT));
            }
        }
        return blacklist;
    }
}
