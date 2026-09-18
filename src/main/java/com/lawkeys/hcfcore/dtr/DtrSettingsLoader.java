package com.lawkeys.hcfcore.dtr;

import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Turns {@code dtr.yml} into an immutable {@link DtrSettings}.
 *
 * <p>Invalid values are reported and replaced by the built-in default rather than
 * taking the server down (ARCHITECTURE.md section 6).
 */
public final class DtrSettingsLoader {

    private DtrSettingsLoader() {
    }

    /**
     * @param section the root of {@code dtr.yml}, or {@code null} when absent
     * @param warn    receives one line per invalid value
     */
    public static DtrSettings load(ConfigurationSection section, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        DtrSettings defaults = DtrSettings.defaults();
        if (section == null) {
            warn.accept("dtr.yml is missing or empty - using built-in defaults for the DTR module.");
            return defaults;
        }

        double loss = section.getDouble("loss-per-death", defaults.lossPerDeath());
        if (loss < 0) {
            warn.accept("loss-per-death cannot be negative (a death would grant DTR); using "
                    + defaults.lossPerDeath() + ".");
            loss = defaults.lossPerDeath();
        }

        double minimum = section.getDouble("minimum", defaults.minimum());
        if (minimum > 0) {
            warn.accept("minimum (" + minimum + ") is above zero, which would make it impossible for "
                    + "a team to ever become raidable; using " + defaults.minimum() + ".");
            minimum = defaults.minimum();
        }

        return new DtrSettings(
                section.getBoolean("enabled", defaults.enabled()),
                loadMaximum(section.getConfigurationSection("maximum"), defaults.maximum(), warn),
                loss,
                minimum,
                loadRegeneration(section.getConfigurationSection("regeneration"),
                        defaults.regeneration(), warn),
                loadAnnouncements(section.getConfigurationSection("announcements"),
                        defaults.announcements(), warn));
    }

    private static DtrSettings.MaximumRules loadMaximum(ConfigurationSection section,
                                                       DtrSettings.MaximumRules defaults,
                                                       Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        double base = section.getDouble("base", defaults.base());
        double perMember = section.getDouble("per-member", defaults.perMember());
        double cap = Math.max(0.0, section.getDouble("cap", defaults.cap()));

        if (base <= 0 && perMember <= 0) {
            warn.accept("maximum.base and maximum.per-member are both zero or less, so every team "
                    + "would start permanently raidable; using the defaults.");
            return defaults;
        }
        return new DtrSettings.MaximumRules(base, perMember, cap);
    }

    private static DtrSettings.RegenerationRules loadRegeneration(ConfigurationSection section,
                                                                 DtrSettings.RegenerationRules defaults,
                                                                 Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        long freeze = Math.max(0L, Durations.capSeconds(section.getLong("freeze-seconds", defaults.freezeSeconds()), "freeze-seconds", warn));
        double amount = Math.max(0.0, section.getDouble("amount", defaults.amount()));
        long interval = Durations.capSeconds(section.getLong("interval-seconds", defaults.intervalSeconds()), "interval-seconds", warn);

        if (interval <= 0 && amount > 0) {
            warn.accept("regeneration.interval-seconds must be positive when an amount is set; using "
                    + defaults.intervalSeconds() + ".");
            interval = defaults.intervalSeconds();
        }
        return new DtrSettings.RegenerationRules(freeze, amount, Math.max(0L, interval));
    }

    private static DtrSettings.AnnouncementRules loadAnnouncements(ConfigurationSection section,
                                                                  DtrSettings.AnnouncementRules defaults, Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        return new DtrSettings.AnnouncementRules(
                section.getBoolean("on-death", defaults.onDeath()),
                section.getBoolean("on-raidable-change", defaults.onRaidableChange()),
                Math.max(0L, Durations.capSeconds(section.getLong("poll-seconds", defaults.pollSeconds()), "poll-seconds", warn)));
    }
}
