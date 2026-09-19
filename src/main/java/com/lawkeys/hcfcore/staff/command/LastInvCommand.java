package com.lawkeys.hcfcore.staff.command;

import com.lawkeys.hcfcore.command.VisiblePlayers;
import com.lawkeys.hcfcore.staff.DeathSnapshot;
import com.lawkeys.hcfcore.staff.StaffMessages;
import com.lawkeys.hcfcore.staff.StaffModule;
import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * {@code /lastinv} - what a player was carrying when they died.
 *
 * <p>Always read-only: a snapshot is a record of something that already happened,
 * and there is nothing an edit to it could mean. That is why it needs no
 * equivalent of {@code /invsee}'s edit permission.
 *
 * <p>The archive is read off the main thread and the window opened back on it -
 * unlike every other lookup in this plugin, which answers from a cache. The
 * archive is deliberately not cached; see {@code LastInventoryStore} for why.
 */
public final class LastInvCommand implements TabExecutor {

    /** Rows of a double chest, which is what a 41-slot snapshot needs to fit in. */
    private static final int VIEW_SIZE = 54;

    private final StaffModule module;

    public LastInvCommand(StaffModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player viewer)) {
            module.getLang().send(sender, "general.player-only");
            return true;
        }
        if (!sender.hasPermission(StaffModule.STAFF_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return true;
        }
        if (!module.getSettings().enabled() || !module.getSettings().lastInventory().enabled()) {
            module.getLang().send(sender, StaffMessages.DISABLED);
            return true;
        }
        if (args.length == 0) {
            module.getLang().send(sender, StaffMessages.USAGE,
                    "usage", "/" + label + " <player> [number]");
            return true;
        }
        // Deaths outlive sessions, so the target is usually offline - but the lookup
        // must never be getOfflinePlayer(String), whose javadoc warns of a blocking
        // web request for a name the server does not know. On the main thread, for a
        // typo. The cached lookup never makes one; a name it does not know has no
        // deaths recorded here either.
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[0]);
        if (target == null) {
            module.getLang().send(sender, StaffMessages.LASTINV_EMPTY, "player", args[0]);
            return true;
        }
        int index = 0;
        if (args.length > 1) {
            try {
                index = Math.max(1, Integer.parseInt(args[1])) - 1;
            } catch (NumberFormatException e) {
                module.getLang().send(sender, StaffMessages.INVALID_NUMBER, "input", args[1]);
                return true;
            }
        }
        lookup(viewer, target.getUniqueId(), args[0], index, args.length > 1);
        return true;
    }

    private void lookup(Player viewer, UUID targetId, String targetName, int index, boolean open) {
        int keep = module.getSettings().lastInventory().keep();
        Bukkit.getScheduler().runTaskAsynchronously(module.getPlugin(), () -> {
            List<DeathSnapshot> snapshots;
            try {
                snapshots = module.getLastInventories().recent(targetId, keep);
            } catch (Exception e) {
                module.getPlugin().getLogger().log(Level.WARNING,
                        "Could not read the death archive for " + targetName, e);
                Bukkit.getScheduler().runTask(module.getPlugin(),
                        () -> module.getLang().send(viewer, StaffMessages.LASTINV_LOOKUP_FAILED));
                return;
            }
            // Back on the main thread: opening a window is not an async operation.
            Bukkit.getScheduler().runTask(module.getPlugin(),
                    () -> present(viewer, snapshots, targetName, index, open));
        });
    }

    private void present(Player viewer, List<DeathSnapshot> snapshots, String targetName,
                         int index, boolean open) {
        if (!viewer.isOnline()) {
            return;
        }
        if (snapshots.isEmpty()) {
            module.getLang().send(viewer, StaffMessages.LASTINV_EMPTY, "player", targetName);
            return;
        }
        long now = System.currentTimeMillis();
        if (!open) {
            module.getLang().send(viewer, StaffMessages.LASTINV_HEADER,
                    "player", targetName, "count", String.valueOf(snapshots.size()));
            for (int i = 0; i < snapshots.size(); i++) {
                module.getLang().send(viewer, StaffMessages.LASTINV_ENTRY,
                        "index", String.valueOf(i + 1),
                        "when", ago(snapshots.get(i), now));
            }
            return;
        }
        if (index >= snapshots.size()) {
            module.getLang().send(viewer, StaffMessages.LASTINV_OUT_OF_RANGE,
                    "index", String.valueOf(index + 1), "player", targetName);
            return;
        }
        DeathSnapshot snapshot = snapshots.get(index);
        ItemStack[] items;
        try {
            items = ItemStack.deserializeItemsFromBytes(snapshot.contents());
        } catch (RuntimeException e) {
            module.getPlugin().getLogger().log(Level.WARNING,
                    "Could not read a death snapshot for " + targetName, e);
            module.getLang().send(viewer, StaffMessages.LASTINV_UNREADABLE);
            return;
        }
        Inventory view = Bukkit.createInventory(null, VIEW_SIZE,
                com.lawkeys.hcfcore.theme.MenuStyle.title(module.getLang().get(
                        StaffMessages.LASTINV_VIEW_TITLE,
                        "player", targetName, "index", String.valueOf(index + 1))));
        for (int slot = 0; slot < items.length && slot < VIEW_SIZE; slot++) {
            view.setItem(slot, items[slot]);
        }
        viewer.openInventory(view);
        // Read-only for everybody: a snapshot records what already happened, so there
        // is nothing an edit could mean. No edit permission applies here.
        module.getInvsee().open(viewer.getUniqueId(), viewer.getUniqueId(), false);
        module.getLang().send(viewer, StaffMessages.LASTINV_OPENED,
                "player", targetName, "when", ago(snapshot, now));
    }

    private static String ago(DeathSnapshot snapshot, long now) {
        return Durations.format(Math.max(0L, (now - snapshot.diedAt()) / 1000L));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(StaffModule.STAFF_PERMISSION)) {
            return List.of();
        }
        return args.length == 1 ? VisiblePlayers.names(sender, args[0]) : List.of();
    }
}
