package com.lawkeys.hcfcore.staff.command;

import com.lawkeys.hcfcore.staff.StaffMessages;
import com.lawkeys.hcfcore.staff.StaffModule;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

/**
 * {@code /clearchat} - pushes everything off everybody's screen.
 *
 * <p>Blank lines rather than any clearing API, because there is none: the client
 * keeps its own chat history and the server can only add to it. The count is
 * configurable since chat height differs between clients and resolutions.
 *
 * <p>Staff are skipped on purpose: clearing chat is usually a response to something
 * that was said, and the staff dealing with it are exactly the people who still need
 * to read it. They are told it happened instead.
 */
public final class ClearChatCommand implements TabExecutor {

    public static final String PERMISSION = "hcfcore.staff.clearchat";

    private final StaffModule module;

    public ClearChatCommand(StaffModule module) {
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
        String blank = " ".repeat(1);
        int lines = module.getSettings().broadcast().clearChatLines();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(StaffModule.STAFF_PERMISSION)) {
                continue;
            }
            for (int i = 0; i < lines; i++) {
                player.sendMessage(blank);
            }
        }
        module.sendToStaffChannel(module.getLang().get(StaffMessages.CHAT_CLEARED,
                "player", sender.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
