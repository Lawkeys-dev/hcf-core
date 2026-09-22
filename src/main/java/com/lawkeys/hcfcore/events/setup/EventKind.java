package com.lawkeys.hcfcore.events.setup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Every kind of event {@code /events} can create, and what setting one up takes.
 *
 * <p>One list for all of them, so staff learn one set of verbs - {@code create},
 * {@code claim}, {@code setzone}, {@code setblock}, {@code info}, {@code delete} -
 * whatever the event (the project owner's choice, 22/09/2026). A kind that has no
 * use for a verb says so, rather than the verb not existing for it.
 *
 * <p>Pure Java: no server API, so it is unit-tested without one.
 */
public enum EventKind {

    KOTH("koth", "events", "koth", Zone.ONE, false),
    CITADEL("citadel", "citadels", "citadel", Zone.ONE, false),
    KING("ktk", "kill-the-king", "ktk", Zone.NONE, false),
    CONQUEST("conquest", "conquest", "conquest", Zone.MANY, false),
    DTC("dtc", "dtc", "dtc", Zone.ONE, true),
    LAST_BREAK("lastbreak", "last-break", "last-break", Zone.ONE, true),
    SLIDE("slide", "slide", "slide", Zone.ONE, false),
    TOTEM("totem", "totem", "totem", Zone.ONE, true),
    /** A Totem with a shorter column: the same section, its own template. */
    MINI_TOTEM("minitotem", "totem", "mini-totem", Zone.ONE, true);

    /** How many zones a kind has to hold, stand in or break inside. */
    public enum Zone {
        /** Kill the King: the warzone is its ground, there is nothing to draw. */
        NONE,
        ONE,
        /** Conquest: several, each with its own name. */
        MANY
    }

    private final String typeName;
    private final String section;
    private final String template;
    private final Zone zone;
    private final boolean hasBlock;

    EventKind(String typeName, String section, String template, Zone zone, boolean hasBlock) {
        this.typeName = typeName;
        this.section = section;
        this.template = template;
        this.zone = zone;
        this.hasBlock = hasBlock;
    }

    /** @return what staff type in {@code /events create <type>} */
    public String typeName() {
        return typeName;
    }

    /** @return the top-level section of {@code events.yml} its events live in */
    public String section() {
        return section;
    }

    /** @return the entry of the shipped {@code events.yml}, under {@link #section()}, a new one is copied from */
    public String template() {
        return template;
    }

    public Zone zone() {
        return zone;
    }

    /** @return whether it has a block to place - a DTC's core, a Totem's column */
    public boolean hasBlock() {
        return hasBlock;
    }

    /** @return whether it owns land: every kind but Kill the King, whose ground is the warzone */
    public boolean hasTerritory() {
        return zone != Zone.NONE;
    }

    /** @return the kind {@code /events create <type>} names, case-insensitively */
    public static Optional<EventKind> fromTypeName(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String typed = raw.trim().toLowerCase(Locale.ROOT);
        for (EventKind kind : values()) {
            if (kind.typeName.equals(typed)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    /**
     * @return the kind of an event found under {@code section} - for the Totem
     *         section, which holds both, a Totem: nothing but the template tells
     *         the two apart, and nothing after creation needs to
     */
    public static Optional<EventKind> fromSection(String section) {
        for (EventKind kind : values()) {
            if (kind.section.equals(section)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    /** @return every section of {@code events.yml} holding events, each once, in order */
    public static List<String> sections() {
        List<String> sections = new ArrayList<>();
        for (EventKind kind : values()) {
            if (!sections.contains(kind.section)) {
                sections.add(kind.section);
            }
        }
        return sections;
    }

    /** @return every type name, for tab completion and the unknown-type message */
    public static List<String> typeNames() {
        List<String> names = new ArrayList<>();
        for (EventKind kind : values()) {
            names.add(kind.typeName);
        }
        return names;
    }
}
