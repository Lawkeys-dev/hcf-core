package com.lawkeys.hcfcore.economy.command;

import java.util.Locale;

/**
 * {@code /team deposit all} and {@code /team withdraw all}: how much "all" is. Pure.
 *
 * <p>Whole cents only, rounded down, so "all" never asks for a fraction of a cent
 * the ledger would then refuse.
 */
final class BankAll {

    /** The word that stands for everything in {@code /team deposit} and {@code /team withdraw}. */
    static final String WORD = "all";

    private BankAll() {
    }

    static boolean is(String input) {
        return input != null && WORD.equals(input.trim().toLowerCase(Locale.ROOT));
    }

    /** @return the whole wallet */
    static double deposit(double balance) {
        return cents(balance);
    }

    /**
     * @param maximumBalance the ceiling per account, {@code 0} for none
     * @return the whole bank, or as much of it as the player's wallet can still take
     */
    static double withdraw(double bank, double balance, double maximumBalance) {
        double room = maximumBalance > 0 ? maximumBalance - balance : bank;
        return cents(Math.min(bank, room));
    }

    private static double cents(double amount) {
        return amount <= 0 || !Double.isFinite(amount) ? 0 : Math.floor(amount * 100) / 100;
    }
}
