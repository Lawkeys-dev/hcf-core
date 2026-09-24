package com.lawkeys.hcfcore.team;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

/**
 * A button another module adds to {@code /team settings}: how {@code claim/} puts
 * the claim lock and the HQ in the window without the team module knowing about
 * territory. Called on the main thread.
 */
public interface TeamMenuButton {

    /** Its key in {@code team-settings.icons}. */
    String key();

    /** The item it shows as, unless {@code team-settings.icons} says otherwise. */
    String defaultIcon();

    /** @return its name, as a language file text (colour tokens allowed) */
    String name(Team team, Player viewer);

    /** @return the lines under its name */
    List<String> lore(Team team, Player viewer);

    /** What a click does: most run the command it stands for, as the player. */
    void click(Team team, Player viewer, ClickType click);
}
