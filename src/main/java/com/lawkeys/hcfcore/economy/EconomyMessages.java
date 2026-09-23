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

    // economy.yml, shop
    public static final String SHOP_BOUGHT = "economy.shop.bought";
    public static final String SHOP_SOLD = "economy.shop.sold";
    public static final String SHOP_NO_MONEY = "economy.shop.no-money";
    public static final String SHOP_NO_ROOM = "economy.shop.no-room";
    public static final String SHOP_NOTHING_TO_SELL = "economy.shop.nothing-to-sell";
    public static final String SHOP_NOT_BUYABLE = "economy.shop.not-buyable";
    public static final String SHOP_NOT_SELLABLE = "economy.shop.not-sellable";
    public static final String SHOP_DISABLED = "economy.shop.disabled";
    public static final String SHOP_MENU_DISABLED = "economy.shop.menu-disabled";
    public static final String SHOP_EMPTY = "economy.shop.empty";
    public static final String SHOP_PLAYERS_ONLY = "economy.shop.players-only";
    public static final String SHOP_SIGN_BUY = "economy.shop.sign-buy";
    public static final String SHOP_SIGN_SELL = "economy.shop.sign-sell";
    public static final String SHOP_SIGN_CREATED = "economy.shop.sign-created";
    public static final String SHOP_SIGN_INVALID = "economy.shop.sign-invalid";
    public static final String SHOP_SIGN_NO_PERMISSION = "economy.shop.sign-no-permission";
    public static final String SHOP_MENU_TITLE = "economy.shop.menu-title";
    public static final String SHOP_ITEM_NAME = "economy.shop.item-name";
    public static final String SHOP_LORE_BUY = "economy.shop.lore-buy";
    public static final String SHOP_LORE_SELL = "economy.shop.lore-sell";
    public static final String SHOP_LORE_NOT_BUYABLE = "economy.shop.lore-not-buyable";
    public static final String SHOP_LORE_NOT_SELLABLE = "economy.shop.lore-not-sellable";
    public static final String SHOP_LORE_HINT = "economy.shop.lore-hint";
    public static final String SHOP_CATEGORY_TITLE = "economy.shop.category-title";
    public static final String SHOP_CATEGORY_NAME = "economy.shop.category-name";
    public static final String SHOP_CATEGORY_LORE = "economy.shop.category-lore";
    public static final String SHOP_CATEGORY_HINT = "economy.shop.category-hint";
    public static final String SHOP_BACK = "economy.shop.back";

    // economy.yml, bounties
    public static final String BOUNTY_PLACED = "economy.bounty.placed";
    public static final String BOUNTY_PLACED_BROADCAST = "economy.bounty.placed-broadcast";
    public static final String BOUNTY_CLAIMED = "economy.bounty.claimed";
    public static final String BOUNTY_LIST_HEADER = "economy.bounty.list-header";
    public static final String BOUNTY_LIST_ENTRY = "economy.bounty.list-entry";
    public static final String BOUNTY_LIST_EMPTY = "economy.bounty.list-empty";
    public static final String BOUNTY_CHECK = "economy.bounty.check";
    public static final String BOUNTY_CHECK_NONE = "economy.bounty.check-none";
    public static final String BOUNTY_CLEARED = "economy.bounty.cleared";
    public static final String BOUNTY_UNKNOWN_PLAYER = "economy.bounty.unknown-player";
    public static final String BOUNTY_SELF = "economy.bounty.self";
    public static final String BOUNTY_BELOW_MINIMUM = "economy.bounty.below-minimum";
    public static final String BOUNTY_DISABLED = "economy.bounty.disabled";
    public static final String BOUNTY_USAGE = "economy.bounty.usage";
    public static final String BOUNTY_PLAYERS_ONLY = "economy.bounty.players-only";
}
