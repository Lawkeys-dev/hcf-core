package com.lawkeys.hcfcore.ability;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A partner item ({@code abilities.yml}): an item, what it does, how often.
 *
 * @param id              identifier, stored on the item so a use finds its ability again
 * @param type            what it does
 * @param material        the item, upper case
 * @param name            shown on it, colour codes allowed
 * @param lore            shown under the name
 * @param glow            whether it shines as an enchanted item does
 * @param enchantments    enchantment key to level, on the item itself (a bow's Infinity)
 * @param cooldownSeconds wait before this ability can be used again
 * @param consume         whether using it takes one from the stack
 * @param uses            above {@code 0}: the item stays, and its durability counts the uses left - it
 *                        wears by one per use, never by a hit or a shot, and breaks after the last
 * @param commands        for {@code commands}: run from the console, with {@code %player%}
 * @param params          what its type reads
 */
public record Ability(String id, AbilityType type, String material, String name, List<String> lore, boolean glow,
                      Map<String, Integer> enchantments, long cooldownSeconds, boolean consume,
                      long uses, List<String> commands, AbilityParams params) {

    public Ability {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(params, "params");
        lore = List.copyOf(lore);
        enchantments = Map.copyOf(enchantments);
        commands = List.copyOf(commands);
        cooldownSeconds = Math.max(0L, cooldownSeconds);
        uses = Math.max(0L, uses);
    }

    /** @return the commands with the user's name filled in */
    public List<String> commandsFor(String playerName) {
        return commands.stream().map(command -> command.replace("%player%", playerName)).toList();
    }
}
