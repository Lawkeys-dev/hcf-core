package com.lawkeys.hcfcore.staff.command;

import com.lawkeys.hcfcore.staff.StaffManager.VanishSource;
import com.lawkeys.hcfcore.staff.StaffMessages;
import com.lawkeys.hcfcore.staff.StaffModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

/**
 * {@code /vanish} - hides a staff member from everybody who may not see them.
 *
 * <p>Its own command as well as part of staff mode, because the two are wanted
 * separately: watching a fight unseen does not always mean wanting a toolbar and
 * an empty inventory.
 */
public final class VanishCommand implements TabExecutor {

    private final StaffModule module;

    public VanishCommand(StaffModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return true;
        }
        if (!sender.hasPermission(StaffModule.STAFF_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return true;
        }
        if (!module.getSettings().enabled()) {
            module.getLang().send(sender, StaffMessages.DISABLED);
            return true;
        }

        if (module.getManager().isVanished(player.getUniqueId())) {
            module.getManager().reveal(player.getUniqueId());
            module.showToEveryone(player);
            module.getLang().send(player, StaffMessages.VANISH_OFF);
        } else {
            module.applyVanish(player, VanishSource.MANUAL);
            module.getLang().send(player, StaffMessages.VANISH_ON);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
