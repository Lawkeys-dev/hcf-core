package com.lawkeys.hcfcore.redeem.command;

import com.lawkeys.hcfcore.command.KnownPlayers;
import com.lawkeys.hcfcore.redeem.RedeemCode;
import com.lawkeys.hcfcore.redeem.RedeemCodes;
import com.lawkeys.hcfcore.redeem.RedeemMessages;
import com.lawkeys.hcfcore.redeem.RedeemModule;
import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * {@code /redeemadmin} - create, change, inspect, reset and delete codes - and
 * {@code /resetredeem <code> [player]}, the name FEATURES.md gives the reset.
 */
public final class RedeemAdminCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS =
            List.of("create", "addcommand", "removecommand", "setuses", "info", "list", "reset", "delete");

    private final RedeemModule module;

    public RedeemAdminCommand(RedeemModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(RedeemModule.ADMIN_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return true;
        }
        if (module.getStartup().refuseCommand(sender)) {
            return true;
        }
        if (command.getName().equalsIgnoreCase("resetredeem")) {
            reset(sender, args, 0, label);
            module.flushSoon();
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> create(sender, args, label);
            case "addcommand" -> addCommand(sender, args, label);
            case "removecommand" -> removeCommand(sender, args, label);
            case "setuses" -> setUses(sender, args, label);
            case "info" -> info(sender, args, label);
            case "list" -> list(sender);
            case "reset" -> reset(sender, args, 1, label + " reset");
            case "delete" -> delete(sender, args, label);
            default -> module.getLang().send(sender, RedeemMessages.USAGE, "usage",
                    "/" + label + " <" + String.join("|", SUBCOMMANDS) + ">");
        }
        if (!sub.equals("info") && !sub.equals("list")) {
            module.flushSoon();
        }
        return true;
    }

    private RedeemCodes codes() {
        return module.getCodes();
    }

    /** {@code create <code> <max-uses|unlimited> <command...>} - a code always starts with its first reward. */
    private void create(CommandSender sender, String[] args, String label) {
        if (args.length < 4) {
            module.getLang().send(sender, RedeemMessages.USAGE,
                    "usage", "/" + label + " create <code> <max-uses|unlimited> <command with %player%>");
            return;
        }
        if (!RedeemCodes.isValid(args[1])) {
            module.getLang().send(sender, RedeemMessages.INVALID, "code", args[1]);
            return;
        }
        OptionalInt uses = parseUses(sender, args[2]);
        if (uses.isEmpty()) {
            return;
        }
        String firstCommand = rest(args, 3);
        if (!codes().create(args[1], uses.getAsInt(), sender.getName(), firstCommand)) {
            module.getLang().send(sender, RedeemMessages.EXISTS, "code", args[1]);
            return;
        }
        module.getLang().send(sender, RedeemMessages.CREATED, "code", args[1], "uses", usesText(uses.getAsInt()));
    }

    private void addCommand(CommandSender sender, String[] args, String label) {
        if (args.length < 3) {
            module.getLang().send(sender, RedeemMessages.USAGE,
                    "usage", "/" + label + " addcommand <code> <command with %player%>");
            return;
        }
        if (!codes().addCommand(args[1], rest(args, 2))) {
            module.getLang().send(sender, RedeemMessages.NO_SUCH_CODE, "code", args[1]);
            return;
        }
        module.getLang().send(sender, RedeemMessages.COMMAND_ADDED, "code", args[1]);
    }

    private void removeCommand(CommandSender sender, String[] args, String label) {
        if (args.length != 3) {
            module.getLang().send(sender, RedeemMessages.USAGE,
                    "usage", "/" + label + " removecommand <code> <number from info>");
            return;
        }
        int index;
        try {
            index = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            index = -1;
        }
        if (!codes().removeCommand(args[1], index)) {
            module.getLang().send(sender, codes().get(args[1]).isEmpty()
                    ? RedeemMessages.NO_SUCH_CODE : RedeemMessages.NO_SUCH_COMMAND, "code", args[1], "number", args[2]);
            return;
        }
        module.getLang().send(sender, RedeemMessages.COMMAND_REMOVED, "code", args[1], "number", args[2]);
    }

    private void setUses(CommandSender sender, String[] args, String label) {
        if (args.length != 3) {
            module.getLang().send(sender, RedeemMessages.USAGE,
                    "usage", "/" + label + " setuses <code> <max-uses|unlimited>");
            return;
        }
        OptionalInt uses = parseUses(sender, args[2]);
        if (uses.isEmpty()) {
            return;
        }
        if (!codes().setMaxUses(args[1], uses.getAsInt())) {
            module.getLang().send(sender, RedeemMessages.NO_SUCH_CODE, "code", args[1]);
            return;
        }
        module.getLang().send(sender, RedeemMessages.USES_SET, "code", args[1], "uses", usesText(uses.getAsInt()));
    }

    private void info(CommandSender sender, String[] args, String label) {
        if (args.length != 2) {
            module.getLang().send(sender, RedeemMessages.USAGE, "usage", "/" + label + " info <code>");
            return;
        }
        RedeemCode code = codes().get(args[1]).orElse(null);
        if (code == null) {
            module.getLang().send(sender, RedeemMessages.NO_SUCH_CODE, "code", args[1]);
            return;
        }
        module.getLang().send(sender, RedeemMessages.INFO_HEADER, "code", code.code(),
                "used", String.valueOf(code.uses()), "uses", usesText(code.maxUses()),
                "by", code.createdBy(),
                "age", Durations.format(Math.max(0L, (System.currentTimeMillis() - code.createdAt()) / 1000L)));
        if (code.commands().isEmpty()) {
            module.getLang().send(sender, RedeemMessages.INFO_NO_COMMANDS);
        }
        for (int i = 0; i < code.commands().size(); i++) {
            module.getLang().send(sender, RedeemMessages.INFO_COMMAND,
                    "number", String.valueOf(i + 1), "command", code.commands().get(i));
        }
    }

    private void list(CommandSender sender) {
        List<RedeemCode> all = codes().list();
        if (all.isEmpty()) {
            module.getLang().send(sender, RedeemMessages.LIST_EMPTY);
            return;
        }
        module.getLang().send(sender, RedeemMessages.LIST_HEADER, "count", String.valueOf(all.size()));
        for (RedeemCode code : all) {
            module.getLang().send(sender, RedeemMessages.LIST_ENTRY, "code", code.code(),
                    "used", String.valueOf(code.uses()), "uses", usesText(code.maxUses()));
        }
    }

    /** {@code reset <code> [player]}: everybody's redemptions, or one player's. */
    private void reset(CommandSender sender, String[] args, int from, String label) {
        if (args.length <= from) {
            module.getLang().send(sender, RedeemMessages.USAGE, "usage", "/" + label + " <code> [player]");
            return;
        }
        String code = args[from];
        if (codes().get(code).isEmpty()) {
            module.getLang().send(sender, RedeemMessages.NO_SUCH_CODE, "code", code);
            return;
        }
        if (args.length <= from + 1) {
            codes().reset(code, null);
            module.getLang().send(sender, RedeemMessages.RESET_ALL, "code", code);
            return;
        }
        String name = args[from + 1];
        UUID playerId = KnownPlayers.idOf(name).orElse(null);
        if (playerId == null) {
            module.getLang().send(sender, RedeemMessages.UNKNOWN_PLAYER, "player", name);
            return;
        }
        if (!codes().reset(code, playerId)) {
            module.getLang().send(sender, RedeemMessages.NOT_REDEEMED_BY, "code", code, "player", name);
            return;
        }
        module.getLang().send(sender, RedeemMessages.RESET_PLAYER, "code", code, "player", name);
    }

    private void delete(CommandSender sender, String[] args, String label) {
        if (args.length != 2) {
            module.getLang().send(sender, RedeemMessages.USAGE, "usage", "/" + label + " delete <code>");
            return;
        }
        if (!codes().delete(args[1])) {
            module.getLang().send(sender, RedeemMessages.NO_SUCH_CODE, "code", args[1]);
            return;
        }
        module.getLang().send(sender, RedeemMessages.DELETED, "code", args[1]);
    }

    private OptionalInt parseUses(CommandSender sender, String input) {
        if (input.equalsIgnoreCase("unlimited")) {
            return OptionalInt.of(0);
        }
        try {
            int uses = Integer.parseInt(input);
            if (uses > 0) {
                return OptionalInt.of(uses);
            }
        } catch (NumberFormatException ignored) {
            // Reported below.
        }
        module.getLang().send(sender, RedeemMessages.INVALID_USES, "input", input);
        return OptionalInt.empty();
    }

    private String usesText(int maxUses) {
        return maxUses == 0 ? module.getLang().get(RedeemMessages.UNLIMITED) : String.valueOf(maxUses);
    }

    private static String rest(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(RedeemModule.ADMIN_PERMISSION)) {
            return List.of();
        }
        boolean reset = command.getName().equalsIgnoreCase("resetredeem");
        List<String> options = new ArrayList<>();
        if (!reset && args.length == 1) {
            options.addAll(SUBCOMMANDS);
        } else if ((reset && args.length == 1) || (!reset && args.length == 2 && !args[0].equalsIgnoreCase("create")
                && !args[0].equalsIgnoreCase("list"))) {
            codes().list().forEach(code -> options.add(code.code()));
        } else if (!reset && args.length == 3 && (args[0].equalsIgnoreCase("create")
                || args[0].equalsIgnoreCase("setuses"))) {
            options.add("unlimited");
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
