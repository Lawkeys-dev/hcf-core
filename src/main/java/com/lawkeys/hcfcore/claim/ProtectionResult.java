package com.lawkeys.hcfcore.claim;

/**
 * Outcome of a build/break/interact permission check on a chunk.
 *
 * <p>Modelled as an enum rather than an object because this is evaluated on
 * every block event on the server - the hot path allocates nothing.
 *
 * <p>Note what these values encode: ownership never appears as "free land". A
 * raided chunk is {@link #ALLOWED_RAID}, still owned by its team, exactly as
 * FEATURES.md section 3 requires.
 */
public enum ProtectionResult {

    /** Unclaimed land, or the actor's own territory. */
    ALLOWED(null),
    /** Another team's territory, currently raidable - the pillage window. */
    ALLOWED_RAID(null),
    /** An ally's territory, with ally building disabled in config. */
    DENIED_ALLY(ClaimMessages.PROTECTION_ALLY),
    /** A server-owned team's territory (spawn, warzone...). */
    DENIED_SYSTEM(ClaimMessages.PROTECTION_SYSTEM),
    /** Unclaimed warzone land, with building there switched off in config. */
    DENIED_WARZONE(ClaimMessages.PROTECTION_WARZONE),
    /** Another team's territory, protected. */
    DENIED_CLAIMED(ClaimMessages.PROTECTION_CLAIMED);

    private final String messageKey;

    ProtectionResult(String messageKey) {
        this.messageKey = messageKey;
    }

    public boolean isAllowed() {
        return this == ALLOWED || this == ALLOWED_RAID;
    }

    /** @return the language key explaining the refusal, or {@code null} when allowed. */
    public String getMessageKey() {
        return messageKey;
    }
}
