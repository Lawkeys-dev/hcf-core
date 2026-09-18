package com.lawkeys.hcfcore.staff.command;

import com.lawkeys.hcfcore.command.VisiblePlayers;
import com.lawkeys.hcfcore.staff.FreezeManager;
import com.lawkeys.hcfcore.staff.StaffMessages;
import com.lawkeys.hcfcore.staff.StaffModule;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * {@code /freeze} - holds a player for a check, and lifts the ban that follows
 * running from one.
 *
 * <p>A dedicated command rather than the team {@code focus} mechanism that
 * FEATURES.md wondered about: focus is a gameplay marker a team puts on an enemy,
 * and a moderation hold is not the same act by anybody's reading.
 */
public final class FreezeCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("list", "unban");

    /** One line of {@code /freeze list}. */
    private record Held(String player, String holder) {
    }

    private final StaffModule module;

    public FreezeCommand(StaffModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(StaffModule.STAFF_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return true;
        }
        if (!module.getSettings().enabled() || !module.getSettings().freeze().enabled()) {
            module.getLang().send(sender, StaffMessages.DISABLED);
            return true;
        }
        // Bans are loaded data: lifting one before they land would be undone.
        if (module.getStartup().refuseCommand(sender)) {
            return true;
        }
        if (args.length == 0) {
            module.getLang().send(sender, StaffMessages.USAGE, "usage", "/" + label + " <player>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> list(sender);
            case "unban" -> unban(sender, args, label);
            default -> toggle(sender, args[0]);
        }
        return true;
    }

    private void toggle(CommandSender sender, String name) {
        Player target = module.requireOnline(sender, name);
        if (target == null) {
            return;
        }
        FreezeManager freezes = module.getFreezes();
        UUID targetId = target.getUniqueId();
        if (freezes.isFrozen(targetId)) {
            freezes.unfreeze(targetId);
            module.getLang().send(sender, StaffMessages.FREEZE_RELEASED, "player", target.getName());
            module.getLang().send(target, StaffMessages.FREEZE_TARGET_RELEASED);
            return;
        }
        if (!freezes.freeze(targetId, sender.getName(), System.currentTimeMillis())) {
            // Somebody else got there first; their name is the one the player was told.
            module.getLang().send(sender, StaffMessages.FREEZE_ALREADY_HELD,
                    "player", target.getName(),
                    "holder", freezes.get(targetId).map(FreezeManager.Freeze::frozenBy).orElse("?"));
            return;
        }
        module.getLang().send(sender, StaffMessages.FREEZE_APPLIED, "player", target.getName());
        module.getLang().send(target, StaffMessages.FREEZE_TARGET_FROZEN, "player", sender.getName());
    }

    private void list(CommandSender sender) {
        FreezeManager freezes = module.getFreezes();
        List<Held> held = new ArrayList<>();
        for (UUID playerId : freezes.frozenPlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                held.add(new Held(player.getName(),
                        freezes.get(playerId).map(FreezeManager.Freeze::frozenBy).orElse("?")));
            }
        }
        if (held.isEmpty()) {
            module.getLang().send(sender, StaffMessages.FREEZE_LIST_EMPTY);
            return;
        }
        held.sort(Comparator.comparing(Held::player, String.CASE_INSENSITIVE_ORDER));
        module.getLang().send(sender, StaffMessages.FREEZE_LIST_HEADER,
                "count", String.valueOf(held.size()));
        for (Held line : held) {
            module.getLang().send(sender, StaffMessages.FREEZE_LIST_ENTRY,
                    "player", line.player(), "holder", line.holder());
        }
    }

    /**
     * Lifts the moderation ban somebody earned by running from a check.
     *
     * <p>Takes a name rather than requiring them online - they cannot be online, that
     * is the point - so the name is resolved through the server's name cache, which
     * never waits on the network. Somebody banned for leaving a check was online
     * moments before it, so they are always in it; a name it does not know cannot be
     * banned here.
     */
    private void unban(CommandSender sender, String[] args, String label) {
        if (args.length < 2) {
            module.getLang().send(sender, StaffMessages.USAGE, "usage", "/" + label + " unban <player>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null || !module.getBans().lift(target.getUniqueId())) {
            module.getLang().send(sender, StaffMessages.BAN_NOT_BANNED, "player", args[1]);
            return;
        }
        module.flushBansSoon();
        module.getLang().send(sender, StaffMessages.BAN_LIFTED, "player", args[1]);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(StaffModule.STAFF_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>(VisiblePlayers.names(sender, args[0]));
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    options.add(sub);
                }
            }
            return options;
        }
        return List.of();
    }
}
