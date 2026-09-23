package com.lawkeys.hcfcore.economy.shop;

/**
 * How many lots a trade moves - pure arithmetic, unit-tested, kept apart from the
 * inventories and balances it is applied to.
 */
public final class ShopMath {

    private ShopMath() {
    }

    /**
     * @param held how many of the item the player holds
     * @param all  sell everything held, lot by lot, rather than one lot
     * @return the lots sold: never a part of one
     */
    public static int lotsToSell(int held, int lotSize, boolean all) {
        if (lotSize <= 0 || held < lotSize) {
            return 0;
        }
        return all ? held / lotSize : 1;
    }

    /**
     * @param wanted  the lots asked for
     * @param balance what the player has
     * @param room    how many of the item their inventory can still take
     * @return the lots bought: as many as asked, cut to what they can pay for and carry
     */
    public static int lotsToBuy(int wanted, int lotSize, double price, double balance, int room) {
        if (wanted <= 0 || lotSize <= 0 || price <= 0) {
            return 0;
        }
        int affordable = (int) Math.floor(balance / price + 1e-9);
        int carried = room / lotSize;
        return Math.max(0, Math.min(wanted, Math.min(affordable, carried)));
    }
}
