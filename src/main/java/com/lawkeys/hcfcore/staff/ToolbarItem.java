package com.lawkeys.hcfcore.staff;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One slot of the staff-mode toolbar: an item, and the command using it runs.
 *
 * <p><strong>A command string rather than a built-in action</strong>, decided by the
 * project owner on 12/09/2026. An operator can bind any command to any slot,
 * including one from another plugin, without a code change - which is what
 * ARCHITECTURE.md section 2 asks for. The cost is that a slot can name a command
 * that does not exist; that shows up as the server's usual "unknown command"
 * rather than as a broken toolbar.
 *
 * <p>The material is a name, not a {@code Material}: this class stays free of
 * Bukkit so the substitution rules below can be tested without a server, and the
 * adapter resolves the name when it builds the stack.
 *
 * @param slot        inventory slot, 0-35 (0-8 being the hotbar)
 * @param material    material name, resolved by the server layer
 * @param name        display name, {@code &} colour codes allowed
 * @param lore        display lore, may be empty
 * @param command     command to run, without the leading slash
 * @param needsTarget whether the command needs a player to act on, which makes the
 *                    item work by clicking somebody rather than by clicking the air
 */
public record ToolbarItem(int slot, String material, String name, List<String> lore,
                          String command, boolean needsTarget) {

    /** The widest inventory slot a player has, so a misconfigured slot is caught on load. */
    public static final int MAX_SLOT = 35;

    /** Replaced by the name of the player the item was used on. */
    public static final String TARGET_PLACEHOLDER = "%player%";

    /** Replaced by the name of the staff member using the item. */
    public static final String USER_PLACEHOLDER = "%staff%";

    public ToolbarItem {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(command, "command");
        lore = List.copyOf(Objects.requireNonNullElseGet(lore, List::of));
    }

    /**
     * Fills the placeholders in with the two names this use has.
     *
     * @param userName   the staff member using the item
     * @param targetName the player clicked, or {@code null} when there is none
     * @return the command to run, without a leading slash
     *
     * <p>A {@code null} target substitutes an empty string rather than the word
     * "null": the resulting command is then merely missing an argument, and the
     * command it is aimed at answers with its own usage message. {@link #isUsable}
     * is what stops a targeted item being used on nothing in the first place.
     */
    public String commandFor(String userName, String targetName) {
        return command
                .replace(TARGET_PLACEHOLDER, targetName == null ? "" : targetName)
                .replace(USER_PLACEHOLDER, userName == null ? "" : userName);
    }

    /**
     * @return whether this item can be used with the target it was given
     *
     * <p>An item that needs a target cannot run without one. The reverse is
     * deliberately allowed: an untargeted item clicked on a player still runs,
     * because a tracker or a vanish toggle should not stop working just because
     * somebody happened to be standing in the way.
     */
    public boolean isUsable(String targetName) {
        return !needsTarget || (targetName != null && !targetName.isBlank());
    }

    /** @return whether this slot number can actually be given to a player */
    public boolean hasValidSlot() {
        return slot >= 0 && slot <= MAX_SLOT;
    }

    /** @return the first word of the command, for permission and logging messages */
    public String commandName() {
        String trimmed = command.trim();
        int space = trimmed.indexOf(' ');
        return (space < 0 ? trimmed : trimmed.substring(0, space)).toLowerCase(Locale.ROOT);
    }
}
