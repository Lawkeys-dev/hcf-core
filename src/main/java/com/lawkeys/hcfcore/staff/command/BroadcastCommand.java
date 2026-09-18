package com.lawkeys.hcfcore.staff.command;

import com.lawkeys.hcfcore.chat.ChatModule;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.staff.StaffMessages;
import com.lawkeys.hcfcore.staff.StaffModule;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;
import java.util.Objects;

/** {@code /broadcast} - sends one formatted line to everybody on the server. */
public final class BroadcastCommand implements TabExecutor {

    public static final String PERMISSION = "hcfcore.staff.broadcast";

    private final StaffModule module;

    public BroadcastCommand(StaffModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return true;
        }
        if (!module.getSettings().enabled()) {
            module.getLang().send(sender, StaffMessages.DISABLED);
            return true;
        }
        if (args.length == 0) {
            module.getLang().send(sender, StaffMessages.USAGE, "usage", "/" + label + " <message>");
            return true;
        }
        String line = LangManager.colorize(module.getSettings().broadcast().format()
                .replace("%message%", ChatModule.typedText(sender, String.join(" ", args))));
        // Deliberately everybody, console included, rather than Bukkit#broadcast with
        // a permission: a broadcast that only some players can see is not a broadcast.
        for (var player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(line);
        }
        Bukkit.getConsoleSender().sendMessage(line);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
