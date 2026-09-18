package com.lawkeys.hcfcore.kit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kits and their cooldowns, without a server. */
class KitManagerTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private KitManager kits;
    private UUID alice;

    private static Kit kit(String id, long cooldownSeconds) {
        return new Kit(id, null, null, cooldownSeconds, new byte[] {1, 2, 3});
    }

    @BeforeEach
    void setUp() {
        kits = new KitManager(KitStore.NO_OP, now::get);
        alice = UUID.randomUUID();
    }

    @Test
    void aKitIsSavedFoundAndDeleted() {
        kits.save(kit("archer", 60));
        assertTrue(kits.get("archer").isPresent());
        assertTrue(kits.get("ARCHER").isPresent(), "ids are case-insensitive");
        assertEquals(1, kits.size());

        assertTrue(kits.delete("Archer"));
        assertTrue(kits.get("archer").isEmpty());
        assertFalse(kits.delete("archer"));
    }

    @Test
    void kitsAreListedInAStableOrder() {
        kits.save(kit("rogue", 0));
        kits.save(kit("archer", 0));
        kits.save(kit("bard", 0));
        assertEquals(List.of("archer", "bard", "rogue"), kits.all().stream().map(Kit::id).toList());
    }

    @Test
    void aCooldownCountsDownAndThenClears() {
        Kit archer = kit("archer", 60);
        kits.save(archer);
        assertFalse(kits.isOnCooldown(alice, "archer"));

        kits.markUsed(alice, archer);
        assertEquals(60L, kits.remainingCooldown(alice, "archer"));

        now.addAndGet(30_000L);
        assertEquals(30L, kits.remainingCooldown(alice, "archer"));

        now.addAndGet(30_000L);
        assertFalse(kits.isOnCooldown(alice, "archer"));
    }

    /**
     * A cooldown is an instant, not a countdown, so it keeps running while the
     * server is off. Otherwise a daily kit is worth nothing on a server that
     * restarts nightly.
     */
    @Test
    void aCooldownKeepsRunningWhileTheServerIsDown() {
        Kit daily = kit("daily", 86_400);
        kits.save(daily);
        kits.markUsed(alice, daily);

        now.addAndGet(86_400_000L);
        assertFalse(kits.isOnCooldown(alice, "daily"));
    }

    @Test
    void aKitWithNoCooldownRecordsNothing() {
        Kit free = kit("free", 0);
        kits.save(free);
        kits.markUsed(alice, free);
        assertFalse(kits.isOnCooldown(alice, "free"));
    }

    /** A recreated kit of the same name must not inherit somebody's old wait. */
    @Test
    void deletingAKitDropsItsCooldowns() {
        Kit archer = kit("archer", 600);
        kits.save(archer);
        kits.markUsed(alice, archer);
        assertTrue(kits.isOnCooldown(alice, "archer"));

        kits.delete("archer");
        kits.save(kit("archer", 600));
        assertFalse(kits.isOnCooldown(alice, "archer"));
    }

    @Test
    void staffCanClearOneWaitOrAllOfThem() {
        kits.save(kit("a", 600));
        kits.save(kit("b", 600));
        kits.markUsed(alice, kit("a", 600));
        kits.markUsed(alice, kit("b", 600));

        kits.clearCooldowns(alice, "a");
        assertFalse(kits.isOnCooldown(alice, "a"));
        assertTrue(kits.isOnCooldown(alice, "b"));

        kits.clearCooldowns(alice, null);
        assertFalse(kits.isOnCooldown(alice, "b"));
    }

    /** Without this a server carries a row for every kit every player ever took. */
    @Test
    void expiredWaitsArePurged() {
        kits.save(kit("a", 60));
        kits.markUsed(alice, kit("a", 60));
        assertEquals(0, kits.purgeExpired(), "nothing has expired yet");

        now.addAndGet(61_000L);
        assertEquals(1, kits.purgeExpired());
        assertEquals(0, kits.purgeExpired(), "and there is nothing left to purge");
    }

    @Test
    void permissionsGateAKit() {
        Kit gated = new Kit("vip", null, "hcfcore.kit.vip", 0, new byte[] {1});
        assertFalse(gated.isAllowed(node -> false));
        assertTrue(gated.isAllowed("hcfcore.kit.vip"::equals));

        Kit open = kit("open", 0);
        assertTrue(open.isAllowed(node -> false), "a kit with no permission is open to all");
    }

    @Test
    void theStoredContentsAreTheKitsOwnCopy() {
        byte[] items = {1, 2, 3};
        Kit archer = new Kit("archer", null, null, 0, items);
        items[0] = 42;
        assertEquals(1, archer.contents()[0]);

        byte[] handedOut = archer.contents();
        handedOut[0] = 99;
        assertEquals(1, archer.contents()[0]);
    }

    // ------------------------------------------------------------------
    // Layouts
    // ------------------------------------------------------------------

    private static final KitLayout SWORD_TO_SLOT_TWO = KitLayout.of(Map.of(0, 2));

    @Test
    void aLayoutIsKeptPerPlayerAndPerKit() {
        kits.save(kit("archer", 0));
        kits.setLayout(alice, "Archer", SWORD_TO_SLOT_TWO);
        assertEquals(SWORD_TO_SLOT_TWO, kits.layout(alice, "archer"));
        assertEquals(KitLayout.NONE, kits.layout(alice, "bard"));
        assertEquals(KitLayout.NONE, kits.layout(UUID.randomUUID(), "archer"));

        kits.setLayout(alice, "archer", KitLayout.NONE);
        assertEquals(KitLayout.NONE, kits.layout(alice, "archer"), "an empty layout forgets the choice");
    }

    @Test
    void deletingAKitDropsItsLayouts() {
        kits.save(kit("archer", 0));
        kits.setLayout(alice, "archer", SWORD_TO_SLOT_TWO);
        kits.delete("archer");
        kits.save(kit("archer", 0));
        assertEquals(KitLayout.NONE, kits.layout(alice, "archer"), "a new kit of the same name starts clean");
    }

    /** A layout moves slots; a kit re-created with other items makes the old moves meaningless. */
    @Test
    void recreatingAKitWithOtherItemsDropsItsLayoutsButNotOtherwise() {
        kits.save(kit("archer", 0));
        kits.setLayout(alice, "archer", SWORD_TO_SLOT_TWO);

        kits.save(new Kit("archer", "Archer!", null, 30, new byte[] {1, 2, 3}));
        assertEquals(SWORD_TO_SLOT_TWO, kits.layout(alice, "archer"), "a new name or cooldown moves no item");

        kits.save(new Kit("archer", null, null, 0, new byte[] {9, 9}));
        assertEquals(KitLayout.NONE, kits.layout(alice, "archer"));
    }

    @Test
    void layoutsAreWrittenAndLoadedBack() throws Exception {
        RecordingStore store = new RecordingStore();
        KitManager first = new KitManager(store, now::get);
        first.save(kit("archer", 0));
        first.setLayout(alice, "archer", SWORD_TO_SLOT_TWO);
        first.flush();
        assertEquals(Map.of("archer", "0:2"), store.layouts.get(alice));

        KitManager second = new KitManager(store, now::get);
        second.loadAll();
        assertEquals(SWORD_TO_SLOT_TWO, second.layout(alice, "archer"));
    }

    /** The dirty-flag rule: a failed write leaves the row marked, so the next flush writes it. */
    @Test
    void aFailedWriteIsRetriedAtTheNextFlush() throws Exception {
        RecordingStore store = new RecordingStore();
        KitManager manager = new KitManager(store, now::get);
        manager.save(kit("archer", 0));
        manager.setLayout(alice, "archer", SWORD_TO_SLOT_TWO);
        store.failLayouts = true;
        assertThrows(IllegalStateException.class, manager::flush);

        store.failLayouts = false;
        manager.flush();
        assertEquals(Map.of("archer", "0:2"), store.layouts.get(alice));
    }

    /**
     * The other half of the rule: the mark is cleared before the row is read, so a
     * change landing while the write is in flight marks it again. Cleared after the
     * write, the mark would take that change with it and it would never be saved.
     */
    @Test
    void aChangeMadeWhileTheWriteIsInFlightIsWrittenNextTime() throws Exception {
        RecordingStore store = new RecordingStore();
        KitManager manager = new KitManager(store, now::get);
        manager.save(kit("archer", 0));
        manager.setLayout(alice, "archer", SWORD_TO_SLOT_TWO);
        KitLayout changed = KitLayout.of(Map.of(0, 5));
        store.duringLayoutWrite = () -> manager.setLayout(alice, "archer", changed);
        manager.flush();
        assertEquals(Map.of("archer", "0:2"), store.layouts.get(alice), "the write in flight saw the old one");

        store.duringLayoutWrite = () -> { };
        manager.flush();
        assertEquals(Map.of("archer", "0:5"), store.layouts.get(alice));
    }

    /** A {@link KitStore} in memory, whose layout writes can be made to fail or to race. */
    private static final class RecordingStore implements KitStore {
        final Map<String, Kit> kits = new HashMap<>();
        final Map<UUID, Map<String, String>> layouts = new HashMap<>();
        boolean failLayouts;
        Runnable duringLayoutWrite = () -> { };

        @Override
        public void initSchema() {
        }

        @Override
        public Collection<Kit> loadKits() {
            return List.copyOf(kits.values());
        }

        @Override
        public Map<UUID, Map<String, Long>> loadCooldowns() {
            return Map.of();
        }

        @Override
        public void saveKit(Kit kit) {
            kits.put(kit.id(), kit);
        }

        @Override
        public void deleteKit(String id) {
            kits.remove(id);
        }

        @Override
        public void saveCooldowns(UUID playerId, Map<String, Long> cooldowns) {
        }

        @Override
        public Map<UUID, Map<String, String>> loadLayouts() {
            return Map.copyOf(layouts);
        }

        @Override
        public void saveLayouts(UUID playerId, Map<String, String> theirs) {
            if (failLayouts) {
                throw new IllegalStateException("database down");
            }
            layouts.put(playerId, Map.copyOf(theirs));
            duringLayoutWrite.run();
        }
    }
}
