package com.lawkeys.hcfcore.economy.bounty;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The price on each player's head (the project owner's request, 23/09/2026). Money
 * placed is money already taken from whoever placed it, so a bounty is saved like a
 * balance - the same dirty-flag order - and survives a restart.
 *
 * <p>Pure Java apart from the store: unit-tested with {@link BountyStore#NO_OP}.
 */
public final class Bounties {

    /** One bounty, for the list. */
    public record Entry(UUID target, double amount) {
    }

    private final BountyStore store;
    private final Map<UUID, Double> amounts = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    public Bounties(BountyStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /** @return the bounty on this player, {@code 0} for none */
    public double get(UUID target) {
        return amounts.getOrDefault(target, 0.0);
    }

    /** @return the new total on the target's head */
    public double add(UUID target, double amount) {
        if (!(amount > 0) || !Double.isFinite(amount)) {
            return get(target);
        }
        double total = amounts.merge(target, amount, Double::sum);
        dirty.add(target);
        return total;
    }

    /** @return what was on the target's head - now nothing - or {@code 0} */
    public double claim(UUID target) {
        Double claimed = amounts.remove(target);
        if (claimed == null) {
            return 0.0;
        }
        dirty.add(target);
        return claimed;
    }

    /** Puts a claimed bounty back, when paying it out failed. */
    public void restore(UUID target, double amount) {
        add(target, amount);
    }

    /** @return the largest bounties first, at most {@code limit} */
    public List<Entry> top(int limit) {
        return amounts.entrySet().stream()
                .map(entry -> new Entry(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingDouble(Entry::amount).reversed())
                .limit(Math.max(0, limit))
                .toList();
    }

    public void loadAll() throws Exception {
        store.initSchema();
        amounts.clear();
        dirty.clear();
        store.loadAll().forEach((target, amount) -> {
            if (amount > 0) {
                amounts.put(target, amount);
            }
        });
    }

    public int flush() throws Exception {
        int written = 0;
        for (UUID target : Set.copyOf(dirty)) {
            dirty.remove(target);
            try {
                store.save(target, get(target));
            } catch (Exception e) {
                dirty.add(target);
                throw e;
            }
            written++;
        }
        return written;
    }
}
