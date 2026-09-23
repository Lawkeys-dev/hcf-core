package com.lawkeys.hcfcore.util;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Every announcement the plugin makes to the whole server - an event starting or
 * won, a map phase, a bounty collected - passes here with its language key, so
 * something outside the game can hear it too: the Discord webhook
 * ({@code integration/discord/}), today.
 *
 * <p>A seam with a neutral default, like the others: until a module installs a
 * listener, publishing does nothing. Called on the main thread; a listener that
 * does slow work - a web request - hands it off itself.
 */
public final class Announcements {

    private static volatile BiConsumer<String, String> listener = (key, message) -> {
    };

    private Announcements() {
    }

    /** Installs what hears every announcement; one at a time. */
    public static void listen(BiConsumer<String, String> replacement) {
        listener = Objects.requireNonNull(replacement, "replacement");
    }

    /** Back to nobody listening - on shutdown. */
    public static void reset() {
        listener = (key, message) -> {
        };
    }

    /**
     * @param key     the language key the message was made from
     * @param message the message as players read it, colours included
     */
    public static void publish(String key, String message) {
        if (key == null || message == null || message.isEmpty()) {
            return;
        }
        try {
            listener.accept(key, message);
        } catch (RuntimeException e) {
            // A broken listener must never break the announcement in the game.
        }
    }
}
