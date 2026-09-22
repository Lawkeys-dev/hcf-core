package com.lawkeys.hcfcore.events.setup;

import java.util.Locale;

/**
 * The name of the server team whose land is an event's territory, when
 * {@code /events create} makes one: the event's id, in the shape team names take.
 *
 * <p>Event ids allow {@code -}, which the shipped team-name pattern does not, so
 * {@code last-break} becomes {@code LastBreak}. Pure Java: unit-tested.
 */
public final class TerritoryNames {

    private TerritoryNames() {
    }

    /**
     * @param maxLength teams.yml's names.max-length: a longer name is cut to it
     * @return the id with each {@code -}-separated word capitalised and the
     *         {@code -} dropped; anything but letters, digits and {@code _} removed
     */
    public static String forEvent(String id, int maxLength) {
        StringBuilder name = new StringBuilder();
        for (String word : id.split("-")) {
            String clean = word.replaceAll("[^A-Za-z0-9_]", "");
            if (!clean.isEmpty()) {
                name.append(clean.substring(0, 1).toUpperCase(Locale.ROOT)).append(clean.substring(1));
            }
        }
        return name.length() > maxLength ? name.substring(0, Math.max(0, maxLength)) : name.toString();
    }
}
