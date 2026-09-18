package com.lawkeys.hcfcore.integration.luckperms;

import com.lawkeys.hcfcore.chat.ChatDecorations;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * Hooks LuckPerms if it is installed, for the chat prefix and suffix.
 *
 * <p><strong>Permissions need no integration at all.</strong> Every permission
 * check in this plugin goes through Bukkit's {@code Permissible}, which LuckPerms
 * already implements - so ranks and staff nodes work with no code here. The one
 * thing the API is needed for is reading a prefix and suffix to put in a chat line,
 * which Bukkit has no notion of.
 *
 * <p>This class deliberately names no LuckPerms type: the only class that does is
 * {@link LuckPermsChatMeta}, and it is not loaded until the check below has passed.
 */
public final class LuckPermsIntegration {

    private LuckPermsIntegration() {
    }

    /**
     * @return LuckPerms-backed decorations if it is installed and its API answers,
     *         otherwise {@link ChatDecorations#NONE}
     */
    public static ChatDecorations decorations(Plugin plugin) {
        // Enabled, not merely loaded: a LuckPerms whose own start failed has no API.
        if (!plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            plugin.getLogger().info("LuckPerms is not installed or not running; chat shows no prefix or suffix.");
            return ChatDecorations.NONE;
        }
        try {
            ChatDecorations decorations = LuckPermsChatMeta.create();
            plugin.getLogger().info("Hooked LuckPerms for chat prefixes and suffixes.");
            return decorations;
        } catch (RuntimeException | LinkageError e) {
            // IllegalStateException if the API is not loaded yet, LinkageError if the
            // classes are absent despite the plugin being listed. Either way the chat
            // format still works, it simply shows no prefix.
            plugin.getLogger().log(Level.WARNING,
                    "LuckPerms is installed but its API could not be used; "
                            + "chat will show no prefix or suffix.", e);
            return ChatDecorations.NONE;
        }
    }
}
