package com.lawkeys.hcfcore.resourcenode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * What a resource node puts back, and in what proportion.
 *
 * <p>Blocks are held as <em>names</em>, never as {@code org.bukkit.Material}: the
 * whole rule layer of this plugin compiles and runs without the server API, and
 * that is what lets a refill be unit-tested. The server layer resolves a name to
 * a {@code Material} once, at load time, and reports the ones it does not know.
 *
 * <p>Weights are relative, not percentages: {@code GLOWSTONE 1} beside
 * {@code NETHERRACK 3} means one block in four is glowstone. Operators do not
 * have to make anything add up to 100, which is one fewer way to misconfigure a
 * mountain.
 */
public record BlockPalette(List<Entry> entries) {

    /**
     * @param material a Minecraft block name, normalised to upper case without its
     *                 {@code minecraft:} prefix so it can be compared to what the
     *                 server reports for a broken block
     * @param weight   relative share, at least 1
     */
    public record Entry(String material, int weight) {

        public Entry {
            material = normalise(material);
            if (material.isEmpty()) {
                throw new IllegalArgumentException("a palette entry needs a material");
            }
            if (weight <= 0) {
                throw new IllegalArgumentException("weight must be positive for " + material);
            }
        }
    }

    public BlockPalette {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    public static BlockPalette of(Entry... entries) {
        return new BlockPalette(List.of(entries));
    }

    public static BlockPalette empty() {
        return new BlockPalette(List.of());
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** @return the sum of the weights; a {@code long}, so a silly config cannot overflow it */
    public long totalWeight() {
        long total = 0L;
        for (Entry entry : entries) {
            total += entry.weight();
        }
        return total;
    }

    /** @return whether that block name is one this palette places */
    public boolean contains(String material) {
        String wanted = normalise(material);
        for (Entry entry : entries) {
            if (entry.material().equals(wanted)) {
                return true;
            }
        }
        return false;
    }

    /** @return every distinct block name in the palette, in configuration order */
    public Set<String> materials() {
        Set<String> names = new LinkedHashSet<>();
        for (Entry entry : entries) {
            names.add(entry.material());
        }
        return names;
    }

    /**
     * Picks a block for one position.
     *
     * @param roll a value in {@code [0, 1)}; the caller owns the randomness, which
     *             is what makes the distribution testable without a seeded server
     * @return the chosen block name, or {@code null} when the palette is empty
     */
    public String pick(double roll) {
        if (entries.isEmpty()) {
            return null;
        }
        long total = totalWeight();
        // Clamp rather than reject: a caller handing us 1.0 (or a rounding artefact
        // just above it) should get the last entry, not an exception in the middle
        // of a refill.
        double bounded = Math.min(Math.max(roll, 0.0d), 0.999_999_999d);
        double target = bounded * total;
        long accumulated = 0L;
        for (Entry entry : entries) {
            accumulated += entry.weight();
            if (target < accumulated) {
                return entry.material();
            }
        }
        return entries.get(entries.size() - 1).material();
    }

    /** @return {@code raw} upper-cased, trimmed and stripped of a {@code minecraft:} prefix */
    public static String normalise(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        int colon = value.indexOf(':');
        // "minecraft:glowstone" and "GLOWSTONE" must compare equal: the server
        // reports the bare name for a broken block, and an operator may well write
        // the namespaced one.
        return colon >= 0 ? value.substring(colon + 1) : value;
    }

    /** @return the names normalised, with blanks dropped */
    public static Set<String> normaliseAll(List<String> raw) {
        Set<String> names = new LinkedHashSet<>();
        if (raw == null) {
            return names;
        }
        for (String value : raw) {
            String normalised = normalise(value);
            if (!normalised.isEmpty()) {
                names.add(normalised);
            }
        }
        return names;
    }

    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        for (Entry entry : entries) {
            parts.add(entry.material() + " x" + entry.weight());
        }
        return String.join(", ", parts);
    }
}
