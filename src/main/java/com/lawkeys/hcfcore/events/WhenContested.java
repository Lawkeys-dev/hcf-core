package com.lawkeys.hcfcore.events;

import java.util.Locale;
import java.util.Optional;

/**
 * What a capture countdown does while the zone is contested - another team standing
 * in it with the holder ({@code when-contested} in {@code events.yml}, the owner's
 * request of 28/09/2026). Losing the zone outright is {@link ContestPolicy}'s.
 */
public enum WhenContested {

    /** The countdown freezes until one team holds the zone alone again. As it always was. */
    PAUSE,

    /**
     * The team that held the zone keeps counting down as long as it is still in it:
     * a challenger has to push it out, not merely step in.
     */
    CONTINUE;

    public static Optional<WhenContested> of(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
