package com.lawkeys.hcfcore.ability;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An ability's own settings, checked against its type's {@link AbilityType.Param}s:
 * every key its type reads is here, read from the file or its default.
 */
public final class AbilityParams {

    private final Map<String, Object> values;

    AbilityParams(Map<String, Object> values) {
        this.values = Map.copyOf(values);
    }

    public static AbilityParams defaults(AbilityType type) {
        Map<String, Object> values = new java.util.HashMap<>();
        type.params().forEach(param -> values.put(param.key(), param.fallback()));
        return new AbilityParams(values);
    }

    private Object get(String key) {
        return Objects.requireNonNull(values.get(key), () -> "no setting " + key);
    }

    public long whole(String key) {
        return ((Number) get(key)).longValue();
    }

    public double decimal(String key) {
        return ((Number) get(key)).doubleValue();
    }

    public boolean bool(String key) {
        return (Boolean) get(key);
    }

    public AbilityEffect effect(String key) {
        return (AbilityEffect) get(key);
    }

    @SuppressWarnings("unchecked")
    public List<AbilityEffect> effects(String key) {
        return (List<AbilityEffect>) get(key);
    }

    @SuppressWarnings("unchecked")
    public Map<Integer, List<AbilityEffect>> effectTable(String key) {
        return (Map<Integer, List<AbilityEffect>>) get(key);
    }

    @SuppressWarnings("unchecked")
    public List<String> strings(String key) {
        return (List<String>) get(key);
    }
}
