package com.lawkeys.hcfcore.kit;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable snapshot of {@code kits.yml}. Partner items are the ability module's ({@code abilities.yml}).
 *
 * @param layoutEditor players may arrange a kit's items with {@code /kit layout}
 *
 * @param signLine   first line of a refill sign, without brackets. A player clicking
 *                   a sign reading {@code [Kit]} with a kit id under it is handed
 *                   that kit - the kitmap standard, and what FEATURES.md section 7
 *                   asks for
 * @param signCooldownSeconds  anti-spam wait on a refill sign, separate from the
 *                             kit's own cooldown: a kitmap kit usually has none, and
 *                             the sign still should not be clickable sixty times a
 *                             second
 */
public record KitSettings(boolean enabled, boolean signsEnabled, String signLine,
                          long signCooldownSeconds, boolean clearBeforeGiving,
                          boolean layoutEditor) {

    public KitSettings {
        Objects.requireNonNull(signLine, "signLine");
    }

    /** Built-in fallback, mirroring {@code resources/kits.yml}. */
    public static KitSettings defaults() {
        return new KitSettings(true, true, "Kit", 3L, true, true);
    }
}
