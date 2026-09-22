package com.lawkeys.hcfcore.claim;

import com.lawkeys.hcfcore.util.Durations;
import com.lawkeys.hcfcore.team.TeamRole;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
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
                loadSizes(section.getConfigurationSection("sizes"), defaults.sizes(), warn),
                loadPrice(section.getConfigurationSection("price"), defaults.price(), warn),
                loadPlacement(section.getConfigurationSection("placement"), defaults.placement()),
                loadProtection(section.getConfigurationSection("protection"), defaults.protection()),
                loadHomes(section.getConfigurationSection("homes"), defaults.homes(), warn),
                loadWorlds(section),
                loadRequiredRoles(section.getConfigurationSection("required-roles"),
                        defaults.requiredRoles(), warn),
                loadWarzone(section.getConfigurationSection("warzone"), defaults.warzone(), warn),
                loadStuck(section.getConfigurationSection("stuck"), defaults.stuck(), warn),
                loadWand(section.getConfigurationSection("wand"), defaults.wand(), warn),
                loadLock(section.getConfigurationSection("lock"), defaults.lock(), warn),
                loadMap(section.getConfigurationSection("map"), defaults.map(), warn));
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

    private static ClaimSettings.SizeRules loadSizes(ConfigurationSection section,
                                                    ClaimSettings.SizeRules defaults, Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        int minSide = Math.max(1, section.getInt("min-side", defaults.minSide()));
        int maxSide = Math.max(0, section.getInt("max-side", defaults.maxSide()));
        if (maxSide > 0 && maxSide < minSide) {
            warn.accept("sizes.max-side (" + maxSide + ") is below sizes.min-side (" + minSide
                    + "), so no claim could ever be made; using min-side as the maximum.");
            maxSide = minSide;
        }
        return new ClaimSettings.SizeRules(minSide, maxSide,
                Math.max(0, section.getInt("max-claims", defaults.maxClaims())),
                Math.max(0L, section.getLong("max-total-area", defaults.maxTotalArea())));
    }

    private static ClaimSettings.PriceRules loadPrice(ConfigurationSection section,
                                                     ClaimSettings.PriceRules defaults, Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        double perBlock = section.getDouble("per-block", defaults.perBlock());
        if (!(perBlock >= 0) || !Double.isFinite(perBlock)) {
            warn.accept("price.per-block must be 0 or more; using " + defaults.perBlock() + ".");
            perBlock = defaults.perBlock();
        }
        double refund = section.getDouble("refund-percent", defaults.refundPercent());
        if (!(refund >= 0 && refund <= 100)) {
            warn.accept("price.refund-percent must be between 0 and 100; using " + defaults.refundPercent() + ".");
            refund = defaults.refundPercent();
        }
        return new ClaimSettings.PriceRules(perBlock, refund);
    }

    private static ClaimSettings.WandRules loadWand(ConfigurationSection section,
                                                   ClaimSettings.WandRules defaults, Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        String material = section.getString("material", defaults.material());
        if (org.bukkit.Material.matchMaterial(String.valueOf(material)) == null
                || !org.bukkit.Material.matchMaterial(material).isItem()) {
            warn.accept("wand.material '" + material + "' is not an item; using " + defaults.material() + ".");
            material = defaults.material();
        }
        String pillar = section.getString("pillar-material", defaults.pillarMaterial());
        if (org.bukkit.Material.matchMaterial(String.valueOf(pillar)) == null
                || !org.bukkit.Material.matchMaterial(pillar).isBlock()) {
            warn.accept("wand.pillar-material '" + pillar + "' is not a block; using " + defaults.pillarMaterial() + ".");
            pillar = defaults.pillarMaterial();
        }
        String marker = section.getString("pillar-marker-material", defaults.pillarMarkerMaterial());
        if (!isFullBlock(marker)) {
            warn.accept("wand.pillar-marker-material '" + marker + "' is not a block; using "
                    + defaults.pillarMarkerMaterial() + ".");
            marker = defaults.pillarMarkerMaterial();
        }
        return new ClaimSettings.WandRules(material,
                section.getString("name", defaults.name()),
                section.isList("lore") ? section.getStringList("lore") : defaults.lore(),
                pillar,
                marker,
                Math.max(1, Math.min(64, section.getInt("pillar-marker-every", defaults.pillarMarkerEvery()))),
                Math.max(1, Math.min(64, section.getInt("pillar-height", defaults.pillarHeight()))));
    }

    /** Reads {@code lock.wall}: what a locked claim shows the players it refuses. */
    private static ClaimSettings.LockRules loadLock(ConfigurationSection section,
                                                    ClaimSettings.LockRules defaults, Consumer<String> warn) {
        ConfigurationSection wall = section == null ? null : section.getConfigurationSection("wall");
        if (wall == null) {
            return defaults;
        }
        String material = wall.getString("material", defaults.material());
        org.bukkit.Material block = material == null ? null : org.bukkit.Material.matchMaterial(material.trim());
        if (block == null || !block.isBlock()) {
            warn.accept("lock.wall.material '" + material + "' is not a block; using " + defaults.material() + ".");
            material = defaults.material();
        }
        return new ClaimSettings.LockRules(
                wall.getBoolean("enabled", defaults.wallEnabled()),
                material,
                wall.getInt("top-y", defaults.topY()),
                wall.getInt("minimum-height", defaults.minimumHeight()),
                wall.getInt("radius-blocks", defaults.radiusBlocks()),
                wall.getInt("show-within-blocks", defaults.showWithinBlocks()),
                wall.getLong("refresh-seconds", defaults.refreshSeconds()));
    }

    /**
     * Reads {@code map}. A pillar material that is not a full, solid block is left out
     * with a warning: a slab or a torch on a corner reads as decoration, and glass
     * panes and the like cost the client more to draw than a cube.
     */
    private static ClaimSettings.MapRules loadMap(ConfigurationSection section,
                                                  ClaimSettings.MapRules defaults, Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        String styleName = section.getString("style", defaults.style().name());
        ClaimSettings.MapStyle style = ClaimSettings.MapStyle.of(styleName);
        if (style == null) {
            warn.accept("map.style '" + styleName + "' is not pillars or chat; using "
                    + defaults.style().name().toLowerCase(java.util.Locale.ROOT) + ".");
            style = defaults.style();
        }
        ConfigurationSection chat = section.getConfigurationSection("chat");
        ConfigurationSection pillars = section.getConfigurationSection("pillars");
        List<String> materials = new ArrayList<>();
        for (String material : pillars == null ? List.<String>of() : pillars.getStringList("materials")) {
            if (isFullBlock(material)) {
                materials.add(material.trim().toUpperCase(java.util.Locale.ROOT));
            } else {
                warn.accept("map.pillars.materials: '" + material + "' is not a full block; left out.");
            }
        }
        if (materials.isEmpty()) {
            if (pillars != null && pillars.isList("materials")) {
                warn.accept("map.pillars.materials holds no full block; the built-in list is used.");
            }
            materials = defaults.materials();
        }
        return new ClaimSettings.MapRules(style,
                chat == null ? defaults.cellBlocks() : chat.getInt("cell-blocks", defaults.cellBlocks()),
                chat == null ? defaults.chatRadiusX() : chat.getInt("radius-x", defaults.chatRadiusX()),
                chat == null ? defaults.chatRadiusZ() : chat.getInt("radius-z", defaults.chatRadiusZ()),
                pillars == null ? defaults.radiusChunks() : pillars.getInt("radius-chunks", defaults.radiusChunks()),
                pillars == null ? defaults.topY() : pillars.getInt("top-y", defaults.topY()),
                pillars == null ? defaults.minimumHeight() : pillars.getInt("minimum-height", defaults.minimumHeight()),
                pillars == null ? defaults.seconds() : pillars.getLong("seconds", defaults.seconds()),
                materials);
    }

    /**
     * @return whether the name is a solid block - what a column may be made of. Solid
     *         rather than occluding: glowstone and glass are the two the wand itself
     *         uses, and neither is occluding (found when the marker block was first
     *         configured, 22/09/2026). What this keeps out is what has no collision at
     *         all - a torch, a flower, a sign - which reads as decoration on a corner.
     */
    private static boolean isFullBlock(String name) {
        org.bukkit.Material material = name == null ? null : org.bukkit.Material.matchMaterial(name.trim());
        return material != null && material.isBlock() && material.isSolid();
    }

    private static ClaimSettings.PlacementRules loadPlacement(ConfigurationSection section,
                                                             ClaimSettings.PlacementRules defaults) {
        if (section == null) {
            return defaults;
        }
        return new ClaimSettings.PlacementRules(
                section.getBoolean("require-connected", defaults.requireConnected()),
                Math.max(0, section.getInt("buffer-blocks", defaults.bufferBlocks())),
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
