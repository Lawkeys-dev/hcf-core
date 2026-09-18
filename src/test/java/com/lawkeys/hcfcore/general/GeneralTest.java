package com.lawkeys.hcfcore.general;

import com.lawkeys.hcfcore.general.PrivateMessages.Delivery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rules behind the utility commands, without a server. */
class GeneralTest {

    private UUID alice;
    private UUID bob;

    @BeforeEach
    void setUp() {
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
    }

    @Nested
    class Messages {

        private PrivateMessages messages;

        @BeforeEach
        void setUp() {
            messages = new PrivateMessages();
        }

        @Test
        void aMessageGetsThroughByDefault() {
            assertSame(Delivery.OK, messages.canSend(alice, bob));
        }

        @Test
        void replyGoesBackToWhoeverSpokeLast() {
            messages.delivered(alice, bob);
            assertEquals(alice, messages.replyTarget(bob).orElseThrow());
            assertEquals(bob, messages.replyTarget(alice).orElseThrow(),
                    "the sender can reply to the reply");
        }

        @Test
        void withNobodyToReplyToThereIsNoTarget() {
            assertTrue(messages.replyTarget(alice).isEmpty());
        }

        @Test
        void aRecipientWithMessagesOffIsNotReachable() {
            messages.toggleMessages(bob);
            assertSame(Delivery.RECIPIENT_OFF, messages.canSend(alice, bob));
        }

        /**
         * Somebody with their own messages off cannot send either. A one-way private
         * conversation is worse than none: the other side replies into nothing.
         */
        @Test
        void messagesOffStopsYouSendingToo() {
            messages.toggleMessages(alice);
            assertSame(Delivery.SENDER_OFF, messages.canSend(alice, bob));
        }

        @Test
        void togglingMessagesReportsTheNewState() {
            assertFalse(messages.toggleMessages(alice), "first toggle turns them off");
            assertFalse(messages.hasMessagesOn(alice));
            assertTrue(messages.toggleMessages(alice), "second turns them back on");
            assertTrue(messages.hasMessagesOn(alice));
        }

        @Test
        void ignoringBlocksTheOtherDirectionOnly() {
            messages.toggleIgnore(bob, alice);
            assertSame(Delivery.IGNORED, messages.canSend(alice, bob));
            assertSame(Delivery.OK, messages.canSend(bob, alice),
                    "ignoring somebody does not stop you talking to them");
        }

        @Test
        void ignoringTogglesAndLists() {
            assertTrue(messages.toggleIgnore(alice, bob));
            assertTrue(messages.isIgnoring(alice, bob));
            assertEquals(java.util.Set.of(bob), messages.ignoredBy(alice));

            assertFalse(messages.toggleIgnore(alice, bob));
            assertFalse(messages.isIgnoring(alice, bob));
            assertTrue(messages.ignoredBy(alice).isEmpty());
        }

        @Test
        void leavingForgetsEverythingAboutAPlayer() {
            messages.toggleMessages(alice);
            messages.toggleIgnore(alice, bob);
            messages.delivered(bob, alice);

            messages.forget(alice);

            assertTrue(messages.hasMessagesOn(alice));
            assertFalse(messages.isIgnoring(alice, bob));
            assertTrue(messages.replyTarget(alice).isEmpty());
            assertTrue(messages.replyTarget(bob).isEmpty(),
                    "and nobody is left replying to a player who has gone");
        }
    }
}
