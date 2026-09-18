package com.lawkeys.hcfcore.events;

/**
 * What happens to the countdown when the holding team stops holding the zone -
 * knocked out, killed, logged off, or joined by an enemy.
 *
 * <p>Configurable rather than hardcoded because HCF servers genuinely disagree
 * on this, and it changes how a KOTH plays more than any other single value.
 */
public enum ContestPolicy {

    /**
     * Losing the zone puts the countdown back to full. The classic, brutal
     * behaviour: holding a KOTH for nine of ten minutes and being pushed off it
     * means starting over.
     *
     * <p><strong>Being contested is not losing the zone.</strong> An enemy walking
     * in freezes the countdown; it costs the holder nothing until they are actually
     * pushed out or a different team takes sole control. Wiping on contact would
     * make a KOTH ungriefable-proof in the wrong direction - one player touching
     * the zone for a single tick would erase nine minutes of work.
     */
    RESET,

    /**
     * The countdown freezes where it is and resumes when a single team holds the
     * zone again - whoever that is. Kinder, and makes long captures winnable on
     * a busy server.
     */
    PAUSE
}
