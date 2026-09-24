package com.lawkeys.hcfcore.lives.command;

import com.lawkeys.hcfcore.command.VisiblePlayers;
import com.lawkeys.hcfcore.lives.Lives;
import com.lawkeys.hcfcore.lives.LivesMessages;
import com.lawkeys.hcfcore.lives.LivesModule;
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
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /lives} - your lives, somebody else's ({@code check}), {@code send},
 * {@code revive}, and for staff {@code give|take|set} - and {@code /revive <player>},
 * the shortcut.
 */
public final class LivesCommand implements TabExecutor {

    private static final List<String> PLAYER_SUBCOMMANDS = List.of("check", "send", "revive");
    private static final List<String> ADMIN_SUBCOMMANDS = List.of("give", "take", "set");

    private final LivesModule module;

    public LivesCommand(LivesModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    /** A name, resolved without ever waiting on the network (see CONTRIBUTING.md). */
    private record Target(UUID id, String name) {
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!module.isEnabled()) {
            module.getLang().send(sender, LivesMessages.DISABLED);
            return true;
        }
        if (module.getStartup().refuseCommand(sender)) {
            return true;
        }
        if (command.getName().equalsIgnoreCase("revive")) {
            revive(sender, args, 0, label);
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player player) {
                module.getLang().send(player, LivesMessages.OWN,
                        "lives", String.valueOf(lives().get(player.getUniqueId())));
            } else {
                usage(sender, label);
            }
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "check" -> check(sender, args, label);
            case "send" -> send(sender, args, label);
            case "revive" -> revive(sender, args, 1, label + " revive");
            case "give", "take", "set" -> admin(sender, args, label);
            default -> usage(sender, label);
        }
        return true;
    }

    private Lives lives() {
        return module.getLives();
    }

    private void check(CommandSender sender, String[] args, String label) {
        if (args.length != 2) {
            module.getLang().send(sender, LivesMessages.USAGE, "usage", "/" + label + " check <player>");
            return;
        }
        resolve(sender, args[1]).ifPresent(target -> module.getLang().send(sender, LivesMessages.OTHER,
                "player", target.name(), "lives", String.valueOf(lives().get(target.id()))));
    }

    private void send(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        if (!module.isSendAllowed()) {
            module.getLang().send(sender, LivesMessages.SEND_DISABLED);
            return;
        }
        if (args.length != 3) {
            module.getLang().send(sender, LivesMessages.USAGE, "usage", "/" + label + " send <player> <amount>");
            return;
        }
        Optional<Target> target = resolve(sender, args[1]);
        Optional<Integer> amount = amount(sender, args[2]);
        if (target.isEmpty() || amount.isEmpty()) {
            return;
        }
        switch (lives().transfer(player.getUniqueId(), target.get().id(), amount.get())) {
            case DONE -> {
                module.flushSoon();
                module.getLang().send(player, LivesMessages.SENT,
                        "amount", String.valueOf(amount.get()), "player", target.get().name());
                Player online = Bukkit.getPlayer(target.get().id());
                if (online != null) {
                    module.getLang().send(online, LivesMessages.RECEIVED,
                            "amount", String.valueOf(amount.get()), "player", player.getName());
                }
            }
            case NOT_ENOUGH -> module.getLang().send(player, LivesMessages.NOT_ENOUGH,
                    "lives", String.valueOf(lives().get(player.getUniqueId())));
            case SAME_PLAYER -> module.getLang().send(player, LivesMessages.SEND_SELF);
            case INVALID_AMOUNT -> module.getLang().send(player, LivesMessages.INVALID_AMOUNT, "input", args[2]);
        }
    }

    private void revive(CommandSender sender, String[] args, int from, String label) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        if (args.length != from + 1) {
            module.getLang().send(sender, LivesMessages.USAGE, "usage", "/" + label + " <player>");
            return;
        }
        resolve(sender, args[from]).ifPresent(target -> {
            switch (module.revive(player.getUniqueId(), target.id())) {
                case DONE -> module.getLang().send(player, LivesMessages.REVIVED, "player", target.name(),
                        "lives", String.valueOf(lives().get(player.getUniqueId())));
                case NOT_BANNED -> module.getLang().send(player, LivesMessages.NOT_BANNED, "player", target.name());
                case MAP_END -> module.getLang().send(player, LivesMessages.MAP_END, "player", target.name());
                case NO_LIFE -> module.getLang().send(player, LivesMessages.NO_LIFE);
            }
        });
    }

    /** {@code give|take|set <player> <amount>}, the "livesmanage" of FEATURES.md. */
    private void admin(CommandSender sender, String[] args, String label) {
        if (!sender.hasPermission(LivesModule.ADMIN_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return;
        }
        if (args.length != 3) {
            module.getLang().send(sender, LivesMessages.USAGE,
                    "usage", "/" + label + " " + args[0].toLowerCase(Locale.ROOT) + " <player> <amount>");
            return;
        }
        Optional<Target> target = resolve(sender, args[1]);
        Optional<Integer> amount = args[0].equalsIgnoreCase("set") ? amountOrZero(sender, args[2]) : amount(sender, args[2]);
        if (target.isEmpty() || amount.isEmpty()) {
            return;
        }
        UUID id = target.get().id();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> lives().add(id, amount.get());
            case "take" -> {
                if (!lives().take(id, amount.get())) {
                    lives().set(id, 0);
                }
            }
            default -> lives().set(id, amount.get());
        }
        module.flushSoon();
        module.getLang().send(sender, LivesMessages.ADMIN_SET,
                "player", target.get().name(), "lives", String.valueOf(lives().get(id)));
    }

    private Optional<Target> resolve(CommandSender sender, String name) {
        Optional<Player> online = VisiblePlayers.find(sender, name);
        if (online.isPresent()) {
            return Optional.of(new Target(online.get().getUniqueId(), online.get().getName()));
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            return Optional.of(new Target(cached.getUniqueId(), Objects.requireNonNullElse(cached.getName(), name)));
        }
        module.getLang().send(sender, LivesMessages.UNKNOWN_PLAYER, "player", name);
        return Optional.empty();
    }

    private Optional<Integer> amount(CommandSender sender, String input) {
        try {
            int amount = Integer.parseInt(input);
            if (amount > 0) {
                return Optional.of(amount);
            }
        } catch (NumberFormatException ignored) {
            // Reported below.
        }
        module.getLang().send(sender, LivesMessages.INVALID_AMOUNT, "input", input);
        return Optional.empty();
    }

    private Optional<Integer> amountOrZero(CommandSender sender, String input) {
        return "0".equals(input.trim()) ? Optional.of(0) : amount(sender, input);
    }

    private void usage(CommandSender sender, String label) {
        List<String> subs = new ArrayList<>(PLAYER_SUBCOMMANDS);
        if (sender.hasPermission(LivesModule.ADMIN_PERMISSION)) {
            subs.addAll(ADMIN_SUBCOMMANDS);
        }
        module.getLang().send(sender, LivesMessages.USAGE, "usage", "/" + label + " [" + String.join("|", subs) + "]");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        boolean revive = command.getName().equalsIgnoreCase("revive");
        List<String> options = new ArrayList<>();
        if (!revive && args.length == 1) {
            options.addAll(PLAYER_SUBCOMMANDS);
            if (sender.hasPermission(LivesModule.ADMIN_PERMISSION)) {
                options.addAll(ADMIN_SUBCOMMANDS);
            }
        } else if (revive ? args.length == 1 : args.length == 2 && args[0].equalsIgnoreCase("revive")) {
            // Whoever a revive is for is deathbanned, so never online: the banned are offered.
            options.addAll(module.revivableNames());
        } else if (!revive && args.length == 2) {
            options.addAll(VisiblePlayers.names(sender, ""));
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
