package com.lawkeys.hcfcore.limiter;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * How many of a block a team's territory may hold - {@code claim-blocks} in
 * {@code limiters.yml}, the "limits per claim" the project owner chose on
 * 12/09/2026: hoppers, spawners, redstone and the like, against lag machines and
 * abuse. Ships empty: which blocks, and how many, is the server's call.
 *
 * <p>Pure Java; materials are named as the server spells them ({@code HOPPER}).
 *
 * @param limits material name to the most a territory may hold; {@code 0} means none
 */
public record ClaimBlockLimits(boolean enabled, Map<String, Integer> limits) {

    public ClaimBlockLimits {
        Map<String, Integer> checked = new LinkedHashMap<>();
        Objects.requireNonNull(limits, "limits").forEach((material, limit) -> {
            if (material != null && limit != null && limit >= 0) {
                checked.put(material.trim().toUpperCase(Locale.ROOT), limit);
            }
        });
        limits = Map.copyOf(checked);
    }

    public static ClaimBlockLimits none() {
        return new ClaimBlockLimits(true, Map.of());
    }

    /** @return whether anything is limited at all - when not, nothing is counted either */
    public boolean isActive() {
        return enabled && !limits.isEmpty();
    }

    /** @return the limit on this block, when there is one */
    public OptionalInt limitFor(String material) {
        Integer limit = isActive() && material != null ? limits.get(material.toUpperCase(Locale.ROOT)) : null;
        return limit == null ? OptionalInt.empty() : OptionalInt.of(limit);
    }

    public boolean isLimited(String material) {
        return limitFor(material).isPresent();
    }

    /** @return whether one more may be placed where {@code current} already stand */
    public static boolean mayPlace(int current, int limit) {
        return current < limit;
    }
}
