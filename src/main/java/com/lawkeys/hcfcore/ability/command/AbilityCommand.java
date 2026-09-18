package com.lawkeys.hcfcore.ability.command;

import com.lawkeys.hcfcore.ability.Ability;
import com.lawkeys.hcfcore.ability.AbilityMenu;
import com.lawkeys.hcfcore.ability.AbilityMessages;
import com.lawkeys.hcfcore.ability.AbilityModule;
import com.lawkeys.hcfcore.command.VisiblePlayers;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@code /ability}: the menu of abilities and one's cooldowns; {@code /ability list};
 * {@code /ability give <player> <ability> [amount]} for staff, from the console too -
 * which is how a killstreak or a store hands one out.
 */
public final class AbilityCommand implements TabExecutor {

    private final AbilityModule module;

    public AbilityCommand(AbilityModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var lang = module.getLang();
        if (args.length == 0) {
            if (sender instanceof Player player) {
                player.openInventory(new AbilityMenu(module, player).getInventory());
            } else {
                list(sender);
            }
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> list(sender);
            case "give" -> give(sender, label, args);
            default -> lang.send(sender, "general.unknown-command", "label", label);
        }
        return true;
    }

    private void list(CommandSender sender) {
        List<Ability> abilities = module.getSettings().abilities();
        if (abilities.isEmpty()) {
            module.getLang().send(sender, AbilityMessages.LIST_EMPTY);
            return;
        }
        module.getLang().send(sender, AbilityMessages.LIST, "abilities",
                abilities.stream().map(a -> a.id() + " (" + a.type().configName() + ")").collect(Collectors.joining(", ")));
    }

    private void give(CommandSender sender, String label, String[] args) {
        var lang = module.getLang();
        if (!sender.hasPermission(AbilityModule.ADMIN_PERMISSION)) {
            lang.send(sender, "general.no-permission");
            return;
        }
        if (args.length < 3) {
            lang.send(sender, "general-commands.usage", "usage", "/" + label + " give <player> <ability> [amount]");
            return;
        }
        Optional<Player> target = VisiblePlayers.find(sender, args[1]);
        if (target.isEmpty()) {
            lang.send(sender, "general-commands.player-not-found", "player", args[1]);
            return;
        }
        Optional<Ability> ability = module.getSettings().ability(args[2]);
        if (ability.isEmpty()) {
            lang.send(sender, AbilityMessages.UNKNOWN, "ability", args[2]);
            return;
        }
        int amount = 1;
        if (args.length > 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException notANumber) {
                amount = 1;
            }
        }
        Player player = target.get();
        player.getInventory().addItem(module.buildItem(ability.get(), amount)).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        lang.send(sender, AbilityMessages.GAVE, "amount", String.valueOf(amount),
                "ability", module.display(ability.get()), "player", player.getName());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("list");
            if (sender.hasPermission(AbilityModule.ADMIN_PERMISSION)) {
                options.add("give");
            }
            return prefixed(options, args[0]);
        }
        if (!args[0].equalsIgnoreCase("give") || !sender.hasPermission(AbilityModule.ADMIN_PERMISSION)) {
            return List.of();
        }
        if (args.length == 2) {
            return VisiblePlayers.names(sender, args[1]);
        }
        if (args.length == 3) {
            module.getSettings().abilities().forEach(a -> options.add(a.id()));
            return prefixed(options, args[2]);
        }
        return List.of();
    }

    private static List<String> prefixed(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(lower)).toList();
    }
}
