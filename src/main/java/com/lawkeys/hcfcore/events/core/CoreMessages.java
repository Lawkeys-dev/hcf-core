package com.lawkeys.hcfcore.events.core;

/** Language keys used by DTC and Last Break (see {@code lang/en.yml}). */
public final class CoreMessages {

    /**
     * DTC and Last Break share one run slot ({@code CoreEventManager}): only
     * one of the two runs at a time. Used instead of the per-kind
     * {@code *_ALREADY_RUNNING} keys below whenever a start is refused, so the
     * message always names the event actually running rather than assuming it
     * shares the kind of the one that was refused (a Last Break running does
     * not make "one DTC at a time" true).
     */
    public static final String CORE_ALREADY_RUNNING = "events.core.already-running";

    // --- DTC -----------------------------------------------------------
    public static final String DTC_STARTED = "events.dtc.started";
    public static final String DTC_STARTED_PER_TEAM = "events.dtc.started-per-team";
    public static final String DTC_MILESTONE = "events.dtc.milestone";
    public static final String DTC_MILESTONE_PER_TEAM = "events.dtc.milestone-per-team";
    public static final String DTC_WON = "events.dtc.won";
    public static final String DTC_EXPIRED = "events.dtc.expired";
    public static final String DTC_STOPPED = "events.dtc.stopped";
    public static final String DTC_COOLDOWN = "events.dtc.cooldown";
    public static final String DTC_NO_TEAM = "events.dtc.no-team";
    public static final String DTC_LIST_ACTIVE = "events.dtc.list-active";
    public static final String DTC_LIST_ACTIVE_PER_TEAM = "events.dtc.list-active-per-team";
    public static final String DTC_LIST_ACTIVE_NOBODY = "events.dtc.list-active-nobody";

    // --- Last Break ------------------------------------------------------
    public static final String LAST_BREAK_STARTED = "events.last-break.started";
    public static final String LAST_BREAK_MILESTONE = "events.last-break.milestone";
    public static final String LAST_BREAK_WON = "events.last-break.won";
    public static final String LAST_BREAK_EXPIRED = "events.last-break.expired";
    public static final String LAST_BREAK_STOPPED = "events.last-break.stopped";
    public static final String LAST_BREAK_COOLDOWN = "events.last-break.cooldown";
    public static final String LAST_BREAK_NO_TEAM = "events.last-break.no-team";
    public static final String LAST_BREAK_LIST_ACTIVE = "events.last-break.list-active";
    public static final String LAST_BREAK_LIST_ACTIVE_NOBODY = "events.last-break.list-active-nobody";

    // --- Setup commands (/events create|setzone|setcore|delete) ----------
    public static final String SETUP_CREATED = "events.setup.created";
    public static final String SETUP_ALREADY_EXISTS = "events.setup.already-exists";
    public static final String SETUP_UNKNOWN_TYPE = "events.setup.unknown-type";
    public static final String SETUP_UNKNOWN_ID = "events.setup.unknown-id";
    public static final String SETUP_NO_WAND = "events.setup.no-wand";
    public static final String SETUP_ZONE_PREVIEW = "events.setup.zone-preview";
    public static final String SETUP_ZONE_DRAWN = "events.setup.zone-drawn";
    public static final String SETUP_INVALID_ID = "events.setup.invalid-id";
    public static final String SETUP_RUNNING = "events.setup.running";
    public static final String SETUP_ZONE_SET = "events.setup.zone-set";
    public static final String SETUP_CORE_SET = "events.setup.core-set";
    public static final String SETUP_CORE_OUTSIDE_ZONE = "events.setup.core-outside-zone";
    public static final String SETUP_CORE_NOT_APPLICABLE = "events.setup.core-not-applicable";
    public static final String SETUP_NO_TARGET_BLOCK = "events.setup.no-target-block";
    public static final String SETUP_DELETED = "events.setup.deleted";
    public static final String SETUP_NOT_IN_CLAIM = "events.setup.not-in-claim";
    public static final String SETUP_USAGE = "events.setup.usage";
    public static final String SETUP_WRITE_FAILED = "events.setup.write-failed";

    // --- Scoreboard --------------------------------------------------------
    public static final String SCOREBOARD_DTC_LINE = "ui.scoreboard.dtc-line";
    public static final String SCOREBOARD_DTC_LINE_PER_TEAM = "ui.scoreboard.dtc-line-per-team";
    public static final String SCOREBOARD_DTC_TEAM_LINE = "ui.scoreboard.dtc-team-line";
    public static final String SCOREBOARD_LAST_BREAK_LINE = "ui.scoreboard.last-break-line";

    // --- Holograms (events.yml, zone-holograms) -----------------------------
    public static final String HOLOGRAM_CORE_TITLE = "events.hologram.core-title";
    public static final String HOLOGRAM_CORE_HEALTH = "events.hologram.core-health";
    public static final String HOLOGRAM_CORE_LEADER = "events.hologram.core-leader";
    public static final String HOLOGRAM_CORE_NOBODY = "events.hologram.core-nobody";

    private CoreMessages() {
    }
}
