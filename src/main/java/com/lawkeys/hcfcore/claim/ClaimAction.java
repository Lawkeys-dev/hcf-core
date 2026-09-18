package com.lawkeys.hcfcore.claim;

import java.util.Locale;
import java.util.Optional;

/**
 * Claim operations whose minimum required role is configurable in
 * {@code claims.yml}, mirroring what {@code TeamAction} does for team
 * operations (ARCHITECTURE.md section 2).
 */
public enum ClaimAction {

    CLAIM,
    UNCLAIM,
    SET_HOME,
    /** {@code /team lockclaim}, during SOTW only. */
    LOCK_CLAIM;

    public String configKey() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Optional<ClaimAction> fromConfigKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String normalized = key.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (ClaimAction action : values()) {
            if (action.name().equals(normalized)) {
                return Optional.of(action);
            }
        }
        return Optional.empty();
    }
}
