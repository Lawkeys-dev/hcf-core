package com.lawkeys.hcfcore.crowbar.command;

import com.lawkeys.hcfcore.command.VisiblePlayers;
import com.lawkeys.hcfcore.crowbar.CrowbarMessages;
import com.lawkeys.hcfcore.crowbar.CrowbarModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * {@code /crowbar give <player> [amount]} - how a crowbar enters the game, from
 * staff, a kit, a redeem code or a crate plugin's reward command.
 */
public final class CrowbarCommand implements TabExecutor {

    private final CrowbarModule module;

    public CrowbarCommand(CrowbarModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(CrowbarModule.ADMIN_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            module.getLang().send(sender, CrowbarMessages.USAGE, "usage", "/" + label + " give <player> [amount]");
            return true;
        }
        Player target = VisiblePlayers.find(sender, args[1]).orElse(null);
        if (target == null) {
            module.getLang().send(sender, CrowbarMessages.PLAYER_NOT_FOUND, "player", args[1]);
            return true;
        }
        int amount = 1;
        if (args.length > 2) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                module.getLang().send(sender, CrowbarMessages.USAGE, "usage", "/" + label + " give <player> [amount]");
                return true;
            }
        }
        // One at a time: each crowbar counts its own uses, so they must not stack.
        for (int i = 0; i < amount; i++) {
            Map<Integer, ItemStack> overflow = target.getInventory().addItem(module.create());
            overflow.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
        }
        module.getLang().send(target, CrowbarMessages.RECEIVED, "amount", String.valueOf(amount));
        if (!target.equals(sender)) {
            module.getLang().send(sender, CrowbarMessages.GIVEN,
                    "amount", String.valueOf(amount), "player", target.getName());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(CrowbarModule.ADMIN_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return "give".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("give") : List.of();
        }
        if (args.length == 2) {
            return VisiblePlayers.names(sender, args[1]);
        }
        return List.of();
    }
}
