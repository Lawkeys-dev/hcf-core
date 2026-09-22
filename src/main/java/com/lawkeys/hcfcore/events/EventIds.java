package com.lawkeys.hcfcore.events;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What a staff-typed event id may look like, and how it is normalised.
 *
 * <p>Pure Java: no server API, so it is unit-tested without one.
 *
 * <p>Every id that reaches the server today comes from a YAML section key an
 * operator wrote by hand. {@code /events create} is the first place a player
 * types an id that the plugin itself turns into a YAML key with Bukkit's
 * {@code ConfigurationSection}, whose path methods ({@code createSection},
 * {@code set}, {@code getConfigurationSection}, {@code contains}...) all read a
 * {@code .} as a path separator. An id such as {@code raid.zone1} would silently
 * become a nested section {@code raid: zone1: {...}} instead of one entry named
 * {@code raid.zone1} - the event would be gone on the next reload although the
 * command reported success (found while reviewing the setup commands,
 * 21/09/2026). Restricting the charset here, before any YAML call, is what
 * keeps that impossible rather than merely undocumented.
 *
 * <p>Every id in the shipped {@code events.yml} already follows this shape
 * (lowercase, letters, digits, {@code _} and {@code -}: {@code koth},
 * {@code citadel}, {@code ktk}, {@code conquest}, {@code dtc}, {@code last-break},
 * {@code slide}), so this is a description of the existing convention, not a
 * new one - it is only enforced for the first time because it is only now that
 * the plugin itself writes an id, rather than an operator typing YAML.
 */
public final class EventIds {

    /** Ids are matched case-insensitively everywhere else in {@code events/}; a written id is lower-cased to match. */
    private static final Pattern VALID = Pattern.compile("[a-z0-9_-]+");

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 32;

    private EventIds() {
    }

    /** @return whether {@code id}, once lower-cased, is a usable event id */
    public static boolean isValid(String id) {
        if (id == null) {
            return false;
        }
        String normalized = normalize(id);
        return normalized.length() >= MIN_LENGTH && normalized.length() <= MAX_LENGTH && VALID.matcher(normalized).matches();
    }

    /** @return {@code id} lower-cased - how a staff-typed id is stored, to match the shipped convention */
    public static String normalize(String id) {
        return id.toLowerCase(Locale.ROOT);
    }
}
