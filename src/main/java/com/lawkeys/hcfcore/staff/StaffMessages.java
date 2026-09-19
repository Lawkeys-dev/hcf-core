package com.lawkeys.hcfcore.staff;

/** Every language key the staff module can produce. Audited against {@code lang/en.yml}. */
public final class StaffMessages {

    private StaffMessages() {
    }

    // Staff mode
    public static final String MODE_ENTERED = "staff.mode.entered";
    public static final String MODE_LEFT = "staff.mode.left";
    public static final String MODE_ANNOUNCE_ENTERED = "staff.mode.announce-entered";
    public static final String MODE_ANNOUNCE_LEFT = "staff.mode.announce-left";
    public static final String MODE_ALREADY_HELD = "staff.mode.already-held";
    public static final String MODE_STASH_RESTORED = "staff.mode.stash-restored";
    public static final String MODE_RESTORE_FAILED = "staff.mode.restore-failed";
    public static final String MODE_ITEM_NEEDS_TARGET = "staff.mode.item-needs-target";
    public static final String MODE_LIST_HEADER = "staff.mode.list-header";
    public static final String MODE_LIST_ENTRY = "staff.mode.list-entry";
    public static final String MODE_LIST_EMPTY = "staff.mode.list-empty";

    // Vanish
    //
    // "enabled"/"disabled" rather than the obvious "on"/"off": YAML 1.1 resolves a
    // bare on, off, yes and no as booleans, so a key written `on:` parses as `true:`
    // and every lookup for "staff.vanish.on" misses. SnakeYAML, which Bukkit's
    // YamlConfiguration uses, does this. Found by the key audit below, not in
    // testing - a missing key only shows as a missing message at runtime.
    public static final String VANISH_ON = "staff.vanish.enabled";
    public static final String VANISH_OFF = "staff.vanish.disabled";

    // Staff chat
    public static final String STAFF_CHAT_ON = "staff.chat.enabled";
    public static final String STAFF_CHAT_OFF = "staff.chat.disabled";

    // Staff build
    public static final String BUILD_ON = "staff.build.enabled";
    public static final String BUILD_ON_NO_BYPASS = "staff.build.enabled-without-bypass";
    public static final String BUILD_OFF = "staff.build.disabled";

    // Teleport family
    public static final String TELEPORTED_TO = "staff.teleport.to-player";
    public static final String TELEPORTED_HERE = "staff.teleport.player-here";
    public static final String TELEPORTED_ALL = "staff.teleport.all";
    public static final String TELEPORTED_LOCATION = "staff.teleport.location";
    public static final String NOBODY_ELSE_ONLINE = "staff.teleport.nobody-else";
    public static final String LOCATION_TOLD = "staff.teleport.location-told";
    public static final String NEAR_HEADER = "staff.near.header";
    public static final String NEAR_ENTRY = "staff.near.entry";
    public static final String NEAR_EMPTY = "staff.near.empty";

    // Dimension rosters
    public static final String DIMENSION_HEADER = "staff.dimension.header";
    public static final String DIMENSION_ENTRY = "staff.dimension.entry";
    public static final String DIMENSION_EMPTY = "staff.dimension.empty";
    public static final String DIMENSION_MISSING = "staff.dimension.missing";

    // Broadcast and clear chat
    public static final String CHAT_CLEARED = "staff.chat-cleared";

    // Freeze
    public static final String FREEZE_APPLIED = "staff.freeze.applied";
    public static final String FREEZE_RELEASED = "staff.freeze.released";
    public static final String FREEZE_TARGET_FROZEN = "staff.freeze.target-frozen";
    public static final String FREEZE_TARGET_RELEASED = "staff.freeze.target-released";
    public static final String FREEZE_REMINDER = "staff.freeze.reminder";
    public static final String FREEZE_BLOCKED = "staff.freeze.blocked";
    public static final String FREEZE_ALREADY_HELD = "staff.freeze.already-held";
    public static final String FREEZE_LIST_HEADER = "staff.freeze.list-header";
    public static final String FREEZE_LIST_ENTRY = "staff.freeze.list-entry";
    public static final String FREEZE_LIST_EMPTY = "staff.freeze.list-empty";
    public static final String FREEZE_LOGOUT_ANNOUNCE = "staff.freeze.logout-announce";

    // Moderation bans
    public static final String BAN_LOGIN_DENIED = "staff.ban.login-denied";
    public static final String BAN_LIFTED = "staff.ban.lifted";
    public static final String BAN_NOT_BANNED = "staff.ban.not-banned";
    public static final String BAN_REASON_FROZEN_LOGOUT = "staff.ban.reason-frozen-logout";

    // Invsee
    public static final String INVSEE_OPENED = "staff.invsee.opened";
    public static final String INVSEE_READ_ONLY = "staff.invsee.read-only";
    public static final String INVSEE_EDITABLE = "staff.invsee.editable";
    public static final String INVSEE_ARMOUR = "staff.invsee.armour";
    public static final String INVSEE_SELF = "staff.invsee.self";

    // Last inventory
    public static final String LASTINV_HEADER = "staff.lastinv.header";
    public static final String LASTINV_ENTRY = "staff.lastinv.entry";
    public static final String LASTINV_EMPTY = "staff.lastinv.empty";
    public static final String LASTINV_OPENED = "staff.lastinv.opened";
    public static final String LASTINV_VIEW_TITLE = "staff.lastinv.view-title";
    public static final String LASTINV_OUT_OF_RANGE = "staff.lastinv.out-of-range";
    public static final String LASTINV_UNREADABLE = "staff.lastinv.unreadable";
    public static final String LASTINV_LOOKUP_FAILED = "staff.lastinv.lookup-failed";

    // Reports and requests
    public static final String TICKET_OPENED = "staff.ticket.opened";
    public static final String TICKET_ANNOUNCE = "staff.ticket.announce";
    public static final String TICKET_ANNOUNCE_REQUEST = "staff.ticket.announce-request";
    public static final String TICKET_TOO_SOON = "staff.ticket.too-soon";
    public static final String TICKET_SELF = "staff.ticket.self";
    public static final String TICKET_LIST_HEADER = "staff.ticket.list-header";
    public static final String TICKET_LIST_ENTRY = "staff.ticket.list-entry";
    public static final String TICKET_LIST_ENTRY_REQUEST = "staff.ticket.list-entry-request";
    public static final String TICKET_LIST_EMPTY = "staff.ticket.list-empty";
    public static final String TICKET_CLAIMED = "staff.ticket.claimed";
    public static final String TICKET_ALREADY_CLAIMED = "staff.ticket.already-claimed";
    public static final String TICKET_CLOSED = "staff.ticket.closed";
    public static final String TICKET_UNKNOWN = "staff.ticket.unknown";
    public static final String TICKET_NOBODY_TO_VISIT = "staff.ticket.nobody-to-visit";
    public static final String TICKET_NOTIFY_CLAIMED = "staff.ticket.notify-claimed";
    public static final String TICKET_NOTIFY_CLOSED = "staff.ticket.notify-closed";
    public static final String TICKET_MENU_TITLE = "staff.ticket.menu.title";
    public static final String TICKET_MENU_REPORT_NAME = "staff.ticket.menu.report-name";
    public static final String TICKET_MENU_REQUEST_NAME = "staff.ticket.menu.request-name";
    public static final String TICKET_MENU_FROM = "staff.ticket.menu.from";
    public static final String TICKET_MENU_ABOUT = "staff.ticket.menu.about";
    public static final String TICKET_MENU_STATUS = "staff.ticket.menu.status";
    public static final String TICKET_MENU_AGE = "staff.ticket.menu.age";
    public static final String TICKET_MENU_MESSAGE_LINE = "staff.ticket.menu.message-line";
    public static final String TICKET_MENU_LEFT_CLICK = "staff.ticket.menu.left-click";
    public static final String TICKET_MENU_RIGHT_CLICK = "staff.ticket.menu.right-click";

    // Strikes
    public static final String STRIKE_ISSUED = "staff.strike.issued";
    public static final String STRIKE_ANNOUNCE = "staff.strike.announce";
    public static final String STRIKE_BROADCAST = "staff.strike.broadcast";
    public static final String STRIKE_POINTS_LOST = "staff.strike.points-lost";
    public static final String STRIKE_DISBANDED = "staff.strike.disbanded";
    public static final String STRIKE_DISBAND_REFUSED = "staff.strike.disband-refused";
    public static final String STRIKE_SUBJECT = "staff.strike.subject";
    public static final String STRIKE_NO_TEAM = "staff.strike.no-team";
    public static final String STRIKE_SYSTEM_TEAM = "staff.strike.system-team";
    public static final String STRIKE_LIST_HEADER = "staff.strike.list-header";
    public static final String STRIKE_LIST_ENTRY = "staff.strike.list-entry";
    public static final String STRIKE_NONE = "staff.strike.none";
    public static final String STRIKE_PARDONED = "staff.strike.pardoned";
    public static final String STRIKE_UNKNOWN = "staff.strike.unknown";
    public static final String STRIKE_UNKNOWN_TEAM = "staff.strike.unknown-team";
    public static final String STRIKE_DETAILS = "staff.strike.details";
    public static final String STRIKE_OFFENCES_HEADER = "staff.strike.offences-header";
    public static final String STRIKE_OFFENCES_ENTRY = "staff.strike.offences-entry";
    public static final String STRIKE_UNKNOWN_OFFENCE = "staff.strike.unknown-offence";

    // Command layer
    public static final String PLAYER_NOT_FOUND = "staff.error.player-not-found";
    public static final String INVALID_NUMBER = "staff.error.invalid-number";
    public static final String DISABLED = "staff.error.disabled";
    public static final String USAGE = "staff.error.usage";
}
