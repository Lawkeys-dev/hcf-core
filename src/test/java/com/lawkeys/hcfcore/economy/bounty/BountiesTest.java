package com.lawkeys.hcfcore.economy.bounty;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BountiesTest {

    private final UUID steve = UUID.randomUUID();
    private final UUID alex = UUID.randomUUID();

    @Test
    void bountiesOnOnePlayerAddUp() {
        Bounties bounties = new Bounties(BountyStore.NO_OP);
        assertEquals(100.0, bounties.add(steve, 100.0));
        assertEquals(350.0, bounties.add(steve, 250.0));
        assertEquals(350.0, bounties.get(steve));
    }

    @Test
    void aClaimTakesTheWholeBountyOnce() {
        Bounties bounties = new Bounties(BountyStore.NO_OP);
        bounties.add(steve, 500.0);
        assertEquals(500.0, bounties.claim(steve));
        assertEquals(0.0, bounties.claim(steve));
        assertEquals(0.0, bounties.get(steve));
    }

    @Test
    void theListIsTheLargestFirst() {
        Bounties bounties = new Bounties(BountyStore.NO_OP);
        bounties.add(steve, 100.0);
        bounties.add(alex, 900.0);
        assertEquals(alex, bounties.top(10).get(0).target());
        assertEquals(1, bounties.top(1).size());
    }

    @Test
    void nothingOrNonsenseAddsNothing() {
        Bounties bounties = new Bounties(BountyStore.NO_OP);
        assertEquals(0.0, bounties.add(steve, -5.0));
        assertEquals(0.0, bounties.add(steve, Double.NaN));
    }

    @Test
    void aClaimIsSavedAsARemovedRow() throws Exception {
        Map<UUID, Double> saved = new HashMap<>();
        BountyStore store = new BountyStore() {
            @Override
            public void initSchema() {
            }

            @Override
            public Map<UUID, Double> loadAll() {
                return Map.of();
            }

            @Override
            public void save(UUID target, double amount) {
                saved.put(target, amount);
            }
        };
        Bounties bounties = new Bounties(store);
        bounties.add(steve, 200.0);
        assertEquals(1, bounties.flush());
        assertEquals(200.0, saved.get(steve));
        bounties.claim(steve);
        bounties.flush();
        assertEquals(0.0, saved.get(steve), "0 is the store's cue to delete the row");
        assertEquals(0, bounties.flush(), "nothing left to write");
    }
}
