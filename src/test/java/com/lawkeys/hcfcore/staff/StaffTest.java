package com.lawkeys.hcfcore.staff;

import com.lawkeys.hcfcore.staff.StaffManager.VanishSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rule-level tests for the staff module: session state, the toolbar, and the
 * stash of held inventories - all without a server.
 */
class StaffTest {

    private StaffManager manager;
    private UUID alice;
    private UUID bob;

    @BeforeEach
    void setUp() {
        manager = new StaffManager();
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
    }

    @Nested
    class StaffMode {

        @Test
        void aPlayerEntersAndLeaves() {
            assertFalse(manager.isInStaffMode(alice));
            assertTrue(manager.enterStaffMode(alice));
            assertTrue(manager.isInStaffMode(alice));
            assertTrue(manager.leaveStaffMode(alice));
            assertFalse(manager.isInStaffMode(alice));
        }

        /**
         * The guard that stops a toolbar being stashed as though it were somebody's
         * real inventory - which would lose the real one for good.
         */
        @Test
        void enteringTwiceIsRefused() {
            assertTrue(manager.enterStaffMode(alice));
            assertFalse(manager.enterStaffMode(alice));
        }

        @Test
        void leavingWhenNotInItChangesNothing() {
            assertFalse(manager.leaveStaffMode(alice));
        }

        @Test
        void oneStaffMemberDoesNotPutAnotherInTheMode() {
            manager.enterStaffMode(alice);
            assertFalse(manager.isInStaffMode(bob));
            assertEquals(java.util.Set.of(alice), manager.staffModePlayers());
        }
    }

    @Nested
    class Vanish {

        @Test
        void hidingAndRevealing() {
            assertFalse(manager.isVanished(alice));
            assertTrue(manager.vanish(alice, VanishSource.MANUAL));
            assertTrue(manager.isVanished(alice));
            assertTrue(manager.reveal(alice));
            assertFalse(manager.isVanished(alice));
        }

        @Test
        void hidingTwiceIsRefused() {
            assertTrue(manager.vanish(alice, VanishSource.MANUAL));
            assertFalse(manager.vanish(alice, VanishSource.STAFF_MODE));
        }

        /**
         * The rule that makes the two sources worth distinguishing: somebody who
         * hid themselves first stays hidden when they leave staff mode. Collapsing
         * the sources into one flag reveals them, which is how a staff member ends
         * up standing visible in a raid they thought they were watching unseen.
         */
        @Test
        void aManualVanishSurvivesLeavingStaffMode() {
            manager.vanish(alice, VanishSource.MANUAL);
            manager.enterStaffMode(alice);
            // Staff mode tries to hide them too, and must not take ownership.
            assertFalse(manager.vanish(alice, VanishSource.STAFF_MODE));
            assertSame(VanishSource.MANUAL, manager.vanishSource(alice));

            manager.leaveStaffMode(alice);
            assertFalse(manager.revealIfStaffMode(alice));
            assertTrue(manager.isVanished(alice), "a vanish they asked for is theirs to keep");
        }

        @Test
        void aVanishStaffModeAddedIsRemovedWithIt() {
            manager.enterStaffMode(alice);
            assertTrue(manager.vanish(alice, VanishSource.STAFF_MODE));

            manager.leaveStaffMode(alice);
            assertTrue(manager.revealIfStaffMode(alice));
            assertFalse(manager.isVanished(alice));
        }

        @Test
        void revealingSomebodyWhoIsNotHiddenChangesNothing() {
            assertFalse(manager.reveal(alice));
            assertFalse(manager.revealIfStaffMode(alice));
            assertNull(manager.vanishSource(alice));
        }
    }

    @Nested
    class Toggles {

        @Test
        void staffChatTogglesBothWays() {
            assertFalse(manager.isStaffChatOn(alice));
            assertTrue(manager.toggleStaffChat(alice));
            assertTrue(manager.isStaffChatOn(alice));
            assertFalse(manager.toggleStaffChat(alice));
            assertFalse(manager.isStaffChatOn(alice));
        }

        @Test
        void staffBuildTogglesBothWays() {
            assertFalse(manager.hasStaffBuild(alice));
            assertTrue(manager.toggleStaffBuild(alice));
            assertTrue(manager.hasStaffBuild(alice));
            assertFalse(manager.toggleStaffBuild(alice));
            assertFalse(manager.hasStaffBuild(alice));
        }

        @Test
        void togglesAreIndependentBetweenPlayers() {
            manager.toggleStaffChat(alice);
            manager.toggleStaffBuild(alice);
            assertFalse(manager.isStaffChatOn(bob));
            assertFalse(manager.hasStaffBuild(bob));
        }

        @Test
        void leavingForgetsTheSessionButNotTheMode() {
            manager.enterStaffMode(alice);
            manager.vanish(alice, VanishSource.STAFF_MODE);
            manager.toggleStaffChat(alice);
            manager.toggleStaffBuild(alice);

            manager.forgetSession(alice);

            assertFalse(manager.isVanished(alice));
            assertFalse(manager.isStaffChatOn(alice));
            assertFalse(manager.hasStaffBuild(alice));
            // Deliberate: the server layer takes them out of the mode first, which is
            // what hands their inventory back. Clearing the flag here would strand it.
            assertTrue(manager.isInStaffMode(alice));
        }
    }

    @Nested
    class Toolbar {

        @Test
        void placeholdersAreFilledIn() {
            ToolbarItem item = new ToolbarItem(0, "BOOK", "Inspect", List.of(),
                    "invsee %player% for %staff%", true);
            assertEquals("invsee bob for alice", item.commandFor("alice", "bob"));
        }

        /**
         * A missing target leaves an empty argument rather than the word "null", so
         * the command it is aimed at answers with its own usage message.
         */
        @Test
        void aMissingTargetLeavesTheArgumentEmpty() {
            ToolbarItem item = new ToolbarItem(0, "BOOK", "Inspect", List.of(), "invsee %player%", true);
            assertEquals("invsee ", item.commandFor("alice", null));
        }

        @Test
        void anItemThatNeedsATargetCannotRunWithoutOne() {
            ToolbarItem targeted = new ToolbarItem(0, "BOOK", "Inspect", List.of(), "invsee %player%", true);
            assertFalse(targeted.isUsable(null));
            assertFalse(targeted.isUsable("  "));
            assertTrue(targeted.isUsable("bob"));
        }

        /**
         * The reverse is allowed on purpose: a tracker or a vanish toggle should not
         * stop working because somebody happened to be standing in the way.
         */
        @Test
        void anUntargetedItemStillRunsWhenClickedOnSomebody() {
            ToolbarItem plain = new ToolbarItem(0, "COMPASS", "Tracker", List.of(), "staff tpnearest", false);
            assertTrue(plain.isUsable(null));
            assertTrue(plain.isUsable("bob"));
        }

        @Test
        void theCommandNameIsItsFirstWord() {
            assertEquals("invsee",
                    new ToolbarItem(0, "BOOK", null, List.of(), "  InvSee %player%  ", true).commandName());
            assertEquals("vanish",
                    new ToolbarItem(0, "LIME_DYE", null, List.of(), "vanish", false).commandName());
        }

        @Test
        void twoItemsCannotShareASlot() {
            List<String> warnings = new ArrayList<>();
            StaffToolbar toolbar = StaffToolbar.of(List.of(
                    new ToolbarItem(3, "COMPASS", "first", List.of(), "staff tpnearest", false),
                    new ToolbarItem(3, "BOOK", "second", List.of(), "invsee %player%", true)),
                    warnings::add);

            assertEquals(1, toolbar.size());
            assertEquals("staff tpnearest", toolbar.at(3).orElseThrow().command(),
                    "the first one wins, being the one nearer the top of the file");
            assertEquals(1, warnings.size());
        }

        @Test
        void anImpossibleSlotIsDropped() {
            List<String> warnings = new ArrayList<>();
            StaffToolbar toolbar = StaffToolbar.of(List.of(
                    new ToolbarItem(-1, "COMPASS", null, List.of(), "staff tpnearest", false),
                    new ToolbarItem(36, "BOOK", null, List.of(), "invsee %player%", true),
                    new ToolbarItem(35, "BARRIER", null, List.of(), "staff", false)),
                    warnings::add);

            assertEquals(1, toolbar.size());
            assertTrue(toolbar.at(35).isPresent());
            assertEquals(2, warnings.size());
        }

        /** A slot bound to nothing would look like a tool and do nothing when used. */
        @Test
        void anItemWithNoCommandIsDropped() {
            List<String> warnings = new ArrayList<>();
            StaffToolbar toolbar = StaffToolbar.of(
                    List.of(new ToolbarItem(0, "COMPASS", null, List.of(), "   ", false)), warnings::add);

            assertTrue(toolbar.isEmpty());
            assertEquals(1, warnings.size());
        }

        @Test
        void theShippedToolbarIsUsableAsIs() {
            StaffToolbar toolbar = StaffSettings.defaults().staffMode().toolbar();
            assertFalse(toolbar.isEmpty());
            for (Map.Entry<Integer, ToolbarItem> entry : toolbar.items().entrySet()) {
                ToolbarItem item = entry.getValue();
                assertEquals(entry.getKey(), item.slot());
                assertTrue(item.hasValidSlot());
                assertFalse(item.command().isBlank());
            }
        }

        /**
         * The shipped toolbar may only bind commands this module actually registers.
         * A slot pointing at a command that does not exist looks like a tool and
         * answers "unknown command" - the ghost behaviour ARCHITECTURE.md section 2
         * forbids, and the reason Inspect and Freeze were left out until they existed.
         */
        @Test
        void everyShippedItemBindsACommandThisModuleProvides() {
            java.util.Set<String> provided = java.util.Set.of(
                    "staff", "vanish", "staffchat", "staffbuild", "broadcast", "clearchat",
                    "freeze", "invsee", "lastinv");
            for (ToolbarItem item : StaffSettings.defaults().staffMode().toolbar().items().values()) {
                assertTrue(provided.contains(item.commandName()),
                        "slot " + item.slot() + " binds '" + item.commandName()
                                + "', which this module does not register");
            }
        }

        /** A targeted item must carry the placeholder it needs, or it can never work. */
        @Test
        void everyTargetedShippedItemUsesThePlaceholder() {
            for (ToolbarItem item : StaffSettings.defaults().staffMode().toolbar().items().values()) {
                if (item.needsTarget()) {
                    assertTrue(item.command().contains(ToolbarItem.TARGET_PLACEHOLDER),
                            "slot " + item.slot() + " needs a target but never uses it");
                }
            }
        }

        @Test
        void aMaterialIsRequired() {
            assertThrows(NullPointerException.class,
                    () -> new ToolbarItem(0, null, null, List.of(), "staff", false));
        }
    }

    @Nested
    class Freezing {

        private FreezeManager freezes;

        @BeforeEach
        void setUp() {
            freezes = new FreezeManager();
        }

        @Test
        void aPlayerIsHeldAndLetGo() {
            assertFalse(freezes.isFrozen(alice));
            assertTrue(freezes.freeze(alice, "SomeStaff", 1_000L));
            assertTrue(freezes.isFrozen(alice));
            assertEquals("SomeStaff", freezes.get(alice).orElseThrow().frozenBy());
            assertEquals(1_000L, freezes.get(alice).orElseThrow().since());

            assertTrue(freezes.unfreeze(alice));
            assertFalse(freezes.isFrozen(alice));
        }

        /**
         * A second staff member must not quietly take over a check in progress: the
         * first one's name is the one on the announcement, and the one the player was
         * told to talk to.
         */
        @Test
        void aSecondStaffMemberCannotTakeOverAHold() {
            assertTrue(freezes.freeze(alice, "First", 1_000L));
            assertFalse(freezes.freeze(alice, "Second", 2_000L));
            assertEquals("First", freezes.get(alice).orElseThrow().frozenBy());
        }

        @Test
        void lettingGoOfSomebodyWhoIsNotHeldChangesNothing() {
            assertFalse(freezes.unfreeze(alice));
            assertTrue(freezes.get(alice).isEmpty());
        }

        @Test
        void holdsAreIndependent() {
            freezes.freeze(alice, "SomeStaff", 1_000L);
            assertFalse(freezes.isFrozen(bob));
            assertEquals(1, freezes.size());
            assertEquals(java.util.Set.of(alice), freezes.frozenPlayers());
        }

        @Test
        void everybodyIsLetGoAtOnce() {
            freezes.freeze(alice, "S", 1L);
            freezes.freeze(bob, "S", 1L);
            freezes.clearAll();
            assertEquals(0, freezes.size());
        }
    }

    @Nested
    class FrozenCommands {

        private StaffSettings.FreezeRules rules;

        @BeforeEach
        void setUp() {
            rules = StaffSettings.defaults().freeze();
        }

        @Test
        void anAllowedCommandGetsThrough() {
            assertTrue(rules.allows("msg SomeStaff hello"));
            assertTrue(rules.allows("r on my way"));
        }

        @Test
        void anythingElseIsRefused() {
            assertFalse(rules.allows("home"));
            assertFalse(rules.allows("tpa Friend"));
            assertFalse(rules.allows("team leave"));
        }

        /**
         * The one that would have been a hole: the server accepts a namespaced form,
         * so a check on the bare word alone lets "minecraft:tp" past a list that
         * refuses "tp". The part after the colon is what the server runs, so it is
         * what has to be judged.
         */
        @Test
        void aNamespacedCommandCannotSlipPast() {
            assertFalse(rules.allows("minecraft:tp Friend"));
            assertFalse(rules.allows("essentials:home"));
            // And the namespaced form of an allowed one still works.
            assertTrue(rules.allows("essentials:msg SomeStaff hello"));
        }

        @Test
        void theCheckIgnoresCase() {
            assertTrue(rules.allows("MSG SomeStaff hello"));
            assertTrue(rules.allows("Reply hi"));
        }

        @Test
        void aBareCommandWithNoArgumentsWorks() {
            assertTrue(rules.allows("msg"));
            assertFalse(rules.allows("spawn"));
        }

        /** An operator who empties the list means it: a frozen player runs nothing. */
        @Test
        void anEmptyListRefusesEverything() {
            StaffSettings.FreezeRules strict =
                    new StaffSettings.FreezeRules(true, List.of(), true, 10L);
            assertFalse(strict.allows("msg SomeStaff hello"));
            assertFalse(strict.allows("anything"));
        }
    }

    @Nested
    class Bans {

        private StaffBans bans;

        @BeforeEach
        void setUp() {
            bans = new StaffBans(StaffBanStore.NO_OP);
        }

        @Test
        void aBanIsRecordedAndLifted() {
            assertFalse(bans.isBanned(alice));
            assertTrue(bans.ban(alice, "logged out while frozen", "SomeStaff", 500L));
            assertTrue(bans.isBanned(alice));

            StaffBan ban = bans.get(alice).orElseThrow();
            assertEquals("logged out while frozen", ban.reason());
            assertEquals("SomeStaff", ban.bannedBy());
            assertEquals(500L, ban.bannedAt());

            assertTrue(bans.lift(alice));
            assertFalse(bans.isBanned(alice));
        }

        /**
         * The first reason is the one that explains why they are out. A later ban
         * overwriting it would lose the record of what actually happened.
         */
        @Test
        void aSecondBanDoesNotOverwriteTheFirstReason() {
            assertTrue(bans.ban(alice, "logged out while frozen", "First", 500L));
            assertFalse(bans.ban(alice, "something else", "Second", 900L));
            assertEquals("logged out while frozen", bans.get(alice).orElseThrow().reason());
        }

        @Test
        void liftingSomebodyWhoIsNotBannedChangesNothing() {
            assertFalse(bans.lift(alice));
        }

        @Test
        void bansAreCountedAndIndependent() {
            bans.ban(alice, "a", "S", 1L);
            bans.ban(bob, "b", "S", 2L);
            assertEquals(2, bans.size());
            bans.lift(alice);
            assertEquals(1, bans.size());
            assertTrue(bans.isBanned(bob));
        }
    }

    @Nested
    class DeathSnapshots {

        /** A caller keeping the array must not be able to change what was recorded. */
        @Test
        void theContentsAreCopiedBothWays() {
            byte[] items = {1, 2, 3};
            DeathSnapshot snapshot = new DeathSnapshot(alice, 1_000L, items);

            items[0] = 42;
            assertEquals(1, snapshot.contents()[0], "the snapshot kept its own copy");

            byte[] handedOut = snapshot.contents();
            handedOut[0] = 99;
            assertEquals(1, snapshot.contents()[0], "and hands out copies too");
        }

        @Test
        void aPlayerIsRequired() {
            assertThrows(NullPointerException.class,
                    () -> new DeathSnapshot(null, 1L, new byte[] {1}));
        }
    }

    @Nested
    class Stashes {

        private StaffStashes stashes;

        @BeforeEach
        void setUp() {
            stashes = new StaffStashes(StaffStashStore.NO_OP);
        }

        @Test
        void anInventoryIsHeldAndHandedBack() {
            byte[] items = {1, 2, 3};
            assertTrue(stashes.put(alice, items));
            assertTrue(stashes.has(alice));
            assertArrayEqualsBytes(items, stashes.get(alice).orElseThrow());

            stashes.remove(alice);
            assertFalse(stashes.has(alice));
            assertTrue(stashes.get(alice).isEmpty());
        }

        /** Overwriting would destroy the first inventory, so it is refused instead. */
        @Test
        void aSecondStashIsRefusedRatherThanOverwritingTheFirst() {
            byte[] first = {1, 2, 3};
            assertTrue(stashes.put(alice, first));
            assertFalse(stashes.put(alice, new byte[] {9, 9}));
            assertArrayEqualsBytes(first, stashes.get(alice).orElseThrow());
        }

        /** A caller keeping the array must not be able to change what is stored. */
        @Test
        void theStoredCopyIsItsOwn() {
            byte[] items = {1, 2, 3};
            stashes.put(alice, items);
            items[0] = 42;
            assertEquals(1, stashes.get(alice).orElseThrow()[0]);
        }

        @Test
        void stashesAreCountedAndIndependent() {
            stashes.put(alice, new byte[] {1});
            stashes.put(bob, new byte[] {2});
            assertEquals(2, stashes.size());
            stashes.remove(alice);
            assertEquals(1, stashes.size());
            assertTrue(stashes.has(bob));
        }

        /** Staff mode left and entered again while the first stash is being written. */
        @Test
        void aStashChangedDuringTheWriteIsWrittenAtTheNextFlush() throws Exception {
            Map<UUID, byte[]> rows = new java.util.HashMap<>();
            Runnable[] duringSave = {() -> { }};
            StaffStashes stored = new StaffStashes(new StaffStashStore() {
                @Override
                public void initSchema() {
                }

                @Override
                public Map<UUID, byte[]> loadAll() {
                    return Map.of();
                }

                @Override
                public void save(UUID playerId, byte[] contents) {
                    Runnable hook = duringSave[0];
                    duringSave[0] = () -> { };
                    hook.run();
                    rows.put(playerId, contents.clone());
                }

                @Override
                public void delete(UUID playerId) {
                    rows.remove(playerId);
                }
            });
            stored.put(alice, new byte[] {1});
            duringSave[0] = () -> {
                stored.remove(alice);
                stored.put(alice, new byte[] {2});
            };
            stored.flush();

            stored.flush();
            assertArrayEqualsBytes(new byte[] {2}, rows.get(alice));
        }

        private static void assertArrayEqualsBytes(byte[] expected, byte[] actual) {
            assertEquals(expected.length, actual.length);
            for (int i = 0; i < expected.length; i++) {
                assertEquals(expected[i], actual[i], "byte " + i);
            }
        }
    }

    @Nested
    class Invsee {

        /** The looked-at player leaving is what ends these sessions: their viewers are listed. */
        @Test
        void theViewersOfAPlayerAreFound() {
            InvseeSessions sessions = new InvseeSessions();
            UUID carol = UUID.randomUUID();
            sessions.open(alice, bob, true);
            sessions.open(carol, bob, false);
            sessions.open(bob, alice, false);

            assertEquals(java.util.Set.of(alice, carol), java.util.Set.copyOf(sessions.viewersOf(bob)));
            assertEquals(List.of(bob), sessions.viewersOf(alice));
            assertEquals(List.of(), sessions.viewersOf(carol));
        }
    }

    @Nested
    class BanPersistence {

        /** A ban lifted and set again, with a new reason, while the first one is being written. */
        @Test
        void aBanChangedDuringTheWriteIsWrittenAtTheNextFlush() throws Exception {
            Map<UUID, StaffBan> rows = new java.util.HashMap<>();
            Runnable[] duringSave = {() -> { }};
            StaffBans bans = new StaffBans(new StaffBanStore() {
                @Override
                public void initSchema() {
                }

                @Override
                public Map<UUID, StaffBan> loadAll() {
                    return Map.of();
                }

                @Override
                public void save(StaffBan ban) {
                    Runnable hook = duringSave[0];
                    duringSave[0] = () -> { };
                    hook.run();
                    rows.put(ban.playerId(), ban);
                }

                @Override
                public void delete(UUID playerId) {
                    rows.remove(playerId);
                }
            });
            bans.ban(alice, "first", "S", 1L);
            duringSave[0] = () -> {
                bans.lift(alice);
                bans.ban(alice, "second", "S", 2L);
            };
            bans.flush();

            bans.flush();
            assertEquals("second", rows.get(alice).reason());
        }

        /** Lifted during a save that then fails: the ban must not be written back. */
        @Test
        void aBanLiftedDuringAFailedWriteStaysLifted() throws Exception {
            Map<UUID, StaffBan> rows = new java.util.HashMap<>();
            Runnable[] duringSave = {() -> { }};
            StaffBans bans = new StaffBans(new StaffBanStore() {
                @Override
                public void initSchema() {
                }

                @Override
                public Map<UUID, StaffBan> loadAll() {
                    return Map.of();
                }

                @Override
                public void save(StaffBan ban) {
                    Runnable hook = duringSave[0];
                    duringSave[0] = () -> { };
                    hook.run();
                    rows.put(ban.playerId(), ban);
                }

                @Override
                public void delete(UUID playerId) {
                    rows.remove(playerId);
                }
            });
            bans.ban(alice, "first", "S", 1L);
            duringSave[0] = () -> {
                bans.lift(alice);
                throw new IllegalStateException("database unreachable");
            };
            assertThrows(IllegalStateException.class, bans::flush);

            bans.flush();
            assertFalse(rows.containsKey(alice), "the lifted ban came back");
        }
    }
}
