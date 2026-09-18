package com.lawkeys.hcfcore.kit.command;

import com.lawkeys.hcfcore.kit.Ability;
import com.lawkeys.hcfcore.kit.Kit;
import com.lawkeys.hcfcore.kit.KitLayout;
import com.lawkeys.hcfcore.kit.KitMessages;
import com.lawkeys.hcfcore.kit.KitModule;
import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** {@code /kit} - take a kit, and the staff verbs that create them. */
public final class KitCommand implements TabExecutor {

    public static final String ADMIN_PERMISSION = "hcfcore.kit.admin";

    private static final List<String> ADMIN_SUBCOMMANDS =
            List.of("create", "delete", "give", "resetcooldown", "ability");

    private final KitModule module;

    public KitCommand(KitModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!module.getSettings().enabled()) {
            module.getLang().send(sender, KitMessages.DISABLED);
            return true;
        }
        // Kits are loaded data: creating one before they land would be erased.
        if (module.getStartup().refuseCommand(sender)) {
            return true;
        }
        if (args.length == 0) {
            list(sender);
            return true;
        }
        String first = args[0].toLowerCase(Locale.ROOT);
        if (first.equals("layout")) {
            layout(sender, args, label);
            return true;
        }
        if (ADMIN_SUBCOMMANDS.contains(first)) {
            if (!sender.hasPermission(ADMIN_PERMISSION)) {
                module.getLang().send(sender, "general.no-permission");
                return true;
            }
            switch (first) {
                case "create" -> create(sender, args, label);
                case "delete" -> delete(sender, args, label);
                case "give" -> give(sender, args, label);
                case "resetcooldown" -> resetCooldown(sender, args, label);
                case "ability" -> ability(sender, args, label);
                default -> module.getLang().send(sender, "general.unknown-command", "label", label);
            }
            return true;
        }
        take(sender, first);
        return true;
    }

    private void list(CommandSender sender) {
        List<Kit> kits = module.getManager().all();
        if (kits.isEmpty()) {
            module.getLang().send(sender, KitMessages.LIST_EMPTY);
            return;
        }
        module.getLang().send(sender, KitMessages.LIST_HEADER, "count", String.valueOf(kits.size()));
        for (Kit kit : kits) {
            if (!kit.isAllowed(sender::hasPermission)) {
                continue;
            }
            long wait = sender instanceof Player player
                    ? module.getManager().remainingCooldown(player.getUniqueId(), kit.id())
                    : 0L;
            if (wait > 0) {
                module.getLang().send(sender, KitMessages.LIST_ENTRY_COOLDOWN,
                        "kit", kit.displayName(), "id", kit.id(), "time", Durations.format(wait));
            } else {
                module.getLang().send(sender, KitMessages.LIST_ENTRY,
                        "kit", kit.displayName(), "id", kit.id());
            }
        }
    }

    private void take(CommandSender sender, String id) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        Kit kit = module.getManager().get(id).orElse(null);
        if (kit == null) {
            module.getLang().send(sender, KitMessages.UNKNOWN, "kit", id);
            return;
        }
        if (!kit.isAllowed(player::hasPermission)) {
            module.getLang().send(sender, KitMessages.NO_PERMISSION, "kit", kit.displayName());
            return;
        }
        long wait = module.getManager().remainingCooldown(player.getUniqueId(), kit.id());
        if (wait > 0) {
            module.getLang().send(sender, KitMessages.ON_COOLDOWN,
                    "kit", kit.displayName(), "time", Durations.format(wait));
            return;
        }
        module.give(player, kit);
        module.getManager().markUsed(player.getUniqueId(), kit);
        module.flushSoon();
        module.getLang().send(sender, KitMessages.RECEIVED, "kit", kit.displayName());
    }

    /**
     * {@code /kit layout <kit> [reset]}: the layout editor, for any kit the player may
     * take. {@code reset} forgets their layout without opening anything.
     */
    private void layout(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        if (!module.getSettings().layoutEditor()) {
            module.getLang().send(sender, KitMessages.LAYOUT_DISABLED);
            return;
        }
        if (args.length < 2) {
            module.getLang().send(sender, KitMessages.USAGE, "usage", "/" + label + " layout <kit> [reset]");
            return;
        }
        Kit kit = module.getManager().get(args[1]).orElse(null);
        if (kit == null) {
            module.getLang().send(sender, KitMessages.UNKNOWN, "kit", args[1]);
            return;
        }
        if (!kit.isAllowed(player::hasPermission)) {
            module.getLang().send(sender, KitMessages.NO_PERMISSION, "kit", kit.displayName());
            return;
        }
        if (args.length > 2 && args[2].equalsIgnoreCase("reset")) {
            module.getManager().setLayout(player.getUniqueId(), kit.id(), KitLayout.NONE);
            module.flushSoon();
            module.getLang().send(sender, KitMessages.LAYOUT_RESET, "kit", kit.displayName());
            return;
        }
        module.openLayoutEditor(player, kit);
    }

    /**
     * Saves the staff member's own inventory as a kit.
     *
     * <p>This is how kits are made, rather than by writing item definitions into
     * YAML: a loadout is enchanted armour, brewed potions and named items, and
     * describing that by hand is a job nobody finishes correctly.
     */
    private void create(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        if (args.length < 2) {
            module.getLang().send(sender, KitMessages.USAGE,
                    "usage", "/" + label + " create <id> [cooldown-seconds] [permission]");
            return;
        }
        long cooldown = 0L;
        if (args.length > 2) {
            try {
                cooldown = Math.max(0L, Long.parseLong(args[2]));
                if (cooldown > Durations.MAX_SECONDS) {
                    throw new NumberFormatException(args[2]);
                }
            } catch (NumberFormatException e) {
                module.getLang().send(sender, KitMessages.INVALID_NUMBER, "input", args[2]);
                return;
            }
        }
        byte[] contents = ItemStack.serializeItemsAsBytes(player.getInventory().getContents());
        Kit kit = new Kit(args[1], args[1], args.length > 3 ? args[3] : null, cooldown, contents);
        module.getManager().save(kit);
        module.flushSoon();
        module.getLang().send(sender, KitMessages.CREATED, "kit", kit.id());
    }

    private void delete(CommandSender sender, String[] args, String label) {
        if (args.length < 2) {
            module.getLang().send(sender, KitMessages.USAGE, "usage", "/" + label + " delete <id>");
            return;
        }
        if (!module.getManager().delete(args[1])) {
            module.getLang().send(sender, KitMessages.UNKNOWN, "kit", args[1]);
            return;
        }
        module.flushSoon();
        module.getLang().send(sender, KitMessages.DELETED, "kit", args[1]);
    }

    private void give(CommandSender sender, String[] args, String label) {
        if (args.length < 3) {
            module.getLang().send(sender, KitMessages.USAGE,
                    "usage", "/" + label + " give <player> <kit>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            module.getLang().send(sender, KitMessages.PLAYER_NOT_FOUND, "player", args[1]);
            return;
        }
        Kit kit = module.getManager().get(args[2]).orElse(null);
        if (kit == null) {
            module.getLang().send(sender, KitMessages.UNKNOWN, "kit", args[2]);
            return;
        }
        // Given by staff, so no cooldown is started: that is the point of giving one.
        module.give(target, kit);
        module.getLang().send(sender, KitMessages.GIVEN,
                "kit", kit.displayName(), "player", target.getName());
        module.getLang().send(target, KitMessages.RECEIVED, "kit", kit.displayName());
    }

    private void resetCooldown(CommandSender sender, String[] args, String label) {
        if (args.length < 2) {
            module.getLang().send(sender, KitMessages.USAGE,
                    "usage", "/" + label + " resetcooldown <player> [kit]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            module.getLang().send(sender, KitMessages.PLAYER_NOT_FOUND, "player", args[1]);
            return;
        }
        module.getManager().clearCooldowns(target.getUniqueId(), args.length > 2 ? args[2] : null);
        module.flushSoon();
        module.getLang().send(sender, KitMessages.COOLDOWN_CLEARED, "player", target.getName());
    }

    /** Hands out a partner item, which is otherwise only obtainable from a reward command. */
    private void ability(CommandSender sender, String[] args, String label) {
        if (args.length < 3) {
            module.getLang().send(sender, KitMessages.USAGE,
                    "usage", "/" + label + " ability <player> <ability> [amount]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            module.getLang().send(sender, KitMessages.PLAYER_NOT_FOUND, "player", args[1]);
            return;
        }
        Ability ability = module.getSettings().ability(args[2]).orElse(null);
        if (ability == null) {
            module.getLang().send(sender, KitMessages.UNKNOWN, "kit", args[2]);
            return;
        }
        int amount = 1;
        if (args.length > 3) {
            try {
                amount = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException e) {
                module.getLang().send(sender, KitMessages.INVALID_NUMBER, "input", args[3]);
                return;
            }
        }
        ItemStack item = module.buildAbilityItem(ability, amount);
        if (item == null) {
            module.getLang().send(sender, KitMessages.DISABLED);
            return;
        }
        for (ItemStack leftover : target.getInventory().addItem(item).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }
        module.getLang().send(sender, KitMessages.GIVEN,
                "kit", ability.id(), "player", target.getName());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(module.getManager() == null ? List.of() : module.getManager().ids());
            if (module.getSettings().layoutEditor()) {
                options.add("layout");
            }
            if (sender.hasPermission(ADMIN_PERMISSION)) {
                options.addAll(ADMIN_SUBCOMMANDS);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("layout")) {
            for (Kit kit : module.getManager().all()) {
                if (kit.isAllowed(sender::hasPermission)) {
                    options.add(kit.id());
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("layout")) {
            options.add("reset");
        } else if (args.length == 2 && sender.hasPermission(ADMIN_PERMISSION)) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "delete" -> options.addAll(module.getManager().ids());
                case "give", "resetcooldown", "ability" -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        options.add(player.getName());
                    }
                }
                default -> {
                }
            }
        } else if (args.length == 3 && sender.hasPermission(ADMIN_PERMISSION)) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "give", "resetcooldown" -> options.addAll(module.getManager().ids());
                case "ability" -> module.getSettings().abilities().forEach(a -> options.add(a.id()));
                default -> {
                }
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
