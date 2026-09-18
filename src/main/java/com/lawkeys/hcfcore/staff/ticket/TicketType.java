package com.lawkeys.hcfcore.staff.ticket;

/** Whether a ticket names somebody. */
public enum TicketType {
    /** {@code /report <player> <reason>} - about another player. */
    REPORT,
    /** {@code /request <message>} - about anything else. */
    REQUEST
}
