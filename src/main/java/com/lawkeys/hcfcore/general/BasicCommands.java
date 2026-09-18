package com.lawkeys.hcfcore.general;

import java.util.List;

/**
 * The names of the everyday commands ({@code BasicsCommand}), as plugin.yml declares them.
 * None is a vanilla command's name: a plugin command of the same name takes it over, and
 * {@code /item} - which admins and datapacks use as {@code /item replace} - was lost
 * that way once. The item command is {@code /i}.
 */
public final class BasicCommands {

    public static final List<String> NAMES = List.of("clearinventory", "feed", "fly", "god", "flyspeed",
            "walkspeed", "hat", "suicide", "extinguish", "workbench", "anvil", "enderchest", "i", "tphere",
            "tppos", "gmc", "gms", "gma", "gmsp", "day", "night", "sun", "rain");

    private BasicCommands() {
    }
}
