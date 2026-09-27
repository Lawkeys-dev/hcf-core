package com.lawkeys.hcfcore.economy.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankAllTest {

    @Test
    void allIsReadWithoutCase() {
        assertTrue(BankAll.is("all"));
        assertTrue(BankAll.is(" ALL "));
        assertFalse(BankAll.is("100"));
        assertFalse(BankAll.is(null));
    }

    @Test
    void depositingAllIsTheWholeWalletInWholeCents() {
        assertEquals(1234.56, BankAll.deposit(1234.567));
        assertEquals(0, BankAll.deposit(0));
        assertEquals(0, BankAll.deposit(-5));
    }

    @Test
    void withdrawingAllStopsAtTheWalletsCeiling() {
        assertEquals(5000, BankAll.withdraw(5000, 100, 0), "no ceiling: the whole bank");
        assertEquals(900, BankAll.withdraw(5000, 100, 1000), "only what the wallet can still take");
        assertEquals(0, BankAll.withdraw(5000, 1000, 1000), "a full wallet takes nothing");
        assertEquals(0, BankAll.withdraw(0, 0, 0), "an empty bank gives nothing");
    }
}
