package com.lawkeys.hcfcore.economy.command;

import com.lawkeys.hcfcore.economy.EconomyManager;
import com.lawkeys.hcfcore.economy.EconomyMessages;
import com.lawkeys.hcfcore.economy.EconomyModule;
import com.lawkeys.hcfcore.economy.EconomyResult;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /eco give|take|set <player> <amount>} - the staff economy management of
 * FEATURES.md section 5.
 */
public final class EconomyCommand implements TabExecutor {

    public static final String ADMIN_PERMISSION = "hcfcore.economy.admin";

    private static final List<String> SUBCOMMANDS = List.of("give", "take", "set");

    private final EconomyModule module;

    public EconomyCommand(EconomyModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return true;
        }
        // The console is here from the first second; an /eco give before balances
        // land would be undone by the load.
        if (module.getStartup().refuseCommand(sender)) {
            return true;
        }
        EconomyManager economy = module.getManager();
        if (economy == null) {
            module.getLang().send(sender, EconomyMessages.DISABLED);
            return true;
        }
        if (args.length < 3) {
            module.getLang().send(sender, "general.unknown-command", "label", label);
            return true;
        }

        Optional<UUID> target = module.resolvePlayer(args[1]);
        if (target.isEmpty()) {
            module.getLang().send(sender, EconomyMessages.PLAYER_NOT_FOUND, "player", args[1]);
            return true;
        }
        Optional<Double> amount = EconomyModule.parseAmount(args[2]);
        if (amount.isEmpty()) {
            module.getLang().send(sender, EconomyMessages.INVALID_AMOUNT);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        EconomyResult result;
        String successKey;
        switch (action) {
            case "give" -> {
                result = economy.deposit(target.get(), amount.get());
                successKey = EconomyMessages.ADMIN_GIVEN;
            }
            case "take" -> {
                result = economy.withdraw(target.get(), amount.get());
                successKey = EconomyMessages.ADMIN_TAKEN;
            }
            case "set" -> {
                result = economy.set(target.get(), amount.get());
                successKey = EconomyMessages.ADMIN_SET;
            }
            default -> {
                module.getLang().send(sender, "general.unknown-command", "label", label);
                return true;
            }
        }

        if (!result.isOk()) {
            module.getLang().send(sender, result.getMessageKey(),
                    "balance", economy.format(economy.getBalance(target.get())),
                    "minimum", "0");
            return true;
        }

        String name = module.nameOf(target.get());
        module.getLang().send(sender, successKey,
                "amount", economy.format(amount.get()), "player", name,
                "balance", economy.format(economy.getBalance(target.get())));

        // Tell the player their money moved - a silent balance change reads as a bug.
        Player online = Bukkit.getPlayer(target.get());
        if (online != null) {
            module.getLang().send(online, EconomyMessages.ADMIN_RECEIVED,
                    "balance", economy.format(economy.getBalance(target.get())));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    options.add(sub);
                }
            }
            return options;
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(player.getName());
                }
            }
            return names;
        }
        return List.of();
    }
}
