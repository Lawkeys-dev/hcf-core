package com.lawkeys.hcfcore.economy.command;

import com.lawkeys.hcfcore.economy.EconomyManager;
import com.lawkeys.hcfcore.economy.EconomyMessages;
import com.lawkeys.hcfcore.economy.EconomyModule;
import com.lawkeys.hcfcore.economy.EconomyResult;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.team.TeamResult;
import com.lawkeys.hcfcore.team.command.TeamSubCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * {@code /team deposit} and {@code /team withdraw}.
 *
 * <p>These belong to the team module by name but could not ship with it: moving
 * money into a team bank means taking it from a player, and there was no player
 * balance until this module existed. They are registered here through
 * {@link TeamModule#registerSubCommand}, so {@code team/} still knows nothing
 * about the economy.
 *
 * <p><strong>Both halves or neither.</strong> Each command moves money between
 * two ledgers, and a failure on the second must not leave it destroyed on the
 * first - so the second step's failure rolls the first one back.
 */
public final class TeamBankSubCommands {

    private TeamBankSubCommands() {
    }

    public static List<TeamSubCommand> all(EconomyModule economy) {
        Objects.requireNonNull(economy, "economy");
        return List.of(new Deposit(economy), new Withdraw(economy));
    }

    /** Shared plumbing: player-only, needs a team, needs a valid amount. */
    private abstract static class BankSubCommand extends TeamSubCommand {

        protected final EconomyModule economy;

        BankSubCommand(EconomyModule economy, String name, String description) {
            super(name, Set.of(), null, "<amount>", description, true, 1);
            this.economy = economy;
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Player player = (Player) sender;
            EconomyManager manager = economy.getManager();
            if (manager == null) {
                module.getLang().send(sender, EconomyMessages.DISABLED);
                return;
            }
            Optional<Team> team = module.getManager().getTeamOf(player.getUniqueId());
            if (team.isEmpty()) {
                module.getLang().send(sender, EconomyMessages.NOT_IN_TEAM);
                return;
            }
            Optional<Double> amount = EconomyModule.parseAmount(args[0]);
            if (amount.isEmpty()) {
                module.getLang().send(sender, EconomyMessages.INVALID_AMOUNT);
                return;
            }
            move(module, player, team.get(), manager, amount.get());
        }

        abstract void move(TeamModule module, Player player, Team team,
                           EconomyManager manager, double amount);

        /** Reports an economy refusal with the placeholders those messages expect. */
        protected void reportEconomyFailure(TeamModule module, Player player,
                                            EconomyManager manager, EconomyResult result) {
            module.getLang().send(player, result.getMessageKey(),
                    "balance", manager.format(manager.getBalance(player.getUniqueId())),
                    "minimum", manager.format(economy.getSettings().pay().minimumAmount()));
        }
    }

    /** Player wallet to team bank. */
    private static final class Deposit extends BankSubCommand {

        Deposit(EconomyModule economy) {
            super(economy, "deposit", "Put money into your team's bank");
        }

        @Override
        void move(TeamModule module, Player player, Team team, EconomyManager manager, double amount) {
            EconomyResult debited = manager.withdraw(player.getUniqueId(), amount);
            if (!debited.isOk()) {
                reportEconomyFailure(module, player, manager, debited);
                return;
            }

            TeamResult credited = module.getManager().depositToBank(team, player.getUniqueId(), amount);
            if (credited.isFailure()) {
                // The bank refused it (rank, or the bank is off): give it back rather
                // than let the money vanish between the two ledgers. restore() rather
                // than deposit() so no rule can refuse the refund.
                manager.restore(player.getUniqueId(), amount);
                module.getLang().send(player, credited.getMessageKey(), module.readable(credited.getPlaceholders()));
                return;
            }

            module.getLang().send(player, EconomyMessages.BANK_DEPOSITED,
                    "amount", manager.format(amount),
                    "team", team.getName(),
                    "bank", manager.format(team.getBalance()),
                    "balance", manager.format(manager.getBalance(player.getUniqueId())));
        }
    }

    /** Team bank to player wallet. */
    private static final class Withdraw extends BankSubCommand {

        Withdraw(EconomyModule economy) {
            super(economy, "withdraw", "Take money out of your team's bank");
        }

        @Override
        void move(TeamModule module, Player player, Team team, EconomyManager manager, double amount) {
            TeamResult debited = module.getManager().withdrawFromBank(team, player.getUniqueId(), amount);
            if (debited.isFailure()) {
                module.getLang().send(player, debited.getMessageKey(), module.readable(debited.getPlaceholders()));
                return;
            }

            EconomyResult credited = manager.deposit(player.getUniqueId(), amount);
            if (!credited.isOk()) {
                // The player's own ceiling refused it: put it back in the bank.
                // restoreToBank() rather than depositToBank() so no rule can refuse
                // the refund and destroy the money.
                module.getManager().restoreToBank(team, amount);
                reportEconomyFailure(module, player, manager, credited);
                return;
            }

            module.getLang().send(player, EconomyMessages.BANK_WITHDRAWN,
                    "amount", manager.format(amount),
                    "team", team.getName(),
                    "bank", manager.format(team.getBalance()),
                    "balance", manager.format(manager.getBalance(player.getUniqueId())));
        }
    }
}
