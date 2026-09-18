package com.lawkeys.hcfcore.staff.ticket;

/** Where a ticket is in the queue. */
public enum TicketStatus {
    /** Nobody has picked it up. */
    OPEN,
    /** A staff member is dealing with it, so nobody else needs to. */
    CLAIMED,
    /** Dealt with. */
    CLOSED
}
