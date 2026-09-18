package com.lawkeys.hcfcore.staff.command;

import com.lawkeys.hcfcore.chat.ChatModule;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.staff.StaffMessages;
import com.lawkeys.hcfcore.staff.StaffModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

/**
 * {@code /staffchat} - toggles the staff channel, or sends one line to it.
 *
 * <p>With arguments it sends that message and leaves the channel setting alone,
 * which is what makes it usable from the console and in the middle of a
 * conversation without having to remember to toggle back.
 */
public final class StaffChatCommand implements TabExecutor {

    private final StaffModule module;

    public StaffChatCommand(StaffModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(StaffModule.STAFF_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return true;
        }
        if (!module.getSettings().enabled() || !module.getSettings().staffChat().enabled()) {
            module.getLang().send(sender, StaffMessages.DISABLED);
            return true;
        }

        if (args.length > 0) {
            module.sendToStaffChannel(format(sender.getName(), ChatModule.typedText(sender, String.join(" ", args))));
            return true;
        }
        if (!(sender instanceof Player player)) {
            // The console has no channel to toggle: it is already on every one of them.
            module.getLang().send(sender, StaffMessages.USAGE, "usage", "/" + label + " <message>");
            return true;
        }
        boolean on = module.getManager().toggleStaffChat(player.getUniqueId());
        module.getLang().send(player, on ? StaffMessages.STAFF_CHAT_ON : StaffMessages.STAFF_CHAT_OFF);
        return true;
    }

    /** Renders one staff-channel line from the configured format; {@code message} is already escaped. */
    public String format(String senderName, String message) {
        return LangManager.colorize(module.getSettings().staffChat().format()
                .replace("%player%", senderName)
                .replace("%message%", message));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
