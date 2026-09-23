package com.lawkeys.hcfcore.economy;

/** Every language key the economy module can produce. Audited against {@code lang/en.yml}. */
public final class EconomyMessages {

    private EconomyMessages() {
    }

    // Balance
    public static final String BALANCE_SELF = "economy.balance.self";
    public static final String BALANCE_OTHER = "economy.balance.other";

    // Pay
    public static final String PAY_SENT = "economy.pay.sent";
    public static final String PAY_RECEIVED = "economy.pay.received";
    public static final String PAY_DISABLED = "economy.pay.disabled";
    public static final String PAY_SELF = "economy.pay.self";
    public static final String PAY_BELOW_MINIMUM = "economy.pay.below-minimum";

    // Staff economy management
    public static final String ADMIN_GIVEN = "economy.admin.given";
    public static final String ADMIN_TAKEN = "economy.admin.taken";
    public static final String ADMIN_SET = "economy.admin.set";
    public static final String ADMIN_RECEIVED = "economy.admin.received";

    // Team bank
    public static final String BANK_DEPOSITED = "economy.bank.deposited";
    public static final String BANK_WITHDRAWN = "economy.bank.withdrawn";

    // Shared failures
    public static final String INSUFFICIENT_FUNDS = "economy.error.insufficient-funds";
    public static final String INVALID_AMOUNT = "economy.error.invalid-amount";
    public static final String ABOVE_MAXIMUM = "economy.error.above-maximum";
    public static final String DISABLED = "economy.error.disabled";
    public static final String PLAYER_NOT_FOUND = "economy.error.player-not-found";
    public static final String NOT_IN_TEAM = "economy.error.not-in-team";

    // economy.yml, kill-reward
    public static final String KILL_REWARD = "economy.kill-reward.earned";
    public static final String KILL_REWARD_STOLEN = "economy.kill-reward.stolen";
}
