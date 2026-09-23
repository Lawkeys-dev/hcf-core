package com.lawkeys.hcfcore.economy.shop;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopSignTest {

    @Test
    void aBuySignIsReadAsWritten() {
        assertEquals(Optional.of(new ShopSign(true, 16, "Ender Pearl", 400.0)),
                ShopSign.read(List.of("[buy]", "16", " Ender Pearl ", "400"), "[Buy]", "[Sell]"));
    }

    @Test
    void aRewrittenPriceStillReads() {
        assertEquals(1200.0, ShopSign.read(List.of("[Sell]", "16", "Diamond", "$1,200.00"), "[Buy]", "[Sell]")
                .orElseThrow().price());
    }

    @Test
    void anythingElseIsNotASign() {
        assertTrue(ShopSign.read(List.of("[Elevator]", "Up", "", ""), "[Buy]", "[Sell]").isEmpty());
        assertTrue(ShopSign.read(List.of("[Buy]", "lots", "Diamond", "400"), "[Buy]", "[Sell]").isEmpty());
        assertTrue(ShopSign.read(List.of("[Buy]", "65", "Diamond", "400"), "[Buy]", "[Sell]").isEmpty());
        assertTrue(ShopSign.read(List.of("[Buy]", "16", "Diamond", "free"), "[Buy]", "[Sell]").isEmpty());
        assertTrue(ShopSign.read(List.of("[Buy]", "16"), "[Buy]", "[Sell]").isEmpty());
    }
}
