package com.lawkeys.hcfcore.resourcenode;

/**
 * Every language key the resource node module can produce. Audited against
 * {@code lang/en.yml} by {@code TeamMessagesTest}.
 */
public final class ResourceNodeMessages {

    private ResourceNodeMessages() {
    }

    // Broadcast
    public static final String REFILLED = "resourcenode.broadcast.refilled";
    public static final String REFILL_SOON = "resourcenode.broadcast.soon";

    // /resourcenode, and the lines this module contributes to /events
    public static final String LIST_HEADER = "resourcenode.list.header";
    public static final String LIST_SCHEDULED = "resourcenode.list.scheduled";
    public static final String LIST_UNSCHEDULED = "resourcenode.list.unscheduled";
    public static final String LIST_REFILLING = "resourcenode.list.refilling";
    public static final String LIST_EMPTY = "resourcenode.list.empty";

    // Staff
    public static final String ADMIN_REFILLING = "resourcenode.admin.refilling";
    public static final String ADMIN_BUSY = "resourcenode.admin.busy";

    // Protection
    public static final String NO_BUILD = "resourcenode.protection.no-build";
    public static final String NO_BREAK = "resourcenode.protection.no-break";

    // Failures
    public static final String UNKNOWN_NODE = "resourcenode.error.unknown";
    public static final String DISABLED = "resourcenode.error.disabled";
    public static final String WORLD_MISSING = "resourcenode.error.world-missing";
    public static final String OUTSIDE_WORLD = "resourcenode.error.outside-world";
    // /resourcenode claim: a Mountain that is a server team's land
    public static final String CLAIM_NOT_CLAIMED = "resourcenode.claim.not-claimed";
    public static final String CLAIM_TEAM_CREATED = "resourcenode.claim.team-created";
    public static final String CLAIM_TEAM_TAKEN = "resourcenode.claim.team-taken";
    public static final String CLAIM_TEAM_FAILED = "resourcenode.claim.team-failed";
    public static final String CLAIM_NO_WAND = "resourcenode.claim.no-wand";
    public static final String CLAIM_IN_GAME = "resourcenode.claim.in-game";
}
