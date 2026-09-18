package com.lawkeys.hcfcore.phase;

/**
 * Every language key SOTW and EOTW can produce.
 *
 * <p>Same contract as {@code TeamMessages}: no player-facing text in Java, and
 * {@code TeamMessagesTest} fails the build if a key used here is missing from
 * {@code lang/en.yml}.
 */
public final class PhaseMessages {

    private PhaseMessages() {
    }

    // SOTW, broadcast
    public static final String SOTW_STARTED = "phase.sotw.started";
    public static final String SOTW_PROGRESS = "phase.sotw.progress";
    public static final String SOTW_ENDED = "phase.sotw.ended";
    public static final String SOTW_STOPPED = "phase.sotw.stopped";

    // SOTW, to one player
    public static final String SOTW_YOU_ARE_PROTECTED = "phase.sotw.you-are-protected";
    public static final String SOTW_VICTIM_PROTECTED = "phase.sotw.victim-protected";
    public static final String SOTW_ENABLED = "phase.sotw.enabled";
    public static final String SOTW_ALREADY_ENABLED = "phase.sotw.already-enabled";
    public static final String SOTW_NOT_ACTIVE = "phase.sotw.not-active";
    public static final String SOTW_ALREADY_ACTIVE = "phase.sotw.already-active";
    public static final String SOTW_DURING_EOTW = "phase.sotw.during-eotw";
    public static final String SOTW_STATUS_ACTIVE = "phase.sotw.status.active";
    public static final String SOTW_STATUS_PROTECTED = "phase.sotw.status.protected";
    public static final String SOTW_STATUS_ENABLED = "phase.sotw.status.enabled";
    public static final String SOTW_STATUS_SCHEDULED = "phase.sotw.status.scheduled";
    public static final String SOTW_STATUS_INACTIVE = "phase.sotw.status.inactive";

    // EOTW
    public static final String EOTW_STARTED = "phase.eotw.started";
    public static final String EOTW_STOPPED = "phase.eotw.stopped";
    public static final String EOTW_NO_CLAIMS = "phase.eotw.no-claims";
    public static final String EOTW_ALREADY_ACTIVE = "phase.eotw.already-active";
    public static final String EOTW_NOT_ACTIVE = "phase.eotw.not-active";
    public static final String EOTW_DURING_SOTW = "phase.eotw.during-sotw";
    public static final String EOTW_STATUS_ACTIVE = "phase.eotw.status.active";
    public static final String EOTW_STATUS_SCHEDULED = "phase.eotw.status.scheduled";
    public static final String EOTW_STATUS_INACTIVE = "phase.eotw.status.inactive";

    // Shared
    public static final String INVALID_DURATION = "phase.error.invalid-duration";

    // Purge
    public static final String PURGE_STARTED = "phase.purge.started";
    public static final String PURGE_ENDED = "phase.purge.ended";
    public static final String PURGE_STOPPED = "phase.purge.stopped";
    public static final String PURGE_ALREADY_ACTIVE = "phase.purge.already-active";
    public static final String PURGE_NOT_ACTIVE = "phase.purge.not-active";
    public static final String PURGE_DURING_SOTW = "phase.purge.during-sotw";
    public static final String PURGE_DURING_EOTW = "phase.purge.during-eotw";
    public static final String PURGE_STATUS_ACTIVE = "phase.purge.status.active";
    public static final String PURGE_STATUS_SCHEDULED = "phase.purge.status.scheduled";
    public static final String PURGE_STATUS_INACTIVE = "phase.purge.status.inactive";
    public static final String AGENDA_PURGE_ACTIVE = "phase.agenda.purge-active";
    public static final String AGENDA_PURGE_SCHEDULED = "phase.agenda.purge-scheduled";

    // /events agenda
    public static final String AGENDA_SOTW_ACTIVE = "phase.agenda.sotw-active";
    public static final String AGENDA_SOTW_SCHEDULED = "phase.agenda.sotw-scheduled";
    public static final String AGENDA_EOTW_ACTIVE = "phase.agenda.eotw-active";
    public static final String AGENDA_EOTW_SCHEDULED = "phase.agenda.eotw-scheduled";
}
