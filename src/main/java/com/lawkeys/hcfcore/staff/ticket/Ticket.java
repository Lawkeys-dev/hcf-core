package com.lawkeys.hcfcore.staff.ticket;

import java.util.Objects;
import java.util.UUID;

/**
 * One report or request.
 *
 * <p>Both are the same thing with a different word on the front - somebody wants
 * staff attention, here is why - so they share a table and a queue. A report names
 * somebody; a request does not. That is the only difference, and making them two
 * systems would double the commands, the storage and the review for no gain.
 *
 * @param id         row id, assigned by the store
 * @param type       report or request
 * @param openedBy   who raised it
 * @param openedName their name at the time, so a closed ticket still reads properly
 *                   years later
 * @param target     the player reported, or {@code null} for a request
 * @param targetName likewise
 * @param message    what they wrote
 * @param handledBy  the staff member who took it, or {@code null}
 * @param status     where it is in the queue
 */
public record Ticket(long id, TicketType type, UUID openedBy, String openedName,
                     UUID target, String targetName, String message,
                     long openedAt, TicketStatus status, String handledBy) {

    /**
     * The width of the {@code message} column. Longer text is cut here rather than
     * refused by the database: MySQL in strict mode rejects an over-long value, and
     * a row that can never be written would be retried at every save for ever.
     */
    public static final int MAX_MESSAGE = 512;
    /** The width of the name columns. */
    public static final int MAX_NAME = 32;

    public Ticket {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(openedBy, "openedBy");
        Objects.requireNonNull(status, "status");
        openedName = cut(openedName == null ? "" : openedName, MAX_NAME);
        targetName = targetName == null ? null : cut(targetName, MAX_NAME);
        handledBy = handledBy == null ? null : cut(handledBy, MAX_NAME);
        message = cut(message == null ? "" : message, MAX_MESSAGE);
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    public Ticket withStatus(TicketStatus newStatus, String staffName) {
        return new Ticket(id, type, openedBy, openedName, target, targetName, message,
                openedAt, newStatus, staffName);
    }

    public boolean isOpen() {
        return status == TicketStatus.OPEN;
    }
}
