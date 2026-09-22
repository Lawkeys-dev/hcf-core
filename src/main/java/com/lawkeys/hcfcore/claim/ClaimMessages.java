package com.lawkeys.hcfcore.claim;

/**
 * Every language key the claim module can produce.
 *
 * <p>Same contract as {@code TeamMessages}: no player-facing text in Java, and
 * {@code ClaimMessagesTest} fails the build if a key used here is missing from
 * {@code lang/en.yml}.
 */
public final class ClaimMessages {

    private ClaimMessages() {
    }

    // Claiming
    public static final String CLAIM_SUCCESS = "claim.claim.success";
    public static final String CLAIM_ALREADY_YOURS = "claim.claim.already-yours";
    public static final String CLAIM_ALREADY_OWNED = "claim.claim.already-owned";
    public static final String CLAIM_LIMIT_REACHED = "claim.claim.limit-reached";
    public static final String CLAIM_NOT_CONNECTED = "claim.claim.not-connected";
    public static final String CLAIM_TOO_CLOSE = "claim.claim.too-close";
    public static final String CLAIM_WORLD_DISABLED = "claim.claim.world-disabled";
    // /team stuck
    public static final String STUCK_MOVED = "claim.stuck.moved";
    public static final String STUCK_NOWHERE = "claim.stuck.nowhere";
    public static final String STUCK_NOT_STUCK = "claim.stuck.not-stuck";
    public static final String STUCK_WARMUP = "claim.stuck.warmup";
    public static final String STUCK_CANCELLED = "claim.stuck.cancelled";

    public static final String CLAIM_DISABLED = "claim.claim.disabled";
    public static final String CLAIM_SYSTEM_TEAM = "claim.claim.system-team";
    public static final String CLAIM_NOTHING_SELECTED = "claim.claim.nothing-selected";
    public static final String CLAIM_TOO_SMALL = "claim.claim.too-small";
    public static final String CLAIM_TOO_BIG = "claim.claim.too-big";
    public static final String CLAIM_TOO_MANY_CLAIMS = "claim.claim.too-many-claims";
    public static final String CLAIM_CANNOT_AFFORD = "claim.claim.cannot-afford";
    public static final String CLAIM_RESERVED_REGION = "claim.claim.reserved-region";
    public static final String CLAIM_WARZONE = "claim.claim.warzone";

    // Staff claiming for any team - server land, typically
    public static final String ADMIN_UNCLAIMED = "claim.admin.unclaimed";
    public static final String FORCECLAIM_USAGE = "claim.admin.forceclaim-usage";
    public static final String FORCEUNCLAIM_CONSOLE = "claim.admin.forceunclaim-console";
    public static final String ADMIN_UNCLAIMED_ALL = "claim.admin.unclaimed-all";

    // Unclaiming
    public static final String UNCLAIM_SUCCESS = "claim.unclaim.success";
    public static final String UNCLAIM_NOT_YOURS = "claim.unclaim.not-yours";
    public static final String UNCLAIM_NOT_CLAIMED = "claim.unclaim.not-claimed";
    public static final String UNCLAIM_ALL_SUCCESS = "claim.unclaim.all-success";
    public static final String UNCLAIM_NOTHING = "claim.unclaim.nothing";
    public static final String UNCLAIM_WOULD_DISCONNECT = "claim.unclaim.would-disconnect";

    // Homes (HQ / secondary base)
    public static final String HOME_SET = "claim.home.set";
    public static final String HOME_NOT_SET = "claim.home.not-set";
    public static final String HOME_NOT_IN_TERRITORY = "claim.home.not-in-territory";
    public static final String HOME_DISABLED = "claim.home.disabled";
    public static final String HOME_WORLD_UNLOADED = "claim.home.world-unloaded";
    public static final String HOME_TELEPORTED = "claim.home.teleported";
    public static final String LOCK_ON = "claim.lock.locked";
    public static final String LOCK_OFF = "claim.lock.unlocked";
    public static final String LOCK_NOT_NOW = "claim.lock.not-now";
    public static final String LOCK_ENTRY_DENIED = "claim.lock.entry-denied";
    public static final String LOCK_EXPELLED = "claim.lock.expelled";
    public static final String LOCK_BROADCAST_LOCKED = "claim.lock.broadcast-locked";
    public static final String LOCK_BROADCAST_UNLOCKED = "claim.lock.broadcast-unlocked";
    public static final String HOME_WARMUP = "claim.home.warmup";
    public static final String HOME_CANCELLED = "claim.home.cancelled";
    public static final String WARMUP_BUSY = "claim.warmup-busy";

    // Protection
    public static final String PROTECTION_CLAIMED = "claim.protection.claimed";
    public static final String PROTECTION_ALLY = "claim.protection.ally";
    public static final String PROTECTION_SYSTEM = "claim.protection.system";
    public static final String PROTECTION_WARZONE = "claim.protection.warzone";

    // Territory feedback
    public static final String ENTER_TERRITORY = "claim.territory.enter";
    public static final String LEAVE_TERRITORY = "claim.territory.leave";
    public static final String INFO_HEADER = "claim.info.header";
    public static final String INFO_OWNER = "claim.info.owner";
    public static final String INFO_WILDERNESS = "claim.info.wilderness";
    public static final String INFO_WARZONE = "claim.info.warzone";
    public static final String INFO_RAIDABLE = "claim.info.raidable";
    public static final String INFO_PROTECTED = "claim.info.protected";
    public static final String INFO_UNENFORCED = "claim.info.unenforced";
    public static final String INFO_COUNT = "claim.info.count";
    public static final String INFO_CLAIM = "claim.info.claim";

    // Claiming wand
    public static final String WAND_GIVEN = "claim.wand.given";
    public static final String WAND_INVENTORY_FULL = "claim.wand.inventory-full";
    public static final String WAND_FIRST = "claim.wand.first-corner";
    public static final String WAND_SECOND = "claim.wand.second-corner";
    public static final String WAND_SELECTION = "claim.wand.selection";
    public static final String WAND_CONFIRM_HINT = "claim.wand.confirm-hint";
    public static final String WAND_INCOMPLETE = "claim.wand.incomplete";
    public static final String WAND_CANCELLED = "claim.wand.cancelled";
    public static final String WAND_EXPIRED = "claim.wand.expired";
    public static final String WAND_FREE = "claim.wand.free";
    public static final String WAND_COST = "claim.wand.cost";
    public static final String WAND_COST_SHORT = "claim.wand.cost-short";

    // Map
    public static final String MAP_HEADER = "claim.map.header";
    public static final String MAP_LEGEND = "claim.map.legend";
    public static final String MAP_ROW = "claim.map.row";

    // Shared
    public static final String NOT_IN_TEAM = "claim.error.not-in-team";
    public static final String INSUFFICIENT_ROLE = "claim.error.insufficient-role";
}
