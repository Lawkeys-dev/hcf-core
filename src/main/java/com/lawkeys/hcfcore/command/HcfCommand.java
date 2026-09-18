package com.lawkeys.hcfcore.command;

import com.lawkeys.hcfcore.HCFCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * {@code /hcf} - the plugin's administrative command.
 *
 * <p>{@code /hcf reload} re-reads every configuration and language file without a
 * restart, which ARCHITECTURE.md section 2 lists as non-negotiable. It is the one
 * command whose name is deliberately not configurable (CONTRIBUTING.md section 3).
 */
public final class HcfCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("reload", "version");

    private final HCFCore plugin;

    public HcfCommand(HCFCore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("hcfcore.admin")) {
            plugin.getLangManager().send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            sendVersion(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "version" -> sendVersion(sender);
            default -> plugin.getLangManager().send(sender, "general.unknown-command", "label", label);
        }
        return true;
    }

    private void reload(CommandSender sender) {
        try {
            plugin.reloadEverything();
            plugin.getLangManager().send(sender, "general.reload-success");
        } catch (RuntimeException e) {
            // Never let a bad config kill the command; report it and keep the
            // previously loaded values in place.
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Reload failed", e);
            plugin.getLangManager().send(sender, "general.reload-failed",
                    "error", String.valueOf(e.getMessage()));
        }
    }

    private void sendVersion(CommandSender sender) {
        plugin.getLangManager().send(sender, "general.version",
                "version", plugin.getPluginMeta().getVersion(),
                "mode", plugin.getGameMode().name());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !sender.hasPermission("hcfcore.admin")) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        for (String sub : SUBCOMMANDS) {
            if (sub.startsWith(prefix)) {
                options.add(sub);
            }
        }
        return options;
    }
}
