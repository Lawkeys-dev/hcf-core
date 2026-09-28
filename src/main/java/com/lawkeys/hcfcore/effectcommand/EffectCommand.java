package com.lawkeys.hcfcore.effectcommand;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A command that gives its user one effect or several until they die, or until
 * they type it again ({@code effect-commands.yml}): {@code /speed}, or
 * {@code /alleffect} for a whole set at once.
 *
 * @param name       the command, lower case, without its slash
 * @param effects    each effect's key, {@code minecraft:speed}, to its level -
 *                   {@code 1} is level I - in the file's order
 * @param aliases    other names it answers to
 * @param permission who may use it
 */
public record EffectCommand(String name, Map<String, Integer> effects, List<String> aliases, String permission) {

    public EffectCommand {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(permission, "permission");
        if (effects.isEmpty()) {
            throw new IllegalArgumentException("an effect command gives at least one effect");
        }
        effects = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(effects));
        aliases = List.copyOf(aliases);
    }

    /** A command of one effect. */
    public EffectCommand(String name, String effect, int level, List<String> aliases, String permission) {
        this(name, Map.of(Objects.requireNonNull(effect, "effect"), level), aliases, permission);
    }

    /** @return its first effect - its only one, for most commands */
    public String effect() {
        return effects.keySet().iterator().next();
    }

    /** @return the level of its first effect */
    public int level() {
        return effects.values().iterator().next();
    }
}
