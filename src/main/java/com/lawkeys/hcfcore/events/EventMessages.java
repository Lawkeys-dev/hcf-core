package com.lawkeys.hcfcore.events;

/** Every language key the events module can produce. Audited against {@code lang/en.yml}. */
public final class EventMessages {

    private EventMessages() {
    }

    // Broadcast during a run
    public static final String STARTED = "events.broadcast.started";
    public static final String CAPTURE_BEGAN = "events.broadcast.capture-began";
    public static final String CONTESTED = "events.broadcast.contested";
    public static final String CONTROL_LOST = "events.broadcast.control-lost";
    public static final String PROGRESS = "events.broadcast.progress";
    public static final String CAPTURED = "events.broadcast.captured";
    public static final String EXPIRED = "events.broadcast.expired";
    public static final String STOPPED = "events.broadcast.stopped";

    // /events
    public static final String LIST_HEADER = "events.list.header";
    public static final String LIST_ACTIVE = "events.list.active";
    public static final String LIST_CONTESTED = "events.list.contested";
    public static final String LIST_FREE = "events.list.free";
    public static final String LIST_SCHEDULED = "events.list.scheduled";
    public static final String LIST_UNSCHEDULED = "events.list.unscheduled";
    public static final String LIST_EMPTY = "events.list.empty";

    // Staff
    public static final String ADMIN_STARTED = "events.admin.started";
    public static final String ADMIN_STOPPED = "events.admin.stopped";

    // Failures
    public static final String UNKNOWN_EVENT = "events.error.unknown";
    public static final String ALREADY_ACTIVE = "events.error.already-active";
    public static final String NOT_ACTIVE = "events.error.not-active";
    public static final String DISABLED = "events.error.disabled";

    // Above an event zone (hologram/)
    public static final String HOLOGRAM_TITLE = "events.hologram.title";
    public static final String HOLOGRAM_CONQUEST_TITLE = "events.hologram.conquest-title";
    public static final String HOLOGRAM_TIME_LEFT = "events.hologram.time-left";
    public static final String HOLOGRAM_CONTESTED = "events.hologram.contested";
    public static final String HOLOGRAM_HELD_BY = "events.hologram.held-by";
    public static final String HOLOGRAM_FREE = "events.hologram.free";
    public static final String HOLOGRAM_NEXT = "events.hologram.next";
    public static final String HOLOGRAM_UNSCHEDULED = "events.hologram.unscheduled";
}
