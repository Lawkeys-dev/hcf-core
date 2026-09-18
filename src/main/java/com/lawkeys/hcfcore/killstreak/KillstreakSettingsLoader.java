package com.lawkeys.hcfcore.killstreak;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Turns {@code killstreaks.yml} into an immutable {@link KillstreakRewards}. */
public final class KillstreakSettingsLoader {

    private KillstreakSettingsLoader() {
    }

    public static boolean isEnabled(ConfigurationSection section) {
        return section == null || section.getBoolean("enabled", true);
    }

    public static KillstreakRewards load(ConfigurationSection section, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        if (section == null) {
            return KillstreakRewards.empty();
        }
        ConfigurationSection rewards = section.getConfigurationSection("rewards");
        if (rewards == null) {
            return KillstreakRewards.empty();
        }
        List<KillstreakReward> parsed = new ArrayList<>();
        for (String key : rewards.getKeys(false)) {
            int streak;
            try {
                streak = Integer.parseInt(key.trim());
            } catch (NumberFormatException e) {
                warn.accept("rewards." + key + " is not a streak number; ignored.");
                continue;
            }
            ConfigurationSection entry = rewards.getConfigurationSection(key);
            if (entry == null) {
                warn.accept("rewards." + key + " is not a block of settings; ignored.");
                continue;
            }
            parsed.add(new KillstreakReward(streak,
                    entry.getString("broadcast", ""), entry.getStringList("commands")));
        }
        return KillstreakRewards.of(parsed, message -> warn.accept("rewards: " + message));
    }
}
