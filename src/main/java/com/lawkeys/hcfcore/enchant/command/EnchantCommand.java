package com.lawkeys.hcfcore.enchant.command;

import com.lawkeys.hcfcore.command.VisiblePlayers;
import com.lawkeys.hcfcore.enchant.CustomEnchant;
import com.lawkeys.hcfcore.enchant.EnchantLevels;
import com.lawkeys.hcfcore.enchant.EnchantMessages;
import com.lawkeys.hcfcore.enchant.EnchantModule;
import com.lawkeys.hcfcore.lang.LangManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code /cenchant} - {@code list} for everybody; {@code apply <enchant> [level]} and
 * {@code remove <enchant>} on the held item, and {@code give <player> <enchant>
 * [level] [amount]} for books, for staff. A book is how a shop, a crate or a redeem
 * code hands an enchant out: it runs {@code cenchant give}.
 */
public final class EnchantCommand implements TabExecutor {

    private static final List<String> STAFF = List.of("apply", "remove", "give");

    private final EnchantModule module;

    public EnchantCommand(EnchantModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!module.isEnabled()) {
            module.getLang().send(sender, EnchantMessages.DISABLED);
            return true;
        }
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        if (!sub.equals("list") && !sender.hasPermission(EnchantModule.ADMIN_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return true;
        }
        switch (sub) {
            case "list" -> list(sender);
            case "apply" -> apply(sender, args, label);
            case "remove" -> remove(sender, args, label);
            case "give" -> give(sender, args, label);
            default -> module.getLang().send(sender, EnchantMessages.USAGE,
                    "usage", "/" + label + " <list|apply <enchant> [level]|remove <enchant>|give <player> <enchant> [level] [amount]>");
        }
        return true;
    }

    private void list(CommandSender sender) {
        List<CustomEnchant> all = module.list();
        if (all.isEmpty()) {
            module.getLang().send(sender, EnchantMessages.LIST_EMPTY);
            return;
        }
        module.getLang().send(sender, EnchantMessages.LIST_HEADER, "count", String.valueOf(all.size()));
        for (CustomEnchant enchant : all) {
            List<String> targets = enchant.targets().stream().map(t -> t.name().toLowerCase(Locale.ROOT)).sorted().toList();
            module.getLang().send(sender, EnchantMessages.LIST_ENTRY,
                    "id", enchant.id(), "max", EnchantLevels.roman(enchant.maxLevel()),
                    "targets", String.join(", ", targets),
                    "enchant", LangManager.colorize(enchant.displayName()));
        }
    }

    private void apply(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        if (args.length < 2) {
            module.getLang().send(sender, EnchantMessages.USAGE, "usage", "/" + label + " apply <enchant> [level]");
            return;
        }
        Optional<CustomEnchant> enchant = enchant(sender, args[1]);
        Optional<Integer> level = enchant.flatMap(found -> level(sender, args.length > 2 ? args[2] : "1", found));
        if (enchant.isEmpty() || level.isEmpty()) {
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.isEmpty()) {
            module.getLang().send(sender, EnchantMessages.NOTHING_HELD);
            return;
        }
        if (!EnchantModule.fits(enchant.get(), held)) {
            module.getLang().send(sender, EnchantMessages.WRONG_ITEM,
                    "enchant", LangManager.colorize(enchant.get().displayName()));
            return;
        }
        module.set(held, enchant.get(), level.get());
        player.getInventory().setItemInMainHand(held);
        module.getLang().send(sender, EnchantMessages.APPLIED,
                "enchant", LangManager.colorize(enchant.get().displayName()), "level", EnchantLevels.roman(level.get()));
    }

    private void remove(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        if (args.length != 2) {
            module.getLang().send(sender, EnchantMessages.USAGE, "usage", "/" + label + " remove <enchant>");
            return;
        }
        Optional<CustomEnchant> enchant = enchant(sender, args[1]);
        if (enchant.isEmpty()) {
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!module.levels(held).containsKey(enchant.get())) {
            module.getLang().send(sender, EnchantMessages.NOT_ON_ITEM,
                    "enchant", LangManager.colorize(enchant.get().displayName()));
            return;
        }
        module.set(held, enchant.get(), 0);
        player.getInventory().setItemInMainHand(held);
        module.getLang().send(sender, EnchantMessages.REMOVED, "enchant", LangManager.colorize(enchant.get().displayName()));
    }

    private void give(CommandSender sender, String[] args, String label) {
        if (args.length < 3) {
            module.getLang().send(sender, EnchantMessages.USAGE,
                    "usage", "/" + label + " give <player> <enchant> [level] [amount]");
            return;
        }
        Player target = VisiblePlayers.find(sender, args[1]).orElse(null);
        if (target == null) {
            module.getLang().send(sender, EnchantMessages.PLAYER_NOT_FOUND, "player", args[1]);
            return;
        }
        Optional<CustomEnchant> enchant = enchant(sender, args[2]);
        Optional<Integer> level = enchant.flatMap(found -> level(sender, args.length > 3 ? args[3] : "1", found));
        if (enchant.isEmpty() || level.isEmpty()) {
            return;
        }
        int amount = 1;
        if (args.length > 4) {
            try {
                amount = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                amount = 0;
            }
            if (amount < 1 || amount > 64) {
                module.getLang().send(sender, EnchantMessages.INVALID_AMOUNT, "input", args[4]);
                return;
            }
        }
        ItemStack books = module.book(enchant.get(), level.get());
        books.setAmount(amount);
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(books);
        overflow.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
        String name = LangManager.colorize(enchant.get().displayName());
        String roman = EnchantLevels.roman(level.get());
        module.getLang().send(target, EnchantMessages.RECEIVED, "enchant", name, "level", roman,
                "amount", String.valueOf(amount));
        if (!target.equals(sender)) {
            module.getLang().send(sender, EnchantMessages.GIVEN, "enchant", name, "level", roman,
                    "amount", String.valueOf(amount), "player", target.getName());
        }
    }

    private Optional<CustomEnchant> enchant(CommandSender sender, String id) {
        Optional<CustomEnchant> enchant = module.find(id);
        if (enchant.isEmpty()) {
            module.getLang().send(sender, EnchantMessages.UNKNOWN, "input", id);
        }
        return enchant;
    }

    private Optional<Integer> level(CommandSender sender, String input, CustomEnchant enchant) {
        try {
            int level = Integer.parseInt(input);
            if (level >= 1 && level <= enchant.maxLevel()) {
                return Optional.of(level);
            }
        } catch (NumberFormatException ignored) {
            // Reported below.
        }
        module.getLang().send(sender, EnchantMessages.INVALID_LEVEL, "input", input,
                "max", String.valueOf(enchant.maxLevel()));
        return Optional.empty();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> options = new ArrayList<>();
        boolean staff = sender.hasPermission(EnchantModule.ADMIN_PERMISSION);
        if (args.length == 1) {
            options.add("list");
            if (staff) {
                options.addAll(STAFF);
            }
        } else if (staff && args.length == 2 && (args[0].equalsIgnoreCase("apply") || args[0].equalsIgnoreCase("remove"))) {
            module.list().forEach(enchant -> options.add(enchant.id()));
        } else if (staff && args.length == 2 && args[0].equalsIgnoreCase("give")) {
            options.addAll(VisiblePlayers.names(sender, args[1]));
        } else if (staff && args.length == 3 && args[0].equalsIgnoreCase("give")) {
            module.list().forEach(enchant -> options.add(enchant.id()));
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
