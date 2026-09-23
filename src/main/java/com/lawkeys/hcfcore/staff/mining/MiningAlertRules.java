package com.lawkeys.hcfcore.staff.mining;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * {@code staff.yml}, {@code mining-alerts}: who hears that a player found a vein of
 * a valuable ore - the "Found Diamonds" line of HCF servers, and the staff's first
 * look at an x-ray client (the project owner's request, 23/09/2026).
 *
 * @param ores         the blocks reported, as {@code Material} spells them
 * @param maxVein      the most blocks one vein is counted up to
 * @param everyone     whether the line goes to everybody, as the classic public
 *                     "[FD]" line does, or to staff only. Staff always get the
 *                     position; everybody else never does
 * @param ignoreCreative whether a player in creative mining is left out
 */
public record MiningAlertRules(boolean enabled, Set<String> ores, int maxVein, boolean everyone,
                               boolean ignoreCreative) {

    public MiningAlertRules {
        ores = Set.copyOf(Objects.requireNonNull(ores, "ores"));
        maxVein = Math.max(1, Math.min(256, maxVein));
    }

    public static MiningAlertRules defaults() {
        return new MiningAlertRules(true, Set.of("DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE", "ANCIENT_DEBRIS",
                "EMERALD_ORE", "DEEPSLATE_EMERALD_ORE"), 32, false, true);
    }

    public boolean reports(Material material) {
        return enabled && ores.contains(material.name());
    }

    /** Reads the section; an ore the server does not know is left out with a warning. */
    public static MiningAlertRules load(ConfigurationSection section, Consumer<String> warn) {
        MiningAlertRules d = defaults();
        if (section == null) {
            return d;
        }
        Set<String> ores = new LinkedHashSet<>();
        List<String> listed = section.isList("ores") ? section.getStringList("ores") : List.copyOf(d.ores());
        for (String raw : listed) {
            Material material = raw == null ? null : Material.matchMaterial(raw.trim());
            if (material == null || !material.isBlock()) {
                warn.accept("mining-alerts.ores: '" + raw + "' is not a block; left out.");
                continue;
            }
            ores.add(material.name());
        }
        String audience = section.getString("audience", d.everyone() ? "everyone" : "staff");
        boolean everyone = "everyone".equalsIgnoreCase(audience == null ? "" : audience.trim());
        if (!everyone && !"staff".equalsIgnoreCase(audience == null ? "" : audience.trim())) {
            warn.accept("mining-alerts.audience: '" + audience + "' is not staff or everyone; staff is used.");
        }
        return new MiningAlertRules(section.getBoolean("enabled", d.enabled()), ores,
                section.getInt("max-vein", d.maxVein()), everyone,
                section.getBoolean("ignore-creative", d.ignoreCreative()));
    }

    /** @return {@code DEEPSLATE_DIAMOND_ORE} as {@code Deepslate Diamond Ore} */
    public static String readable(Material material) {
        StringBuilder name = new StringBuilder();
        for (String word : material.name().toLowerCase(Locale.ROOT).split("_")) {
            if (!word.isEmpty()) {
                name.append(name.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1));
            }
        }
        return name.toString();
    }
}
