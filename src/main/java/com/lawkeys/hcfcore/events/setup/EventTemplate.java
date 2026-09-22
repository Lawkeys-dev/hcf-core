package com.lawkeys.hcfcore.events.setup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns an example event of the shipped {@code events.yml} into a new event
 * standing where a staff member stands - what {@code /events create} writes.
 *
 * <p>The template is the shipped example itself, so a new KOTH has the same
 * capture time, a new Conquest the same four zones laid out the same way, as the
 * documentation shows. Everything in it that is a position - the zone's corners, a
 * Conquest's zones, a core, a Totem's base - moves together, keeping its shape:
 * the block (a core, a column's base) lands at the given position, or, with no
 * block, the middle of the floor of the zones does.
 *
 * <p>Works on plain maps, as SnakeYAML and Bukkit's {@code getValues} give them.
 * Pure Java: unit-tested against the shipped file.
 */
public final class EventTemplate {

    /** The colour tokens a display name starts with: {@code {primary}}, {@code {error}}... */
    private static final Pattern LEADING_TOKENS = Pattern.compile("^((?:\\{[a-z_-]+\\})*)");

    private EventTemplate() {
    }

    /**
     * @param template the example entry, left untouched
     * @param id       the new event's id, which becomes its display name, in the
     *                 template's colour
     * @return a new entry: moved to {@code (x, y, z)} in {@code world}, and with no
     *         schedule - an event staff just created never opens by itself
     */
    public static Map<String, Object> place(Map<String, Object> template, String id, String world, int x, int y, int z) {
        Map<String, Object> entry = copy(template);
        Object name = entry.get("display-name");
        Matcher tokens = LEADING_TOKENS.matcher(name == null ? "" : name.toString());
        entry.put("display-name", (tokens.find() ? tokens.group(1) : "") + id);
        entry.put("world", world);
        entry.put("schedule", new ArrayList<>());
        Optional<int[]> anchor = anchor(entry);
        if (anchor.isPresent()) {
            shift(entry, x - anchor.get()[0], y - anchor.get()[1], z - anchor.get()[2]);
        }
        return entry;
    }

    /**
     * @return the land an entry's zones stand on - {@code {minX, minZ, maxX, maxZ}},
     *         every zone included - or empty for an event with none
     */
    public static Optional<int[]> bounds(Map<String, Object> entry) {
        List<Map<String, Object>> corners = corners(entry);
        if (corners.isEmpty()) {
            return Optional.empty();
        }
        int[] bounds = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (Map<String, Object> corner : corners) {
            int cx = coordinate(corner, "x");
            int cz = coordinate(corner, "z");
            bounds[0] = Math.min(bounds[0], cx);
            bounds[1] = Math.min(bounds[1], cz);
            bounds[2] = Math.max(bounds[2], cx);
            bounds[3] = Math.max(bounds[3], cz);
        }
        return Optional.of(bounds);
    }

    /** @return the point that lands where staff stand: the block, else the middle of the zones' floor */
    private static Optional<int[]> anchor(Map<String, Object> entry) {
        for (String key : List.of("core", "base")) {
            Map<String, Object> block = map(entry.get(key));
            if (isPosition(block)) {
                return Optional.of(new int[]{coordinate(block, "x"), coordinate(block, "y"), coordinate(block, "z")});
            }
        }
        List<Map<String, Object>> corners = corners(entry);
        if (corners.isEmpty()) {
            return Optional.empty();
        }
        int[] bounds = bounds(entry).orElseThrow();
        int floor = Integer.MAX_VALUE;
        for (Map<String, Object> corner : corners) {
            floor = Math.min(floor, coordinate(corner, "y"));
        }
        return Optional.of(new int[]{Math.floorDiv(bounds[0] + bounds[2], 2), floor, Math.floorDiv(bounds[1] + bounds[3], 2)});
    }

    /** @return every zone corner: the entry's own, and each of a Conquest's zones' */
    private static List<Map<String, Object>> corners(Map<String, Object> entry) {
        List<Map<String, Object>> corners = new ArrayList<>();
        addCorners(entry, corners);
        Map<String, Object> zones = map(entry.get("zones"));
        if (zones != null) {
            for (Object zone : zones.values()) {
                Map<String, Object> zoneMap = map(zone);
                if (zoneMap != null) {
                    addCorners(zoneMap, corners);
                }
            }
        }
        return corners;
    }

    private static void addCorners(Map<String, Object> section, List<Map<String, Object>> into) {
        for (String key : List.of("corner-1", "corner-2")) {
            Map<String, Object> corner = map(section.get(key));
            if (isPosition(corner)) {
                into.add(corner);
            }
        }
    }

    /** Moves every position in the entry - any map with a numeric x, y and z - by the offset. */
    private static void shift(Map<String, Object> section, int dx, int dy, int dz) {
        if (isPosition(section)) {
            section.put("x", coordinate(section, "x") + dx);
            section.put("y", coordinate(section, "y") + dy);
            section.put("z", coordinate(section, "z") + dz);
            return;
        }
        for (Object value : section.values()) {
            Map<String, Object> child = map(value);
            if (child != null) {
                shift(child, dx, dy, dz);
            }
        }
    }

    private static boolean isPosition(Map<String, Object> section) {
        return section != null && section.get("x") instanceof Number && section.get("y") instanceof Number
                && section.get("z") instanceof Number;
    }

    private static int coordinate(Map<String, Object> section, String axis) {
        return ((Number) section.get(axis)).intValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    /** @return a deep copy, maps and lists copied, so the template is never touched */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> copy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), copyValue(entry.getValue()));
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return copy((Map<String, Object>) map);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object item : list) {
                copy.add(copyValue(item));
            }
            return copy;
        }
        return value;
    }
}
