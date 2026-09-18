package com.lawkeys.hcfcore.team;

import java.util.Locale;
import java.util.Optional;

/**
 * Chat channel a player is currently talking in (see FEATURES.md section 1,
 * "Chat de team").
 *
 * <p>The channel is per-player runtime state held by {@code TeamManager}; the
 * listener that actually routes chat messages is intentionally not part of this
 * change - see the note in {@code team/listener/package-info.java}.
 */
public enum ChatChannel {

    PUBLIC,
    TEAM,
    ALLY;

    public static Optional<ChatChannel> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.trim().toUpperCase(Locale.ROOT);
        for (ChatChannel channel : values()) {
            if (channel.name().equals(normalized)) {
                return Optional.of(channel);
            }
        }
        return Optional.empty();
    }
}
