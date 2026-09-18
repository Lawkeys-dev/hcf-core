package com.lawkeys.hcfcore.util;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Runs the console commands an event gives its winner: KOTH, Conquest, Kill the King.
 *
 * <p>A command that fails is reported and the others still run - part of a reward is
 * better than none. The dispatching is handed in, so this stays free of the server
 * API; callers pass the console dispatcher and their logger.
 */
public final class RewardCommands {

    private RewardCommands() {
    }

    /**
     * @param placeholders {@code team} fills {@code %team%}, and so on; a placeholder
     *                     with no value is left as it is
     * @param dispatch     runs one command, on the main thread
     * @param failed       told of each command that threw, with what it threw
     */
    public static void run(List<String> commands, Map<String, String> placeholders,
                           Consumer<String> dispatch, BiConsumer<String, RuntimeException> failed) {
        Objects.requireNonNull(dispatch, "dispatch");
        Objects.requireNonNull(failed, "failed");
        for (String command : commands) {
            String resolved = command;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                resolved = resolved.replace('%' + entry.getKey() + '%', entry.getValue());
            }
            try {
                dispatch.accept(resolved);
            } catch (RuntimeException e) {
                failed.accept(resolved, e);
            }
        }
    }
}
