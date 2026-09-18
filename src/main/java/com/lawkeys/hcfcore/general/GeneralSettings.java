package com.lawkeys.hcfcore.general;

import java.util.Objects;

/**
 * Immutable snapshot of {@code general.yml} - the utility commands of FEATURES.md
 * section 10.
 *
 * @param logoutSeconds  how long {@code /logout} takes. A safe logout exists so a
 *                       player cannot escape a fight by closing their client; a
 *                       countdown that any damage or movement cancels is the
 *                       standard answer
 * @param renameMaxLength  longest name {@code /rename} accepts, so nobody writes a
 *                         book into an item name
 */
public record GeneralSettings(
        boolean enabled,
        SpawnRules spawn,
        long logoutSeconds,
        int renameMaxLength,
        boolean privateMessagesEnabled) {

    public GeneralSettings {
        Objects.requireNonNull(spawn, "spawn");
    }

    /**
     * Where {@code /spawn} sends a player.
     *
     * @param world     the world whose spawn point is used; empty means the player's
     *                  current world, which is what a multi-world server usually wants
     * @param warmupSeconds  a delay any damage cancels, so /spawn is not an escape
     *                       from a fight. Combat tag already blocks it, but a server
     *                       running without combat tags still wants this
     */
    public record SpawnRules(boolean enabled, String world, long warmupSeconds) {

        public SpawnRules {
            Objects.requireNonNull(world, "world");
        }
    }

    /** Built-in fallback, mirroring {@code resources/general.yml}. */
    public static GeneralSettings defaults() {
        return new GeneralSettings(true, new SpawnRules(true, "", 5L), 30L, 32, true);
    }
}
