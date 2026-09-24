package com.lawkeys.hcfcore.util;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Map;

/**
 * Hands one of the plugin's commands' names over to another plugin, and takes it
 * back: {@code /shop} switched off ({@code economy.yml}) leaves {@code /shop} to a
 * shop plugin of the server's choosing.
 *
 * <p>The first plugin to be enabled gets a name two plugins declare; the other has
 * only {@code otherplugin:shop}. So a released name goes to the other plugin's
 * command of that name if there is one, and a plugin enabled after this one simply
 * finds it free. Read against the Paper 26.2 command map, whose known commands are
 * the server's own table.
 */
public final class CommandLabels {

    private CommandLabels() {
    }

    /** Takes {@code command} off its name and aliases, giving each to another plugin's command of that name. */
    public static void release(Plugin plugin, PluginCommand command) {
        CommandMap map = Bukkit.getCommandMap();
        Map<String, Command> known = map.getKnownCommands();
        String prefix = plugin.getName().toLowerCase(Locale.ROOT) + ":";
        boolean changed = false;
        for (String label : labels(command)) {
            if (known.get(label) == command) {
                known.remove(label);
                changed = true;
                otherNamed(known, label, prefix).ifPresent(other -> known.put(label, other));
            }
        }
        if (changed) {
            Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
        }
    }

    /** Puts {@code command} back on its name and aliases, where nobody else holds them. */
    public static void claim(PluginCommand command) {
        Map<String, Command> known = Bukkit.getCommandMap().getKnownCommands();
        boolean changed = false;
        for (String label : labels(command)) {
            if (!known.containsKey(label)) {
                known.put(label, command);
                changed = true;
            }
        }
        if (changed) {
            Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
        }
    }

    private static java.util.List<String> labels(PluginCommand command) {
        java.util.List<String> labels = new java.util.ArrayList<>();
        labels.add(command.getName().toLowerCase(Locale.ROOT));
        command.getAliases().forEach(alias -> labels.add(alias.toLowerCase(Locale.ROOT)));
        return labels;
    }

    private static java.util.Optional<Command> otherNamed(Map<String, Command> known, String label, String ownPrefix) {
        return known.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith(":" + label) && !entry.getKey().startsWith(ownPrefix))
                .map(Map.Entry::getValue)
                .findFirst();
    }
}
