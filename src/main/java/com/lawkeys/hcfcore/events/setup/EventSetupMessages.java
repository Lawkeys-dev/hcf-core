package com.lawkeys.hcfcore.events.setup;

/** Language keys of the {@code /events} setup commands (see {@code lang/en.yml}, {@code events.setup}). */
public final class EventSetupMessages {

    public static final String USAGE = "events.setup.usage";
    public static final String IN_GAME_ONLY = "events.setup.in-game-only";
    public static final String WRITE_FAILED = "events.setup.write-failed";
    public static final String INVALID_ID = "events.setup.invalid-id";
    public static final String UNKNOWN_ID = "events.setup.unknown-id";
    public static final String RUNNING = "events.setup.running";
    public static final String NO_WAND = "events.setup.no-wand";

    // --- create / delete -------------------------------------------------
    public static final String UNKNOWN_TYPE = "events.setup.unknown-type";
    public static final String ALREADY_EXISTS = "events.setup.already-exists";
    public static final String NO_TEMPLATE = "events.setup.no-template";
    public static final String CREATED = "events.setup.created";
    public static final String AUTO_CLAIMED = "events.setup.auto-claimed";
    public static final String AUTO_CLAIM_REFUSED = "events.setup.auto-claim-refused";
    public static final String DELETED = "events.setup.deleted";
    public static final String DELETED_TERRITORY = "events.setup.deleted-territory";

    // --- territory: claim / unclaim ---------------------------------------
    public static final String TERRITORY_NOT_APPLICABLE = "events.setup.territory-not-applicable";
    public static final String TERRITORY_TEAM_CREATED = "events.setup.territory-team-created";
    public static final String TERRITORY_NAME_TAKEN = "events.setup.territory-name-taken";
    public static final String TERRITORY_TEAM_FAILED = "events.setup.territory-team-failed";
    public static final String TERRITORY_NONE = "events.setup.territory-none";
    public static final String UNCLAIMED = "events.setup.unclaimed";
    public static final String UNCLAIMED_ALL = "events.setup.unclaimed-all";

    // --- zones: setzone / delzone -----------------------------------------
    public static final String ZONE_NOT_APPLICABLE = "events.setup.zone-not-applicable";
    public static final String ZONE_NAME_NEEDED = "events.setup.zone-name-needed";
    public static final String ZONE_ONLY_ONE = "events.setup.zone-only-one";
    public static final String ZONE_INVALID_NAME = "events.setup.zone-invalid-name";
    public static final String ZONE_UNKNOWN = "events.setup.zone-unknown";
    public static final String ZONE_LAST = "events.setup.zone-last";
    public static final String ZONE_PREVIEW = "events.setup.zone-preview";
    public static final String ZONE_PREVIEW_OUTSIDE = "events.setup.zone-preview-outside";
    public static final String ZONE_DRAWN = "events.setup.zone-drawn";
    public static final String ZONE_OUTSIDE_TERRITORY = "events.setup.zone-outside-territory";
    public static final String ZONE_DELETED = "events.setup.zone-deleted";

    // --- setblock ---------------------------------------------------------
    public static final String BLOCK_NOT_APPLICABLE = "events.setup.block-not-applicable";
    public static final String NO_TARGET = "events.setup.no-target";
    public static final String BLOCK_OUTSIDE_ZONE = "events.setup.block-outside-zone";
    public static final String CORE_SET = "events.setup.core-set";
    public static final String COLUMN_SET = "events.setup.column-set";
    public static final String NOT_LOADED = "events.setup.not-loaded";

    // --- info: the checklist ------------------------------------------------
    public static final String INFO_HEADER = "events.setup.info.header";
    public static final String INFO_ZONE = "events.setup.info.zone";
    public static final String INFO_ZONE_NAMED = "events.setup.info.zone-named";
    public static final String INFO_ZONE_OUTSIDE = "events.setup.info.zone-outside";
    public static final String INFO_NO_ZONE = "events.setup.info.no-zone";
    public static final String INFO_WARZONE = "events.setup.info.warzone";
    public static final String INFO_NO_WARZONE = "events.setup.info.no-warzone";
    public static final String INFO_CORE = "events.setup.info.core";
    public static final String INFO_COLUMN = "events.setup.info.column";
    public static final String INFO_COLUMN_MISSING = "events.setup.info.column-missing";
    public static final String INFO_TERRITORY = "events.setup.info.territory";
    public static final String INFO_TERRITORY_EMPTY = "events.setup.info.territory-empty";
    public static final String INFO_TERRITORY_NONE = "events.setup.info.territory-none";
    public static final String INFO_SCHEDULE = "events.setup.info.schedule";
    public static final String INFO_SCHEDULE_NONE = "events.setup.info.schedule-none";
    public static final String INFO_RUNNING = "events.setup.info.running";
    public static final String INFO_IDLE = "events.setup.info.idle";
    public static final String INFO_NOT_LOADED = "events.setup.info.not-loaded";

    private EventSetupMessages() {
    }
}
