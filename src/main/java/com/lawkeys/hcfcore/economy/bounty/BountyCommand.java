package com.lawkeys.hcfcore.economy.bounty;

import com.lawkeys.hcfcore.economy.EconomyManager;
import com.lawkeys.hcfcore.economy.EconomyMessages;
import com.lawkeys.hcfcore.lang.LangManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@code /bounty} - the list; {@code /bounty <player> <amount>} puts money on a
 * head, taken from the placer's balance at once; {@code /bounty check <player>};
 * {@code /bounty clear <player>} for staff, which returns nothing: a bounty is many
 * players' money, gone once placed.
 */
public final class BountyCommand implements TabExecutor {

    public static final String ADMIN_PERMISSION = "hcfcore.economy.bounty.admin";

    private final Supplier<Bounties> bounties;
    private final Supplier<BountyRules> rules;
    private final Supplier<EconomyManager> economy;
    private final LangManager lang;

    public BountyCommand(Supplier<Bounties> bounties, Supplier<BountyRules> rules, Supplier<EconomyManager> economy,
                         LangManager lang) {
        this.bounties = Objects.requireNonNull(bounties, "bounties");
        this.rules = Objects.requireNonNull(rules, "rules");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.lang = Objects.requireNonNull(lang, "lang");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        BountyRules current = rules.get();
        EconomyManager money = economy.get();
        Bounties all = bounties.get();
        if (!current.enabled() || money == null || all == null) {
            lang.send(sender, EconomyMessages.BOUNTY_DISABLED);
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            list(sender, all, money, current);
            return true;
        }
        if (args[0].equalsIgnoreCase("check") && args.length > 1) {
            OfflinePlayer target = known(sender, args[1]);
            if (target != null) {
                double amount = all.get(target.getUniqueId());
                lang.send(sender, amount > 0 ? EconomyMessages.BOUNTY_CHECK : EconomyMessages.BOUNTY_CHECK_NONE,
                        "player", name(target), "amount", money.format(amount));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("clear") && args.length > 1) {
            if (!sender.hasPermission(ADMIN_PERMISSION)) {
                lang.send(sender, "general.no-permission");
                return true;
            }
            OfflinePlayer target = known(sender, args[1]);
            if (target != null) {
                double cleared = all.claim(target.getUniqueId());
                lang.send(sender, EconomyMessages.BOUNTY_CLEARED, "player", name(target),
                        "amount", money.format(cleared));
            }
            return true;
        }
        if (args.length < 2) {
            lang.send(sender, EconomyMessages.BOUNTY_USAGE);
            return true;
        }
        place(sender, args[0], args[1], all, money, current);
        return true;
    }

    private void place(CommandSender sender, String name, String rawAmount, Bounties all, EconomyManager money,
                       BountyRules current) {
        if (!(sender instanceof Player placer)) {
            lang.send(sender, EconomyMessages.BOUNTY_PLAYERS_ONLY);
            return;
        }
        OfflinePlayer target = known(sender, name);
        if (target == null) {
            return;
        }
        if (target.getUniqueId().equals(placer.getUniqueId())) {
            lang.send(placer, EconomyMessages.BOUNTY_SELF);
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(rawAmount.replace(",", "."));
        } catch (NumberFormatException e) {
            lang.send(placer, EconomyMessages.BOUNTY_USAGE);
            return;
        }
        if (!Double.isFinite(amount) || amount < current.minimumAmount()) {
            lang.send(placer, EconomyMessages.BOUNTY_BELOW_MINIMUM, "minimum", money.format(current.minimumAmount()));
            return;
        }
        // Rounded down to the cent, like every sum of money.
        amount = Math.floor(amount * 100.0) / 100.0;
        var paid = money.withdraw(placer.getUniqueId(), amount);
        if (!paid.isOk()) {
            lang.send(placer, paid.getMessageKey());
            return;
        }
        double total = all.add(target.getUniqueId(), amount);
        lang.send(placer, EconomyMessages.BOUNTY_PLACED, "player", name(target), "amount", money.format(amount),
                "total", money.format(total));
        if (current.announce()) {
            String line = lang.get(EconomyMessages.BOUNTY_PLACED_BROADCAST, "placer", placer.getName(),
                    "player", name(target), "amount", money.format(amount), "total", money.format(total));
            Bukkit.getOnlinePlayers().forEach(online -> online.sendMessage(line));
            Bukkit.getConsoleSender().sendMessage(line);
            com.lawkeys.hcfcore.util.Announcements.publish(EconomyMessages.BOUNTY_PLACED_BROADCAST, line);
        }
    }

    private void list(CommandSender sender, Bounties all, EconomyManager money, BountyRules current) {
        List<Bounties.Entry> top = all.top(current.listSize());
        if (top.isEmpty()) {
            lang.send(sender, EconomyMessages.BOUNTY_LIST_EMPTY);
            return;
        }
        lang.send(sender, EconomyMessages.BOUNTY_LIST_HEADER);
        int rank = 1;
        for (Bounties.Entry entry : top) {
            lang.send(sender, EconomyMessages.BOUNTY_LIST_ENTRY, "rank", String.valueOf(rank++),
                    "player", name(Bukkit.getOfflinePlayer(entry.target())), "amount", money.format(entry.amount()));
        }
    }

    /** @return the player of that name if the server has ever seen them, after saying so otherwise */
    private OfflinePlayer known(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        OfflinePlayer target = online != null ? online : Bukkit.getOfflinePlayerIfCached(name);
        if (target == null || (!target.isOnline() && !target.hasPlayedBefore())) {
            lang.send(sender, EconomyMessages.BOUNTY_UNKNOWN_PLAYER, "player", name);
            return null;
        }
        return target;
    }

    private static String name(OfflinePlayer player) {
        String name = player.getName();
        return name == null ? String.valueOf(player.getUniqueId()) : name;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("list");
            options.add("check");
            if (sender.hasPermission(ADMIN_PERMISSION)) {
                options.add("clear");
            }
            Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
        } else if (args.length == 2 && List.of("check", "clear").contains(args[0].toLowerCase(Locale.ROOT))) {
            Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
    }
}
