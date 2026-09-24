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
                loadKoth(section.getConfigurationSection("koth"), defaults.koth()),
                Math.max(0, section.getInt("max-officers", defaults.maxOfficers())),
                loadCustom(section.getConfigurationSection("team-settings"), defaults.custom(), warn),
                loadShortcuts(section.getConfigurationSection("shortcuts"), defaults.shortcuts(), warn));
    }

    private static TeamSettings.CustomRules loadCustom(ConfigurationSection section,
                                                       TeamSettings.CustomRules defaults, Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        Set<String> locked = new LinkedHashSet<>();
        List<String> raw = section.contains("locked") ? section.getStringList("locked") : List.copyOf(defaults.locked());
        for (String key : raw) {
            if (key != null && !key.isBlank()) {
                locked.add(key.trim().toLowerCase(Locale.ROOT));
            }
        }
        Map<String, String> icons = new java.util.LinkedHashMap<>(defaults.icons());
        ConfigurationSection iconSection = section.getConfigurationSection("icons");
        if (iconSection != null) {
            for (String key : iconSection.getKeys(false)) {
                String material = iconSection.getString(key, "").trim();
                org.bukkit.Material found = org.bukkit.Material.matchMaterial(material);
                if (found == null || !found.isItem()) {
                    warn.accept("team-settings.icons." + key + ": '" + material + "' is not an item; the default is used.");
                } else {
                    icons.put(key.trim().toLowerCase(Locale.ROOT), found.name());
                }
            }
        }
        Set<JoinMode> modes = java.util.EnumSet.noneOf(JoinMode.class);
        if (section.contains("join-modes")) {
            for (String mode : section.getStringList("join-modes")) {
                JoinMode.fromId(mode).ifPresentOrElse(modes::add, () -> warn.accept(
                        "team-settings.join-modes: '" + mode + "' is not closed, invite or open; ignored."));
            }
        } else {
            modes.addAll(defaults.joinModes());
        }
        String rawDefault = section.getString("default-join-mode", defaults.defaultJoinMode().configKey());
        JoinMode defaultMode = JoinMode.fromId(rawDefault).orElseGet(() -> {
            warn.accept("team-settings.default-join-mode: '" + rawDefault + "' is not closed, invite or open; invite is used.");
            return JoinMode.INVITE;
        });
        Pattern discord = defaults.discordPattern();
        String rawPattern = section.getString("discord.pattern");
        if (rawPattern != null && !rawPattern.isBlank()) {
            try {
                discord = Pattern.compile(rawPattern);
            } catch (PatternSyntaxException e) {
                warn.accept("team-settings.discord.pattern is not a valid regex (" + e.getDescription()
                        + "); the default is used.");
            }
        }
        return new TeamSettings.CustomRules(section.getBoolean("enabled", defaults.enabled()), modes, defaultMode,
                locked, icons, section.getInt("description.max-length", defaults.descriptionLength()), discord);
    }

    /**
     * Words and commands are kept lower-case and without a slash; one that is not a
     * single word is reported and left out.
     */
    private static TeamSettings.Shortcuts loadShortcuts(ConfigurationSection section,
                                                        TeamSettings.Shortcuts defaults, Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        return new TeamSettings.Shortcuts(section.getBoolean("enabled", defaults.enabled()),
                words(section, "subcommands", defaults.subcommands(), warn),
                words(section, "commands", defaults.commands(), warn));
    }

    private static Map<String, String> words(ConfigurationSection parent, String path, Map<String, String> defaults,
                                             Consumer<String> warn) {
        ConfigurationSection section = parent.getConfigurationSection(path);
        if (section == null) {
            return parent.contains(path) ? Map.of() : defaults;
        }
        Map<String, String> words = new java.util.LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            String word = key.trim().toLowerCase(Locale.ROOT);
            if (word.startsWith("/")) {
                word = word.substring(1);
            }
            String target = section.getString(key, "").trim();
            if (target.startsWith("/")) {
                target = target.substring(1);
            }
            if (word.isEmpty() || word.contains(" ") || target.isEmpty()) {
                warn.accept("shortcuts." + path + "." + key + " needs one word and a subcommand; ignored.");
                continue;
            }
            words.put(word, target);
        }
        return words;
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
            warn.accept(path + ": '" + raw + "' is not a valid role (leader, co-leader, officer, member); using "
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
                section.getLong("per-king-win", defaults.perKingWin()),
                section.getLong("per-dtc-win", defaults.perDtcWin()),
                section.getLong("per-last-break-win", defaults.perLastBreakWin()),
                section.getLong("per-slide-win", defaults.perSlideWin()),
                section.getLong("per-totem-win", defaults.perTotemWin()),
                section.getLong("per-citadel-capture", defaults.perCitadelCapture()),
                section.getLong("per-mini-totem-win", defaults.perMiniTotemWin()),
                // A file written before the share existed, with a per-raidable of its
                // own, keeps meaning what it said: the share only applies when set.
                section.contains("raidable-loss-percent")
                        ? section.getDouble("raidable-loss-percent")
                        : section.contains("per-raidable") ? 0.0 : defaults.raidableLossPercent());
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
