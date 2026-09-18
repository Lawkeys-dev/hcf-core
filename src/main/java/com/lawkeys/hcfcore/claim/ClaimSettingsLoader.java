package com.lawkeys.hcfcore.claim;

import com.lawkeys.hcfcore.util.Durations;
import com.lawkeys.hcfcore.team.TeamRole;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Turns {@code claims.yml} into an immutable {@link ClaimSettings}.
 *
 * <p>Same contract as {@code TeamSettingsLoader}: an invalid value is reported
 * and replaced by the built-in default rather than taking the server down
 * (ARCHITECTURE.md section 6).
 */
public final class ClaimSettingsLoader {

    private ClaimSettingsLoader() {
    }

    /**
     * @param section the root of {@code claims.yml}, or {@code null} when absent
     * @param warn    receives one line per invalid value
     */
    public static ClaimSettings load(ConfigurationSection section, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        ClaimSettings defaults = ClaimSettings.defaults();
        if (section == null) {
            warn.accept("claims.yml is missing or empty - using built-in defaults for the claim module.");
            return defaults;
        }

        return new ClaimSettings(
                section.getBoolean("enabled", defaults.enabled()),
                loadLimits(section.getConfigurationSection("limits"), defaults.limits(), warn),
                loadPlacement(section.getConfigurationSection("placement"), defaults.placement()),
                loadProtection(section.getConfigurationSection("protection"), defaults.protection()),
                loadHomes(section.getConfigurationSection("homes"), defaults.homes(), warn),
                loadWorlds(section),
                loadRequiredRoles(section.getConfigurationSection("required-roles"),
                        defaults.requiredRoles(), warn),
                loadWarzone(section.getConfigurationSection("warzone"), defaults.warzone(), warn),
                loadStuck(section.getConfigurationSection("stuck"), defaults.stuck(), warn));
    }

    private static ClaimSettings.StuckRules loadStuck(ConfigurationSection section,
                                                     ClaimSettings.StuckRules defaults, Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        return new ClaimSettings.StuckRules(
                Math.max(0L, Durations.capSeconds(section.getLong("warmup-seconds", defaults.warmupSeconds()), "warmup-seconds", warn)));
    }

    /**
     * Reads the warzone: display name, whether it is buildable, and one square per
     * world. A world with a missing or non-positive radius is skipped with a
     * warning rather than read as "no warzone" silently, since an operator who
     * wrote the entry meant to have one.
     */
    private static ClaimSettings.WarzoneRules loadWarzone(ConfigurationSection section,
                                                         ClaimSettings.WarzoneRules defaults,
                                                         Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        String displayName = section.getString("display-name", defaults.displayName());
        if (displayName == null || displayName.isBlank()) {
            displayName = defaults.displayName();
        }
        Map<String, ClaimSettings.WarzoneRules.Area> areas = new LinkedHashMap<>();
        ConfigurationSection worlds = section.getConfigurationSection("worlds");
        if (worlds != null) {
            for (String world : worlds.getKeys(false)) {
                ConfigurationSection entry = worlds.getConfigurationSection(world);
                if (entry == null) {
                    warn.accept("warzone.worlds." + world + " is not a section; that world has no warzone.");
                    continue;
                }
                int radius = entry.getInt("radius", 0);
                if (radius <= 0) {
                    warn.accept("warzone.worlds." + world + ".radius must be a positive number of blocks; "
                            + "that world has no warzone.");
                    continue;
                }
                areas.put(world, new ClaimSettings.WarzoneRules.Area(
                        entry.getInt("center-x", 0), entry.getInt("center-z", 0), radius));
            }
        }
        return new ClaimSettings.WarzoneRules(displayName,
                section.getBoolean("allow-building", defaults.allowBuilding()), areas);
    }

    private static ClaimSettings.LimitRules loadLimits(ConfigurationSection section,
                                                      ClaimSettings.LimitRules defaults,
                                                      Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        int base = Math.max(0, section.getInt("base", defaults.base()));
        int perMember = Math.max(0, section.getInt("per-member", defaults.perMember()));
        int maximum = Math.max(0, section.getInt("maximum", defaults.maximum()));
        int maxPerCommand = Math.max(0, section.getInt("max-per-command", defaults.maxPerCommand()));

        if (maximum > 0 && maximum < base) {
            warn.accept("limits.maximum (" + maximum + ") is below limits.base (" + base
                    + "), so no team could ever reach its base allowance; using base as the maximum.");
            maximum = base;
        }
        return new ClaimSettings.LimitRules(base, perMember, maximum, maxPerCommand);
    }

    private static ClaimSettings.PlacementRules loadPlacement(ConfigurationSection section,
                                                             ClaimSettings.PlacementRules defaults) {
        if (section == null) {
            return defaults;
        }
        return new ClaimSettings.PlacementRules(
                section.getBoolean("require-connected", defaults.requireConnected()),
                Math.max(0, section.getInt("minimum-distance-to-others",
                        defaults.minimumDistanceToOthers())),
                section.getBoolean("allow-disconnecting", defaults.allowDisconnecting()));
    }

    private static ClaimSettings.ProtectionRules loadProtection(ConfigurationSection section,
                                                               ClaimSettings.ProtectionRules defaults) {
        if (section == null) {
            return defaults;
        }
        return new ClaimSettings.ProtectionRules(
                section.getBoolean("allow-ally-build", defaults.allowAllyBuild()),
                section.getBoolean("allow-raid-building", defaults.allowRaidBuilding()),
                section.getBoolean("announce-territory", defaults.announceTerritory()),
                section.getBoolean("block-explosions", defaults.blockExplosions()));
    }

    private static ClaimSettings.HomeRules loadHomes(ConfigurationSection section,
                                                    ClaimSettings.HomeRules defaults, Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        return new ClaimSettings.HomeRules(
                section.getBoolean("enabled", defaults.enabled()),
                section.getBoolean("require-inside-territory", defaults.requireInsideTerritory()),
                Math.max(0L, Durations.capSeconds(section.getLong("warmup-seconds", defaults.warmupSeconds()), "warmup-seconds", warn)));
    }

    private static Set<String> loadWorlds(ConfigurationSection section) {
        Set<String> worlds = new LinkedHashSet<>();
        for (String world : section.getStringList("claimable-worlds")) {
            if (world != null && !world.isBlank()) {
                worlds.add(world.trim());
            }
        }
        return worlds;
    }

    private static Map<ClaimAction, TeamRole> loadRequiredRoles(ConfigurationSection section,
                                                               Map<ClaimAction, TeamRole> defaults,
                                                               Consumer<String> warn) {
        Map<ClaimAction, TeamRole> roles = new EnumMap<>(ClaimAction.class);
        roles.putAll(defaults);
        if (section == null) {
            return roles;
        }
        for (String key : section.getKeys(false)) {
            ClaimAction action = ClaimAction.fromConfigKey(key).orElse(null);
            if (action == null) {
                warn.accept("required-roles." + key + " is not a known claim action; ignored.");
                continue;
            }
            TeamRole fallback = defaults.getOrDefault(action, TeamRole.LEADER);
            String raw = section.getString(key);
            TeamRole role = TeamRole.fromId(raw).orElseGet(() -> {
                warn.accept("required-roles." + key + ": '" + raw
                        + "' is not a valid role (leader, co-leader, member); using "
                        + fallback.name().toLowerCase(Locale.ROOT) + ".");
                return fallback;
            });
            roles.put(action, role);
        }
        return roles;
    }
}
