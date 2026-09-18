package com.lawkeys.hcfcore.kit;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A partner item: an item that does something when it is used.
 *
 * <p>FEATURES.md section 7 calls abilities and partner items a core feature of the
 * game mode, and says the list of them - effects, triggers, cooldowns - is to be
 * specified when they are implemented. The mechanism is here and the list is
 * {@code abilities.yml}, shipped empty: an ability is an item, a trigger, a
 * cooldown and a list of console commands, which is the shape that can express any
 * of them without this module knowing what a "partner item" is meant to do.
 *
 * @param id         identifier, stored on the item so a use can find its ability again
 * @param material   the item
 * @param displayName shown on it, colour codes allowed
 * @param lore       shown under the name
 * @param cooldownSeconds wait between uses, per player
 * @param consume    whether using it takes one from the stack, which is what makes a
 *                   partner item a consumable rather than a tool
 * @param commands   run from the console, with {@code %player%}
 */
public record Ability(String id, String material, String displayName, List<String> lore,
                      long cooldownSeconds, boolean consume, List<String> commands) {

    public Ability {
        Objects.requireNonNull(id, "id");
        id = id.toLowerCase(Locale.ROOT);
        Objects.requireNonNull(material, "material");
        lore = List.copyOf(Objects.requireNonNullElseGet(lore, List::<String>of));
        commands = List.copyOf(Objects.requireNonNullElseGet(commands, List::<String>of));
        cooldownSeconds = Math.max(0L, cooldownSeconds);
    }

    /** @return the commands with the user's name filled in */
    public List<String> commandsFor(String playerName) {
        return commands.stream()
                .map(command -> command.replace("%player%", playerName == null ? "" : playerName))
                .toList();
    }

    /** @return whether using this would actually do anything */
    public boolean hasEffect() {
        return !commands.isEmpty();
    }
}
