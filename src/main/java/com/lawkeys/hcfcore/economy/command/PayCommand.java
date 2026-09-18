package com.lawkeys.hcfcore.economy.command;

import com.lawkeys.hcfcore.command.VisiblePlayers;
import com.lawkeys.hcfcore.economy.EconomyManager;
import com.lawkeys.hcfcore.economy.EconomyMessages;
import com.lawkeys.hcfcore.economy.EconomyModule;
import com.lawkeys.hcfcore.economy.EconomyResult;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /pay <player> <amount>} and {@code /balance [player]}.
 *
 * <p>Parses input and renders the outcome; the money itself moves in
 * {@code EconomyManager}, which is where the concurrency guarantees live.
 */
public final class PayCommand implements TabExecutor {

    private final EconomyModule module;

    public PayCommand(EconomyModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Before balances land, /balance would show the starting amount and a
        // /pay would be undone by the load.
        if (module.getStartup().refuseCommand(sender)) {
            return true;
        }
        EconomyManager economy = module.getManager();
        if (economy == null) {
            module.getLang().send(sender, EconomyMessages.DISABLED);
            return true;
        }

        // /balance shares this executor - the two commands only differ in how many
        // arguments they expect, and both need the same formatting and lookup.
        if (!command.getName().equalsIgnoreCase("pay")) {
            if (args.length == 0) {
                showBalance(sender, economy);
            } else {
                showOtherBalance(sender, economy, args[0]);
            }
            return true;
        }

        if (args.length < 2) {
            return false; // Paper prints the usage declared in plugin.yml.
        }
        pay(sender, economy, args);
        return true;
    }

    private void showBalance(CommandSender sender, EconomyManager economy) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        module.getLang().send(sender, EconomyMessages.BALANCE_SELF,
                "balance", economy.format(economy.getBalance(player.getUniqueId())));
    }

    private void showOtherBalance(CommandSender sender, EconomyManager economy, String name) {
        Optional<UUID> target = module.resolvePlayer(name);
        if (target.isEmpty()) {
            module.getLang().send(sender, EconomyMessages.PLAYER_NOT_FOUND, "player", name);
            return;
        }
        module.getLang().send(sender, EconomyMessages.BALANCE_OTHER,
                "player", module.nameOf(target.get()),
                "balance", economy.format(economy.getBalance(target.get())));
    }

    private void pay(CommandSender sender, EconomyManager economy, String[] args) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        Optional<UUID> target = module.resolvePlayer(args[0]);
        if (target.isEmpty()) {
            module.getLang().send(sender, EconomyMessages.PLAYER_NOT_FOUND, "player", args[0]);
            return;
        }
        Optional<Double> amount = EconomyModule.parseAmount(args[1]);
        if (amount.isEmpty()) {
            module.getLang().send(sender, EconomyMessages.INVALID_AMOUNT);
            return;
        }

        EconomyResult result = economy.transfer(player.getUniqueId(), target.get(), amount.get());
        if (!result.isOk()) {
            module.getLang().send(sender, result.getMessageKey(),
                    "balance", economy.format(economy.getBalance(player.getUniqueId())),
                    "minimum", economy.format(module.getSettings().pay().minimumAmount()));
            return;
        }

        String formatted = economy.format(amount.get());
        module.getLang().send(sender, EconomyMessages.PAY_SENT,
                "amount", formatted, "player", module.nameOf(target.get()),
                "balance", economy.format(economy.getBalance(player.getUniqueId())));

        Player recipient = Bukkit.getPlayer(target.get());
        if (recipient != null) {
            module.getLang().send(recipient, EconomyMessages.PAY_RECEIVED,
                    "amount", formatted, "player", player.getName(),
                    "balance", economy.format(economy.getBalance(target.get())));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        // Only names the sender can see: completion would otherwise list vanished staff.
        return VisiblePlayers.names(sender, args[0]);
    }
}
