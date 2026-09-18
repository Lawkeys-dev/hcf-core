package com.lawkeys.hcfcore.economy;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Turns {@code economy.yml} into an immutable {@link EconomySettings}.
 *
 * <p>Invalid values are reported and replaced by the built-in default rather than
 * taking the server down (ARCHITECTURE.md section 6).
 */
public final class EconomySettingsLoader {

    private EconomySettingsLoader() {
    }

    public static EconomySettings load(ConfigurationSection section, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        EconomySettings defaults = EconomySettings.defaults();
        if (section == null) {
            warn.accept("economy.yml is missing or empty - using built-in defaults.");
            return defaults;
        }

        double starting = section.getDouble("starting-balance", defaults.startingBalance());
        if (starting < 0) {
            warn.accept("starting-balance cannot be negative; using " + defaults.startingBalance() + ".");
            starting = defaults.startingBalance();
        }
        double maximum = section.getDouble("maximum-balance", defaults.maximumBalance());
        if (maximum < 0) {
            warn.accept("maximum-balance cannot be negative (use 0 for no ceiling); using 0.");
            maximum = 0.0;
        }
        if (maximum > 0 && maximum < starting) {
            warn.accept("maximum-balance (" + maximum + ") is below starting-balance (" + starting
                    + "), so every new account would be over the ceiling; using no ceiling.");
            maximum = 0.0;
        }

        return new EconomySettings(
                section.getBoolean("enabled", defaults.enabled()),
                starting,
                maximum,
                loadCurrency(section.getConfigurationSection("currency"), defaults.currency()),
                loadPay(section.getConfigurationSection("pay"), defaults.pay(), warn));
    }

    private static EconomySettings.CurrencyFormat loadCurrency(ConfigurationSection section,
                                                              EconomySettings.CurrencyFormat defaults) {
        if (section == null) {
            return defaults;
        }
        return new EconomySettings.CurrencyFormat(
                section.getString("symbol", defaults.symbol()),
                section.getString("singular", defaults.singular()),
                section.getString("plural", defaults.plural()));
    }

    private static EconomySettings.PayRules loadPay(ConfigurationSection section,
                                                   EconomySettings.PayRules defaults,
                                                   Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        double minimum = section.getDouble("minimum-amount", defaults.minimumAmount());
        if (minimum < 0) {
            warn.accept("pay.minimum-amount cannot be negative; using " + defaults.minimumAmount() + ".");
            minimum = defaults.minimumAmount();
        }
        return new EconomySettings.PayRules(
                section.getBoolean("enabled", defaults.enabled()), minimum);
    }
}
