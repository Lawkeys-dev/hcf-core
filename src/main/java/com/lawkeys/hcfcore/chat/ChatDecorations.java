package com.lawkeys.hcfcore.chat;

import org.bukkit.entity.Player;

/**
 * A player's chat prefix and suffix, as a permissions plugin sees them.
 *
 * <p>Declared here rather than in the integration so that the chat format can be
 * written, and read, without LuckPerms being on the classpath at all. The default
 * gives neither, which is exactly what a server without a permissions plugin
 * should show.
 */
public interface ChatDecorations {

    /** @return the prefix, or an empty string - never {@code null} */
    String prefix(Player player);

    /** @return the suffix, or an empty string - never {@code null} */
    String suffix(Player player);

    /**
     * @return the weight of the player's primary group, heaviest first - the rank
     *         order of the classic tab list; 0 without a permissions plugin
     */
    default int weight(Player player) {
        return 0;
    }

    ChatDecorations NONE = new ChatDecorations() {
        @Override
        public String prefix(Player player) {
            return "";
        }

        @Override
        public String suffix(Player player) {
            return "";
        }
    };
}
