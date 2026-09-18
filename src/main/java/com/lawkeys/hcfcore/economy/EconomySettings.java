package com.lawkeys.hcfcore.economy;

import java.util.Objects;

/**
 * Immutable snapshot of {@code economy.yml}.
 *
 * <p>Held through a {@code Supplier} so {@code /hcf reload} applies at once
 * (ARCHITECTURE.md section 2).
 *
 * @param maximumBalance ceiling per account; {@code 0} means no ceiling
 */
public record EconomySettings(
        boolean enabled,
        double startingBalance,
        double maximumBalance,
        CurrencyFormat currency,
        PayRules pay) {

    public EconomySettings {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(pay, "pay");
    }

    /**
     * @param symbol   prefix shown before an amount, e.g. {@code $}
     * @param singular unit name for exactly one, e.g. {@code dollar}
     * @param plural   unit name otherwise
     */
    public record CurrencyFormat(String symbol, String singular, String plural) {

        public CurrencyFormat {
            Objects.requireNonNull(symbol, "symbol");
            Objects.requireNonNull(singular, "singular");
            Objects.requireNonNull(plural, "plural");
        }
    }

    /** @param minimumAmount smallest transfer allowed, to stop 0.01 spam */
    public record PayRules(boolean enabled, double minimumAmount) {
    }

    /** Built-in fallback, mirroring {@code resources/economy.yml}. */
    public static EconomySettings defaults() {
        return new EconomySettings(
                true,
                100.0,
                0.0,
                new CurrencyFormat("$", "dollar", "dollars"),
                new PayRules(true, 1.0));
    }
}
