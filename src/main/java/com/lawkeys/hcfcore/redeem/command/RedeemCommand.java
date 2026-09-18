package com.lawkeys.hcfcore.redeem.command;

import com.lawkeys.hcfcore.redeem.RedeemCodes;
import com.lawkeys.hcfcore.redeem.RedeemMessages;
import com.lawkeys.hcfcore.redeem.RedeemModule;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

/** {@code /redeem <code>}. No tab completion, on purpose: it would list the codes. */
public final class RedeemCommand implements TabExecutor {

    private final RedeemModule module;

    public RedeemCommand(RedeemModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return true;
        }
        if (!module.isEnabled()) {
            module.getLang().send(sender, RedeemMessages.DISABLED);
            return true;
        }
        if (module.getStartup().refuseCommand(sender)) {
            return true;
        }
        if (args.length != 1) {
            module.getLang().send(sender, RedeemMessages.USAGE, "usage", "/" + label + " <code>");
            return true;
        }
        RedeemCodes.Outcome outcome = module.getCodes().redeem(args[0], player.getUniqueId(),
                module.getFailureCooldownMillis());
        switch (outcome.status()) {
            case REDEEMED -> {
                module.flushSoon();
                for (String reward : outcome.commands()) {
                    try {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), reward.replace("%player%", player.getName()));
                    } catch (RuntimeException e) {
                        // The redemption stands: the other rewards still go out, and
                        // staff find the failure in the log with the code's name.
                        module.getPlugin().getLogger().log(Level.WARNING,
                                "A reward of code '" + args[0] + "' failed for " + player.getName() + ": " + reward, e);
                    }
                }
                module.getLang().send(player, RedeemMessages.REDEEMED, "code", args[0]);
            }
            case UNKNOWN -> module.getLang().send(player, RedeemMessages.UNKNOWN);
            case EXHAUSTED -> module.getLang().send(player, RedeemMessages.EXHAUSTED);
            case ALREADY_REDEEMED -> module.getLang().send(player, RedeemMessages.ALREADY);
            case EMPTY -> module.getLang().send(player, RedeemMessages.EMPTY);
            case TOO_SOON -> module.getLang().send(player, RedeemMessages.TOO_SOON);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
