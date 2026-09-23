package com.lawkeys.hcfcore.economy.shop;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopMathTest {

    @Test
    void oneLotIsSoldOrEverythingHeldInWholeLots() {
        assertEquals(1, ShopMath.lotsToSell(40, 16, false));
        assertEquals(2, ShopMath.lotsToSell(40, 16, true));
        assertEquals(0, ShopMath.lotsToSell(15, 16, true), "never a part of a lot");
    }

    @Test
    void buyingIsCutToWhatIsAffordableAndCarried() {
        assertEquals(1, ShopMath.lotsToBuy(1, 16, 100.0, 1000.0, 64));
        assertEquals(3, ShopMath.lotsToBuy(10, 16, 100.0, 350.0, 640), "three lots of money");
        assertEquals(2, ShopMath.lotsToBuy(10, 16, 100.0, 5000.0, 40), "two lots of room");
        assertEquals(0, ShopMath.lotsToBuy(1, 16, 100.0, 99.99, 64));
        assertEquals(1, ShopMath.lotsToBuy(1, 16, 0.1 + 0.2, 0.3, 64), "no rounding against the player");
    }

    @Test
    void modesSayWhereTheShopIs() {
        assertTrue(ShopRules.Mode.BOTH.signs() && ShopRules.Mode.BOTH.menu());
        assertFalse(ShopRules.Mode.SIGNS.menu());
        assertFalse(ShopRules.Mode.MENU.signs());
        assertEquals(Optional.of(ShopRules.Mode.MENU), ShopRules.Mode.of(" Menu "));
    }

    @Test
    void anItemIsKeptInRange() {
        ShopRules.Item item = new ShopRules.Item("DIAMOND", 100, -1.0, 25.0);
        assertEquals(64, item.amount());
        assertFalse(item.buyable());
        assertTrue(item.sellable());
        assertEquals("Deepslate Diamond Ore", com.lawkeys.hcfcore.util.MaterialNames.readable("DEEPSLATE_DIAMOND_ORE"));
    }
}
