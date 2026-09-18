package com.lawkeys.hcfcore.general.command;

import com.lawkeys.hcfcore.chat.ChatModule;
import com.lawkeys.hcfcore.command.VisiblePlayers;
import com.lawkeys.hcfcore.general.GeneralMessages;
import com.lawkeys.hcfcore.general.GeneralModule;
import com.lawkeys.hcfcore.general.PrivateMessages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** {@code /msg}, {@code /reply}, {@code /togglepm} and {@code /ignore}. */
public final class MessageCommand implements TabExecutor {

    private final GeneralModule module;

    public MessageCommand(GeneralModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return true;
        }
        if (!module.getSettings().enabled() || !module.getSettings().privateMessagesEnabled()) {
            module.getLang().send(sender, GeneralMessages.DISABLED);
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "msg" -> message(player, args, label);
            case "reply" -> reply(player, args, label);
            case "togglepm" -> togglePm(player);
            case "ignore" -> ignore(player, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void message(Player player, String[] args, String label) {
        if (args.length < 2) {
            module.getLang().send(player, GeneralMessages.USAGE,
                    "usage", "/" + label + " <player> <message>");
            return;
        }
        // Somebody the sender cannot see is answered like somebody offline, or a
        // private message would be the way to find vanished staff.
        Player target = VisiblePlayers.find(player, args[0]).orElse(null);
        if (target == null) {
            module.getLang().send(player, GeneralMessages.PLAYER_NOT_FOUND, "player", args[0]);
            return;
        }
        send(player, target, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
    }

    private void reply(Player player, String[] args, String label) {
        if (args.length == 0) {
            module.getLang().send(player, GeneralMessages.USAGE, "usage", "/" + label + " <message>");
            return;
        }
        UUID targetId = module.getMessages().replyTarget(player.getUniqueId()).orElse(null);
        Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
        // Vanished since the last message: answered as if they had left, like /msg.
        if (target == null || !VisiblePlayers.canSee(player, target)) {
            module.getLang().send(player, GeneralMessages.MESSAGE_NOBODY);
            return;
        }
        send(player, target, String.join(" ", args));
    }

    /**
     * Delivers one message, or says why it could not be.
     *
     * <p>A sender who is being ignored is told the message was blocked rather than
     * being let to believe it arrived. Some servers hide that; telling them is the
     * honest choice, and it stops somebody repeating themselves into a void.
     *
     * <p>What was typed is escaped unless the sender may colour chat
     * ({@link ChatModule#COLOR_PERMISSION}): the templates are coloured after their
     * placeholders are filled, so any player sent colours, obfuscated text or a fake
     * "[Admin]" to another - the rule public chat already follows (found in the
     * command review, 15/09/2026).
     */
    private void send(Player from, Player to, String typed) {
        String message = ChatModule.typedText(from, typed);
        PrivateMessages.Delivery delivery =
                module.getMessages().canSend(from.getUniqueId(), to.getUniqueId());
        switch (delivery) {
            case SENDER_OFF -> {
                module.getLang().send(from, GeneralMessages.MESSAGE_BLOCKED);
                return;
            }
            case RECIPIENT_OFF, IGNORED -> {
                module.getLang().send(from, GeneralMessages.MESSAGE_THEY_BLOCKED,
                        "player", to.getName());
                return;
            }
            case OK -> {
                // fall through
            }
        }
        module.getLang().send(from, GeneralMessages.MESSAGE_SENT,
                "player", to.getName(), "message", message);
        module.getLang().send(to, GeneralMessages.MESSAGE_RECEIVED,
                "player", from.getName(), "message", message);
        module.getMessages().delivered(from.getUniqueId(), to.getUniqueId());
    }

    private void togglePm(Player player) {
        boolean on = module.getMessages().toggleMessages(player.getUniqueId());
        module.messagesToggled(player, on);
        module.getLang().send(player,
                on ? GeneralMessages.MESSAGE_TOGGLED_ON : GeneralMessages.MESSAGE_TOGGLED_OFF);
    }

    private void ignore(Player player, String[] args) {
        if (args.length == 0) {
            List<String> names = new ArrayList<>();
            for (UUID ignoredId : module.getMessages().ignoredBy(player.getUniqueId())) {
                names.add(Bukkit.getOfflinePlayer(ignoredId).getName());
            }
            if (names.isEmpty()) {
                module.getLang().send(player, GeneralMessages.IGNORE_LIST_EMPTY);
            } else {
                module.getLang().send(player, GeneralMessages.IGNORE_LIST,
                        "players", String.join(", ", names));
            }
            return;
        }
        // Ignoring somebody offline has to work by name, but never through
        // getOfflinePlayer(String): its javadoc warns of a blocking web request for
        // a name the server does not know. The cached lookup never makes one.
        var target = Bukkit.getOfflinePlayerIfCached(args[0]);
        if (target == null) {
            module.getLang().send(player, GeneralMessages.IGNORE_UNKNOWN, "player", args[0]);
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            module.getLang().send(player, GeneralMessages.IGNORE_SELF);
            return;
        }
        boolean ignored = module.getMessages().toggleIgnore(player.getUniqueId(), target.getUniqueId());
        module.getLang().send(player,
                ignored ? GeneralMessages.IGNORE_ADDED : GeneralMessages.IGNORE_REMOVED,
                "player", Objects.requireNonNullElse(target.getName(), args[0]));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        // /reply takes a message, not a name; /togglepm takes nothing.
        if (args.length != 1 || command.getName().equalsIgnoreCase("togglepm")
                || command.getName().equalsIgnoreCase("reply")) {
            return List.of();
        }
        // Only names the sender can see: completion would otherwise list vanished staff.
        return VisiblePlayers.names(sender, args[0]);
    }
}
