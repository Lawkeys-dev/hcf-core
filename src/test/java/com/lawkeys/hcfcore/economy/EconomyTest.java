package com.lawkeys.hcfcore.economy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rule-level tests for the economy module. */
class EconomyTest {

    private EconomySettings settings;
    private EconomyManager economy;

    private UUID alice;
    private UUID bob;

    @BeforeEach
    void setUp() {
        settings = EconomySettings.defaults();
        economy = new EconomyManager(() -> settings, EconomyStore.NO_OP);
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
    }

    private void reconfigure(EconomySettings replacement) {
        this.settings = replacement;
    }

    // ------------------------------------------------------------------

    @Nested
    class Balances {

        @Test
        void anUntouchedAccountReportsTheStartingBalanceWithoutARow() {
            assertEquals(100.0, economy.getBalance(alice), 1e-9);
            assertFalse(economy.hasAccount(alice), "reading must not create an account row");
            assertEquals(0, economy.getAccountCount());
        }

        @Test
        void depositAndWithdrawMoveTheBalance() {
            assertTrue(economy.deposit(alice, 50.0).isOk());
            assertEquals(150.0, economy.getBalance(alice), 1e-9);

            assertTrue(economy.withdraw(alice, 30.0).isOk());
            assertEquals(120.0, economy.getBalance(alice), 1e-9);
        }

        @Test
        void youCannotWithdrawMoreThanYouHave() {
            assertEquals(EconomyResult.INSUFFICIENT_FUNDS, economy.withdraw(alice, 100.01));
            assertEquals(100.0, economy.getBalance(alice), 1e-9, "a refused debit must not move anything");
        }

        @Test
        void nonPositiveAndNonFiniteAmountsAreRefused() {
            assertEquals(EconomyResult.INVALID_AMOUNT, economy.deposit(alice, 0.0));
            assertEquals(EconomyResult.INVALID_AMOUNT, economy.deposit(alice, -5.0));
            assertEquals(EconomyResult.INVALID_AMOUNT, economy.deposit(alice, Double.NaN));
            assertEquals(EconomyResult.INVALID_AMOUNT, economy.deposit(alice, Double.POSITIVE_INFINITY));
            assertEquals(EconomyResult.INVALID_AMOUNT, economy.withdraw(alice, -1.0));
        }

        @Test
        void theCeilingIsEnforcedOnCreditsAndOnStaffOverrides() {
            reconfigure(withMaximum(500.0));

            assertEquals(EconomyResult.ABOVE_MAXIMUM, economy.deposit(alice, 401.0));
            assertEquals(100.0, economy.getBalance(alice), 1e-9);

            assertTrue(economy.deposit(alice, 400.0).isOk());
            assertEquals(EconomyResult.ABOVE_MAXIMUM, economy.set(alice, 501.0));
        }

        @Test
        void aZeroCeilingMeansNoCeiling() {
            assertTrue(economy.deposit(alice, 1_000_000_000.0).isOk());
        }

        @Test
        void noCeilingStillStopsShortOfInfinity() {
            // MySQL refuses to store Infinity: an account holding it could never be
            // saved again (found in the command review, 15/09/2026).
            assertTrue(economy.deposit(alice, Double.MAX_VALUE).isOk());
            double before = economy.getBalance(alice);

            assertEquals(EconomyResult.ABOVE_MAXIMUM, economy.deposit(alice, Double.MAX_VALUE));
            assertEquals(before, economy.getBalance(alice), "a refused credit changes nothing");
            assertTrue(Double.isFinite(economy.getBalance(alice)));
        }

        @Test
        void staffCanSetABalanceOutright() {
            assertTrue(economy.set(alice, 42.0).isOk());
            assertEquals(42.0, economy.getBalance(alice), 1e-9);

            assertTrue(economy.set(alice, 0.0).isOk(), "zero is a legitimate balance");
            assertEquals(EconomyResult.INVALID_AMOUNT, economy.set(alice, -1.0));
        }

        @Test
        void theModuleCanBeDisabled() {
            reconfigure(withEnabled(false));

            assertEquals(EconomyResult.DISABLED, economy.deposit(alice, 10.0));
            assertEquals(EconomyResult.DISABLED, economy.withdraw(alice, 10.0));
            assertEquals(EconomyResult.DISABLED, economy.transfer(alice, bob, 10.0));
        }
    }

    @Nested
    class Transfers {

        @Test
        void payingMovesMoneyBetweenTwoAccounts() {
            assertTrue(economy.transfer(alice, bob, 40.0).isOk());

            assertEquals(60.0, economy.getBalance(alice), 1e-9);
            assertEquals(140.0, economy.getBalance(bob), 1e-9);
        }

        @Test
        void aRefusedTransferLeavesBothSidesUntouched() {
            assertEquals(EconomyResult.INSUFFICIENT_FUNDS, economy.transfer(alice, bob, 500.0));

            assertEquals(100.0, economy.getBalance(alice), 1e-9);
            assertEquals(100.0, economy.getBalance(bob), 1e-9);
        }

        @Test
        void aCreditRefusedByTheCeilingRollsTheDebitBack() {
            // Money must never be destroyed: if the credit cannot land, the payer
            // gets it back rather than it vanishing between the two accounts.
            reconfigure(withMaximum(120.0));

            assertEquals(EconomyResult.ABOVE_MAXIMUM, economy.transfer(alice, bob, 50.0));

            assertEquals(100.0, economy.getBalance(alice), 1e-9);
            assertEquals(100.0, economy.getBalance(bob), 1e-9);
        }

        @Test
        void aRollbackReturnsTheMoneyEvenWhenTheRulesWouldNowRefuseIt() {
            // A /hcf reload can land between a transfer's two halves. Undoing the
            // first half must not be subject to the new rules, or the money is
            // destroyed outright - so the rollback path goes through restore(),
            // which answers to no setting.
            assertTrue(economy.withdraw(alice, 40.0).isOk(), "the debit half");
            reconfigure(withEnabled(false));

            assertEquals(EconomyResult.DISABLED, economy.deposit(alice, 40.0),
                    "a normal credit is refused, which is what would destroy the money");

            economy.restore(alice, 40.0);
            assertEquals(100.0, economy.getBalance(alice), 1e-9);
        }

        @Test
        void restoreIgnoresAmountsThatAreNotRealMoney() {
            economy.restore(alice, 0.0);
            economy.restore(alice, -10.0);
            economy.restore(alice, Double.NaN);

            assertFalse(economy.hasAccount(alice), "a no-op restore must not open an account");
        }

        @Test
        void payingYourselfIsRefused() {
            assertEquals(EconomyResult.SAME_PLAYER, economy.transfer(alice, alice, 10.0));
        }

        @Test
        void theMinimumTransferIsEnforced() {
            reconfigure(withPay(new EconomySettings.PayRules(true, 5.0)));

            assertEquals(EconomyResult.BELOW_MINIMUM, economy.transfer(alice, bob, 4.99));
            assertTrue(economy.transfer(alice, bob, 5.0).isOk());
        }

        @Test
        void transfersCanBeDisabledWithoutDisablingTheEconomy() {
            reconfigure(withPay(new EconomySettings.PayRules(false, 1.0)));

            assertEquals(EconomyResult.PAY_DISABLED, economy.transfer(alice, bob, 10.0));
            assertTrue(economy.deposit(alice, 10.0).isOk(), "the rest of the economy still works");
        }
    }

    /**
     * The failure mode that matters most in an economy: two operations reading the
     * same balance and both deciding it is affordable.
     */
    @Nested
    class Concurrency {

        @Test
        void concurrentWithdrawalsCannotSpendTheSameMoneyTwice() throws Exception {
            economy.set(alice, 100.0);
            int threads = 16;
            // 100 available, 10 each: exactly ten must succeed, whatever the interleaving.
            AtomicInteger succeeded = runConcurrently(threads,
                    () -> economy.withdraw(alice, 10.0).isOk());

            assertEquals(10, succeeded.get(), "exactly ten withdrawals of 10 fit in 100");
            assertEquals(0.0, economy.getBalance(alice), 1e-9);
        }

        @Test
        void concurrentTransfersConserveTheTotalMoneySupply() throws Exception {
            economy.set(alice, 1000.0);
            economy.set(bob, 1000.0);
            double totalBefore = economy.getBalance(alice) + economy.getBalance(bob);

            int threads = 32;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            try {
                for (int i = 0; i < threads; i++) {
                    // Half the threads push each way, so opposite transfers overlap.
                    UUID from = i % 2 == 0 ? alice : bob;
                    UUID to = i % 2 == 0 ? bob : alice;
                    futures.add(pool.submit(() -> {
                        try {
                            start.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        for (int n = 0; n < 50; n++) {
                            economy.transfer(from, to, 1.0);
                        }
                    }));
                }
                start.countDown();
                for (var future : futures) {
                    future.get(30, TimeUnit.SECONDS);
                }
            } finally {
                pool.shutdownNow();
            }

            assertEquals(totalBefore, economy.getBalance(alice) + economy.getBalance(bob), 1e-6,
                    "money must be neither created nor destroyed by concurrent transfers");
        }

        /** Runs {@code action} on {@code threads} threads at once, counting the successes. */
        private AtomicInteger runConcurrently(int threads, java.util.function.BooleanSupplier action)
                throws Exception {
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger succeeded = new AtomicInteger();
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            try {
                for (int i = 0; i < threads; i++) {
                    futures.add(pool.submit(() -> {
                        try {
                            start.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        if (action.getAsBoolean()) {
                            succeeded.incrementAndGet();
                        }
                    }));
                }
                start.countDown();
                for (var future : futures) {
                    future.get(30, TimeUnit.SECONDS);
                }
            } finally {
                pool.shutdownNow();
            }
            return succeeded;
        }
    }

    @Nested
    class Formatting {

        @Test
        void amountsAreShownWithTheConfiguredSymbolAndGrouping() {
            assertEquals("$1,234.50", economy.format(1234.5));
            assertEquals("$0.00", economy.format(0.0));
        }

        @Test
        void theCurrencyNameAgreesWithTheAmount() {
            assertEquals("1.00 dollar", economy.formatWithName(1.0));
            assertEquals("2.00 dollars", economy.formatWithName(2.0));
            assertEquals("0.00 dollars", economy.formatWithName(0.0));
        }

        @Test
        void theCurrencyIsFullyConfigurable() {
            reconfigure(withCurrency(new EconomySettings.CurrencyFormat("€", "euro", "euros")));

            assertEquals("€10.00", economy.format(10.0));
            assertEquals("1.00 euro", economy.formatWithName(1.0));
        }
    }

    @Nested
    class Persistence {

        @Test
        void flushWritesChangedAccountsOnly() throws Exception {
            RecordingStore store = new RecordingStore();
            EconomyManager persisting = new EconomyManager(() -> settings, store);

            assertEquals(0, persisting.flush(), "nothing touched, nothing written");

            persisting.deposit(alice, 10.0);
            assertEquals(1, persisting.flush());
            assertEquals(List.of(alice), store.saved);

            store.saved.clear();
            assertEquals(0, persisting.flush());
        }

        @Test
        void aFailedWriteKeepsTheAccountQueued() {
            RecordingStore store = new RecordingStore();
            store.failOnSave = true;
            EconomyManager persisting = new EconomyManager(() -> settings, store);
            persisting.deposit(alice, 10.0);

            assertThrows(IllegalStateException.class, persisting::flush);

            store.failOnSave = false;
            try {
                assertEquals(1, persisting.flush(), "the unwritten balance must be retried");
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        @Test
        void loadAllRestoresBalances() throws Exception {
            RecordingStore store = new RecordingStore();
            store.preloaded.put(alice, 777.0);
            EconomyManager loading = new EconomyManager(() -> settings, store);

            loading.loadAll();

            assertEquals(777.0, loading.getBalance(alice), 1e-9);
            assertTrue(loading.hasAccount(alice));
        }
    }

    @Test
    void configChangesApplyWithoutRecreatingTheManager() {
        assertTrue(economy.transfer(alice, bob, 10.0).isOk());

        reconfigure(withPay(new EconomySettings.PayRules(false, 1.0)));

        assertEquals(EconomyResult.PAY_DISABLED, economy.transfer(alice, bob, 10.0),
                "/hcf reload must be enough");
    }

    // --- settings helpers -------------------------------------------------

    private EconomySettings withEnabled(boolean enabled) {
        return new EconomySettings(enabled, settings.startingBalance(), settings.maximumBalance(),
                settings.currency(), settings.pay());
    }

    private EconomySettings withMaximum(double maximum) {
        return new EconomySettings(settings.enabled(), settings.startingBalance(), maximum,
                settings.currency(), settings.pay());
    }

    private EconomySettings withCurrency(EconomySettings.CurrencyFormat currency) {
        return new EconomySettings(settings.enabled(), settings.startingBalance(),
                settings.maximumBalance(), currency, settings.pay());
    }

    private EconomySettings withPay(EconomySettings.PayRules pay) {
        return new EconomySettings(settings.enabled(), settings.startingBalance(),
                settings.maximumBalance(), settings.currency(), pay);
    }

    // --- test double ------------------------------------------------------

    private static class RecordingStore implements EconomyStore {
        final Map<UUID, Double> preloaded = new LinkedHashMap<>();
        final List<UUID> saved = new ArrayList<>();
        boolean failOnSave;

        @Override
        public void initSchema() {
        }

        @Override
        public Map<UUID, Double> loadAll() {
            return Map.copyOf(preloaded);
        }

        @Override
        public void save(UUID playerId, double balance) {
            if (failOnSave) {
                throw new IllegalStateException("simulated write failure");
            }
            saved.add(playerId);
        }
    }
}
