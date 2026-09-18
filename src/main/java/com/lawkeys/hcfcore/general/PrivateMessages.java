package com.lawkeys.hcfcore.general;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is talking to whom privately, who has turned messages off, and who is
 * ignoring whom.
 *
 * <p>Memory only: an ignore list that survived a restart would be a stored social
 * graph, which FEATURES.md does not ask for and which nobody would think to clear.
 * A session's worth is what {@code /ignore} is for - the alternative is
 * {@code /togglepm}, which is also a session.
 *
 * <p>Pure Java, so the rules below are testable.
 */
public final class PrivateMessages {

    private final Map<UUID, UUID> lastSender = new ConcurrentHashMap<>();
    private final Set<UUID> messagesOff = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<UUID>> ignoring = new ConcurrentHashMap<>();

    /** Why a message could not be delivered, or {@link Delivery#OK}. */
    public enum Delivery {
        OK,
        /** The recipient has private messages switched off. */
        RECIPIENT_OFF,
        /** The recipient is ignoring the sender. */
        IGNORED,
        /** The sender has their own messages off, so they cannot send either. */
        SENDER_OFF
    }

    /**
     * @return whether this message may be delivered
     *
     * <p>Somebody with their own messages off cannot send them: a one-way private
     * conversation is worse than none, since the other side replies into nothing.
     */
    public Delivery canSend(UUID from, UUID to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (messagesOff.contains(from)) {
            return Delivery.SENDER_OFF;
        }
        if (messagesOff.contains(to)) {
            return Delivery.RECIPIENT_OFF;
        }
        if (isIgnoring(to, from)) {
            return Delivery.IGNORED;
        }
        return Delivery.OK;
    }

    /** Remembers who to reply to, on both sides of the conversation. */
    public void delivered(UUID from, UUID to) {
        lastSender.put(to, from);
        lastSender.put(from, to);
    }

    /** @return who this player would reply to */
    public Optional<UUID> replyTarget(UUID playerId) {
        return Optional.ofNullable(lastSender.get(playerId));
    }

    /** @return whether messages are now on */
    public boolean toggleMessages(UUID playerId) {
        if (messagesOff.remove(playerId)) {
            return true;
        }
        messagesOff.add(playerId);
        return false;
    }

    public boolean hasMessagesOn(UUID playerId) {
        return !messagesOff.contains(playerId);
    }

    /** Sets the switch outright - how a choice saved in an earlier session is put back. */
    public void setMessagesOn(UUID playerId, boolean on) {
        if (on) {
            messagesOff.remove(playerId);
        } else {
            messagesOff.add(playerId);
        }
    }

    /**
     * @return whether {@code target} is now ignored
     *
     * <p>Ignoring yourself is refused by the caller rather than here, so that the
     * message explaining why can be written once in the command.
     */
    public boolean toggleIgnore(UUID playerId, UUID target) {
        Set<UUID> list = ignoring.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet());
        if (list.remove(target)) {
            return false;
        }
        list.add(target);
        return true;
    }

    public boolean isIgnoring(UUID playerId, UUID target) {
        Set<UUID> list = ignoring.get(playerId);
        return list != null && list.contains(target);
    }

    public Set<UUID> ignoredBy(UUID playerId) {
        return Set.copyOf(ignoring.getOrDefault(playerId, Set.of()));
    }

    /** Forgets everything about a player who has left. */
    public void forget(UUID playerId) {
        lastSender.remove(playerId);
        lastSender.values().remove(playerId);
        messagesOff.remove(playerId);
        ignoring.remove(playerId);
    }
}
