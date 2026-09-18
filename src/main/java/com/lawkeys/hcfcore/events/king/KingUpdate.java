package com.lawkeys.hcfcore.events.king;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Something that happened to a Kill the King run, ready to be acted on.
 *
 * <p>Like {@code EventUpdate} for the captures: the rule engine has no server, so
 * it says what happened and the server layer does it - broadcast, message the
 * King, hurt them, pay the winner, give their items back.
 *
 * @param kingId       the King, or {@code null} when the run was called off before
 *                     anyone was crowned
 * @param winnerId     who the rewards go to, or {@code null} when nobody won
 * @param messageKey   what to say, or {@code null} for {@link Type#PENALTY}, which
 *                     is felt rather than read
 * @param witherLevel  for {@link Type#PENALTY}: the Wither level to apply, as players
 *                     see it; 0 otherwise
 * @param damage       for {@link Type#PENALTY}: the damage to deal; 0 otherwise
 */
public record KingUpdate(Type type,
                         String eventId,
                         UUID kingId,
                         UUID winnerId,
                         String messageKey,
                         Map<String, String> placeholders,
                         int witherLevel,
                         double damage) {

    public enum Type {
        /** A King was drawn. Broadcast. */
        CROWNED(Audience.EVERYONE, false),
        /** A remaining-time mark. Broadcast. */
        PROGRESS(Audience.EVERYONE, false),
        /** The King just left the warzone. Told to the King. */
        LEFT_ZONE(Audience.KING, false),
        /** The King is back inside. Told to the King. */
        RETURNED(Audience.KING, false),
        /** The King is outside past the grace: hurt them. */
        PENALTY(Audience.NOBODY, false),
        /** The time ran out with the King alive: the King wins. */
        SURVIVED(Audience.EVERYONE, true),
        /** Killed by a player who may win it: the killer wins. */
        KILLED(Audience.EVERYONE, true),
        /** Died with no killer who may win it. Nobody wins. */
        DIED(Audience.EVERYONE, true),
        /** Logged out. Nobody wins. */
        FLED(Audience.EVERYONE, true),
        /** Stopped by staff. */
        STOPPED(Audience.EVERYONE, true),
        /** Called off before anyone was crowned. */
        CANCELLED(Audience.EVERYONE, true);

        private final Audience audience;
        private final boolean ending;

        Type(Audience audience, boolean ending) {
            this.audience = audience;
            this.ending = ending;
        }

        public Audience audience() {
            return audience;
        }

        /** @return whether the run is over after this update */
        public boolean isEnding() {
            return ending;
        }
    }

    public enum Audience {
        EVERYONE, KING, NOBODY
    }

    public KingUpdate {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(eventId, "eventId");
        placeholders = Map.copyOf(Objects.requireNonNull(placeholders, "placeholders"));
    }

    /** @param placeholders alternating key and value, as elsewhere in the codebase */
    static KingUpdate of(Type type, String eventId, UUID kingId, UUID winnerId, String messageKey,
                         String... placeholders) {
        return new KingUpdate(type, eventId, kingId, winnerId, messageKey, pairs(placeholders), 0, 0.0);
    }

    static KingUpdate penalty(String eventId, UUID kingId, int witherLevel, double damage) {
        return new KingUpdate(Type.PENALTY, eventId, kingId, null, null, Map.of(), witherLevel, damage);
    }

    private static Map<String, String> pairs(String... placeholders) {
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException("placeholders must be key/value pairs");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < placeholders.length; i += 2) {
            map.put(placeholders[i], placeholders[i + 1]);
        }
        return map;
    }
}
