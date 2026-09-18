package com.lawkeys.hcfcore.effectcommand;

import java.util.List;
import java.util.Objects;

/**
 * A command that gives its user an effect until they die, or until they type it
 * again ({@code effect-commands.yml}).
 *
 * @param name       the command, lower case, without its slash
 * @param effect     the effect's key, {@code minecraft:speed}
 * @param level      the effect's level, {@code 1} is level I
 * @param aliases    other names it answers to
 * @param permission who may use it
 */
public record EffectCommand(String name, String effect, int level, List<String> aliases, String permission) {

    public EffectCommand {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(permission, "permission");
        aliases = List.copyOf(aliases);
    }
}
