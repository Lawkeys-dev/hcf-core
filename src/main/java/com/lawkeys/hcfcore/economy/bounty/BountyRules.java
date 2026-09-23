package com.lawkeys.hcfcore.economy.bounty;

import org.bukkit.configuration.ConfigurationSection;

/**
 * {@code economy.yml}, {@code bounties}.
 *
 * @param minimumAmount the least one placement may add, against 1-coin spam
 * @param listSize      how many bounties {@code /bounty} lists
 */
public record BountyRules(boolean enabled, double minimumAmount, boolean announce, int listSize) {

    public BountyRules {
        minimumAmount = Double.isFinite(minimumAmount) ? Math.max(0.01, minimumAmount) : 100.0;
        listSize = Math.max(1, Math.min(50, listSize));
    }

    public static BountyRules defaults() {
        return new BountyRules(true, 50.0, true, 10);
    }

    public static BountyRules load(ConfigurationSection section) {
        BountyRules d = defaults();
        if (section == null) {
            return d;
        }
        return new BountyRules(section.getBoolean("enabled", d.enabled()),
                section.getDouble("minimum-amount", d.minimumAmount()),
                section.getBoolean("announce", d.announce()),
                section.getInt("list-size", d.listSize()));
    }
}
