package com.lawkeys.hcfcore.economy;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The economy module's entry point and rule engine.
 *
 * <p>Pure Java, no server API, cache-as-source-of-truth like every other module.
 *
 * <p><strong>Concurrency matters more here than elsewhere.</strong> A balance is
 * read-modify-write, and two concurrent transfers reading the same balance would
 * let a player spend the same money twice. Every mutation therefore goes through
 * {@link java.util.concurrent.ConcurrentHashMap#compute}, which holds the bin
 * lock for the duration - so the check and the write cannot be split. That alone
 * already makes the total money supply safe: a debit that would overdraw is
 * refused inside the lock, and a credit that is refused is rolled back.
 *
 * <p>{@link #transfer} takes one extra lock on top of that, chosen by uuid order
 * so it is the same lock whichever way the money is going. It does not add a
 * conservation guarantee - it narrows the window in which money is in flight
 * between the two accounts, so a concurrent read of the pair is far less likely
 * to observe a half-finished transfer. Only ever one lock is held, so there is
 * no lock-ordering hazard to get wrong.
 */
public final class EconomyManager {

    private final Supplier<EconomySettings> settings;
    private final EconomyStore store;

    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    /** Transfer stripes; see {@link #lockFor(UUID)}. Power of two, never resized. */
    private final Object[] stripes = newStripes(64);

    public EconomyManager(Supplier<EconomySettings> settings, EconomyStore store) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.store = Objects.requireNonNull(store, "store");
    }

    private EconomySettings config() {
        return settings.get();
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    /**
     * @return the player's balance; an account nobody has touched yet reports the
     *         configured starting balance without a row being written for it
     */
    public double getBalance(UUID player) {
        Double balance = balances.get(player);
        return balance != null ? balance : config().startingBalance();
    }

    /** @return whether this player has ever been given an account row. */
    public boolean hasAccount(UUID player) {
        return balances.containsKey(player);
    }

    public int getAccountCount() {
        return balances.size();
    }

    // ------------------------------------------------------------------
    // Mutating
    // ------------------------------------------------------------------

    /** Credits an account. */
    public EconomyResult deposit(UUID player, double amount) {
        Objects.requireNonNull(player, "player");
        EconomySettings config = config();
        if (!config.enabled()) {
            return EconomyResult.DISABLED;
        }
        if (!isPositiveAmount(amount)) {
            return EconomyResult.INVALID_AMOUNT;
        }

        EconomyResult[] outcome = {EconomyResult.OK};
        balances.compute(player, (ignored, current) -> {
            double balance = current != null ? current : config.startingBalance();
            double updated = balance + amount;
            // Not finite: with no ceiling configured, two deposits near the largest
            // double reached Infinity, which MySQL refuses to store - the account
            // could never be saved again (found in the command review, 15/09/2026).
            if (!Double.isFinite(updated)
                    || (config.maximumBalance() > 0 && updated > config.maximumBalance())) {
                outcome[0] = EconomyResult.ABOVE_MAXIMUM;
                return current;
            }
            return updated;
        });
        if (outcome[0].isOk()) {
            dirty.add(player);
        }
        return outcome[0];
    }

    /** Debits an account, refusing to take more than it holds. */
    public EconomyResult withdraw(UUID player, double amount) {
        Objects.requireNonNull(player, "player");
        EconomySettings config = config();
        if (!config.enabled()) {
            return EconomyResult.DISABLED;
        }
        if (!isPositiveAmount(amount)) {
            return EconomyResult.INVALID_AMOUNT;
        }

        EconomyResult[] outcome = {EconomyResult.OK};
        balances.compute(player, (ignored, current) -> {
            double balance = current != null ? current : config.startingBalance();
            if (balance < amount) {
                outcome[0] = EconomyResult.INSUFFICIENT_FUNDS;
                return current;
            }
            return balance - amount;
        });
        if (outcome[0].isOk()) {
            dirty.add(player);
        }
        return outcome[0];
    }

    /** Staff override: sets a balance outright, ignoring the current one. */
    public EconomyResult set(UUID player, double amount) {
        Objects.requireNonNull(player, "player");
        EconomySettings config = config();
        if (!config.enabled()) {
            return EconomyResult.DISABLED;
        }
        if (!Double.isFinite(amount) || amount < 0) {
            return EconomyResult.INVALID_AMOUNT;
        }
        if (config.maximumBalance() > 0 && amount > config.maximumBalance()) {
            return EconomyResult.ABOVE_MAXIMUM;
        }
        balances.put(player, amount);
        dirty.add(player);
        return EconomyResult.OK;
    }

    /**
     * Moves money between two players.
     *
     * <p>The debit happens first: if the credit is then refused (a ceiling), the
     * debit is rolled back, so money is never destroyed. The pair is serialised on
     * a single lock picked from the lower of the two uuids, which is the same lock
     * for both directions - see the class javadoc for what that does and does not
     * guarantee.
     */
    public EconomyResult transfer(UUID from, UUID to, double amount) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        EconomySettings config = config();
        if (!config.enabled()) {
            return EconomyResult.DISABLED;
        }
        if (!config.pay().enabled()) {
            return EconomyResult.PAY_DISABLED;
        }
        if (from.equals(to)) {
            return EconomyResult.SAME_PLAYER;
        }
        if (!isPositiveAmount(amount)) {
            return EconomyResult.INVALID_AMOUNT;
        }
        if (amount < config.pay().minimumAmount()) {
            return EconomyResult.BELOW_MINIMUM;
        }

        // The lower uuid picks the lock, so A->B and B->A land on the same one.
        UUID first = from.compareTo(to) <= 0 ? from : to;
        synchronized (lockFor(first)) {
            EconomyResult debited = withdraw(from, amount);
            if (!debited.isOk()) {
                return debited;
            }
            EconomyResult credited = deposit(to, amount);
            if (!credited.isOk()) {
                // Put it back. Deliberately not through deposit(): that consults the
                // live settings, and a reload landing between the two halves (module
                // switched off, ceiling lowered) would refuse the refund and destroy
                // the money outright. A rollback restores a state that was legal a
                // moment ago, so it is not the rules' business.
                restore(from, amount);
                return credited;
            }
            return EconomyResult.OK;
        }
    }

    /**
     * Puts money back after a half-completed movement, unconditionally.
     *
     * <p>Never call this to grant money: it answers to no setting. It exists so a
     * refused second leg cannot leave the supply short.
     */
    public void restore(UUID player, double amount) {
        Objects.requireNonNull(player, "player");
        if (!isPositiveAmount(amount)) {
            return;
        }
        balances.merge(player, amount, Double::sum);
        dirty.add(player);
    }

    /**
     * @return the lock guarding this account
     *
     * <p>A fixed array of stripes rather than one lock per account: the set of
     * accounts is unbounded and a per-account lock map would grow with it. Two
     * unrelated accounts sharing a stripe are merely serialised, which costs
     * nothing at HCF transfer rates. Deliberately <em>not</em> interned strings:
     * those are shared with the whole JVM, so unrelated code holding the same
     * literal would contend with - or block - a money movement.
     */
    private Object lockFor(UUID player) {
        return stripes[Math.floorMod(player.hashCode(), stripes.length)];
    }

    private static Object[] newStripes(int count) {
        Object[] locks = new Object[count];
        for (int i = 0; i < count; i++) {
            locks[i] = new Object();
        }
        return locks;
    }

    private static boolean isPositiveAmount(double amount) {
        return Double.isFinite(amount) && amount > 0;
    }

    // ------------------------------------------------------------------
    // Formatting
    // ------------------------------------------------------------------

    /** @return the amount as players should see it, e.g. {@code $1,234.50} */
    public String format(double amount) {
        return config().currency().symbol() + String.format(Locale.ROOT, "%,.2f", amount);
    }

    /** @return the amount followed by the currency name, e.g. {@code 1 dollar} */
    public String formatWithName(double amount) {
        EconomySettings.CurrencyFormat currency = config().currency();
        String name = Math.abs(amount - 1.0) < 1e-9 ? currency.singular() : currency.plural();
        return String.format(Locale.ROOT, "%,.2f", amount) + ' ' + name;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    /** Loads every balance. Blocking - async task only. */
    public void loadAll() throws Exception {
        store.initSchema();
        balances.clear();
        dirty.clear();
        balances.putAll(store.loadAll());
    }

    /**
     * Writes changed balances. Blocking - async task only.
     *
     * @return the number of accounts written
     */
    public int flush() throws Exception {
        int written = 0;
        for (UUID player : Set.copyOf(dirty)) {
            dirty.remove(player);
            Double balance = balances.get(player);
            if (balance == null) {
                continue;
            }
            try {
                store.save(player, balance);
                written++;
            } catch (Exception e) {
                dirty.add(player);
                throw e;
            }
        }
        return written;
    }
}
