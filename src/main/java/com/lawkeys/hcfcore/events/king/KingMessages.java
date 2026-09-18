package com.lawkeys.hcfcore.events.king;

/**
 * Every language key Kill the King can produce.
 *
 * <p>Same contract as {@code TeamMessages}: no player-facing text in Java, and
 * {@code TeamMessagesTest} fails the build if a key used here is missing from
 * {@code lang/en.yml}.
 */
public final class KingMessages {

    private KingMessages() {
    }

    // Broadcast
    public static final String CROWNED = "events.king.crowned";
    public static final String COORDINATES = "events.king.coordinates";
    public static final String PROGRESS = "events.king.progress";
    public static final String SURVIVED = "events.king.survived";
    public static final String KILLED = "events.king.killed";
    public static final String DIED = "events.king.died";
    public static final String FLED = "events.king.fled";
    public static final String STOPPED = "events.king.stopped";

    // Called off before anyone was crowned
    public static final String CANCELLED_NOT_ENOUGH_PLAYERS = "events.king.cancelled.not-enough-players";
    public static final String CANCELLED_NO_WARZONE = "events.king.cancelled.no-warzone";
    public static final String CANCELLED_NO_SPOT = "events.king.cancelled.no-spot";
    public static final String CANCELLED_TELEPORT_FAILED = "events.king.cancelled.teleport-failed";

    // To the King alone
    public static final String YOU_ARE_KING = "events.king.you-are-king";
    public static final String LEFT_ZONE = "events.king.left-zone";
    public static final String RETURNED = "events.king.returned";
    public static final String SAFE_ZONE_REFUSED = "events.king.safe-zone-refused";
    public static final String NOT_WHILE_KING = "events.king.not-while-king";
    public static final String ITEMS_RETURNED = "events.king.items-returned";

    // /events
    public static final String LIST_ACTIVE = "events.king.list.active";
    public static final String LIST_CROWNING = "events.king.list.crowning";
    public static final String ALREADY_RUNNING = "events.king.already-running";
}
