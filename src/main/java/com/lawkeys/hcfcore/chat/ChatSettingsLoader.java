package com.lawkeys.hcfcore.chat;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;
import java.util.function.Consumer;

/** Turns {@code chat.yml} into an immutable {@link ChatSettings}. */
public final class ChatSettingsLoader {

    private ChatSettingsLoader() {
    }

    public static ChatSettings load(ConfigurationSection section, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        ChatSettings defaults = ChatSettings.defaults();
        if (section == null) {
            warn.accept("chat.yml is missing or empty - using the built-in chat format.");
            return defaults;
        }
        String format = Objects.requireNonNullElse(section.getString("format"), defaults.format());
        if (!format.contains(ChatSettings.MESSAGE)) {
            // A format with no message would silence the server without an error.
            warn.accept("format does not contain " + ChatSettings.MESSAGE
                    + ", so nobody's message would be shown; using the built-in format.");
            format = defaults.format();
        }
        return new ChatSettings(
                section.getBoolean("enabled", defaults.enabled()),
                format,
                Objects.requireNonNullElse(section.getString("kills-format"), defaults.killsFormat()),
                Math.max(0, section.getInt("range-blocks", defaults.rangeBlocks())),
                section.getBoolean("log-team-chat", defaults.logTeamChat()));
    }
}
