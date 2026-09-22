package com.lawkeys.hcfcore.claim.wand;

import com.lawkeys.hcfcore.claim.ClaimMessages;
import com.lawkeys.hcfcore.claim.ClaimSettings;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.util.LegacyText;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Who holds a claiming wand, what it is drawing, and the corners picked so far.
 *
 * <p>The traditional HCF gesture: left-click a block for the first corner, right-click
 * one for the second, sneak and left-click to confirm; dropping the wand gives up.
 * The corners are shown as columns of glass - sent to the holder alone, never placed
 * in the world - and put back as they were when the selection changes or ends.
 *
 * <p>Nothing here is stored: a selection lasts as long as its wand, and a player
 * leaving or dying drops both.
 */
public final class WandSessions {

    private final ClaimWand wand;
    private final LangManager lang;
    private final Supplier<ClaimSettings.WandRules> rules;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    private static final class Session {
        final WandTask task;
        Selection selection = Selection.empty();
        final List<Location> shown = new ArrayList<>();

        Session(WandTask task) {
            this.task = task;
        }
    }

    public WandSessions(ClaimWand wand, LangManager lang, Supplier<ClaimSettings.WandRules> rules) {
        this.wand = Objects.requireNonNull(wand, "wand");
        this.lang = Objects.requireNonNull(lang, "lang");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    public ClaimWand getWand() {
        return wand;
    }

    /**
     * Hands a player the wand for {@code task}, replacing whatever they were drawing.
     *
     * @return whether they have it - a full inventory has no room for one
     */
    public boolean give(Player player, WandTask task) {
        Objects.requireNonNull(task, "task");
        Session previous = sessions.remove(player.getUniqueId());
        if (previous != null) {
            hidePillars(player, previous);
        }
        if (!holdsWand(player)) {
            if (!player.getInventory().addItem(wand.create(rules.get())).isEmpty()) {
                lang.send(player, ClaimMessages.WAND_INVENTORY_FULL);
                return false;
            }
        }
        sessions.put(player.getUniqueId(), new Session(task));
        lang.send(player, ClaimMessages.WAND_GIVEN, "task", task.label());
        return true;
    }

    private boolean holdsWand(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (wand.isWand(item)) {
                return true;
            }
        }
        return false;
    }

    /** @param which 1 for the first corner, 2 for the second */
    public void select(Player player, int which, Block block) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            orphan(player);
            return;
        }
        hidePillars(player, session);
        session.selection = session.selection.with(which,
                new Selection.Corner(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
        lang.send(player, which == 1 ? ClaimMessages.WAND_FIRST : ClaimMessages.WAND_SECOND,
                "x", String.valueOf(block.getX()), "y", String.valueOf(block.getY()),
                "z", String.valueOf(block.getZ()));
        showPillars(player, session);
        if (session.selection.isComplete()) {
            Selection selection = session.selection;
            lang.send(player, ClaimMessages.WAND_SELECTION, "size", selection.width() + "x" + selection.length(),
                    "area", String.valueOf(selection.area()));
            for (String line : session.task.preview(player, selection)) {
                player.sendMessage(LegacyText.SERIALIZER.deserialize(line));
            }
            lang.send(player, ClaimMessages.WAND_CONFIRM_HINT);
        }
    }

    /** Sneak and left-click: makes the selection so, if both corners are picked. */
    public void confirm(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            orphan(player);
            return;
        }
        if (!session.selection.isComplete()) {
            lang.send(player, ClaimMessages.WAND_INCOMPLETE);
            return;
        }
        if (session.task.confirm(player, session.selection)) {
            end(player, false);
        }
    }

    /**
     * Ends a player's selection and takes every wand they hold.
     *
     * @param tell whether to say it was given up - a drop, not a finished claim
     */
    public void end(Player player, boolean tell) {
        Session session = sessions.remove(player.getUniqueId());
        if (session != null) {
            hidePillars(player, session);
        }
        removeWands(player);
        if (tell) {
            lang.send(player, ClaimMessages.WAND_CANCELLED);
        }
    }

    /** A player gone: nothing to show them any more, only to forget. */
    public void forget(UUID playerId) {
        sessions.remove(playerId);
    }

    /** A wand with no selection behind it - kept after a restart - is simply taken back. */
    private void orphan(Player player) {
        removeWands(player);
        lang.send(player, ClaimMessages.WAND_EXPIRED);
    }

    private void removeWands(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (wand.isWand(contents[slot])) {
                player.getInventory().setItem(slot, null);
            }
        }
        if (wand.isWand(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    // ------------------------------------------------------------------
    // Pillars
    // ------------------------------------------------------------------

    /**
     * Shows a column on each picked corner - on all four once both are picked - from
     * the block above the corner up {@code pillar-height} blocks, through air only, so
     * the ground and walls read as they are.
     */
    private void showPillars(Player player, Session session) {
        Selection selection = session.selection;
        World world = player.getWorld();
        if (selection.world() == null || !selection.world().equals(world.getName())) {
            return;
        }
        ClaimSettings.WandRules wandRules = rules.get();
        Material material = Material.matchMaterial(wandRules.pillarMaterial());
        BlockData pillar = (material == null || !material.isBlock() ? Material.GLASS : material).createBlockData();
        Material markerMaterial = Material.matchMaterial(wandRules.pillarMarkerMaterial());
        BlockData marker = (markerMaterial == null || !markerMaterial.isBlock()
                ? Material.GLOWSTONE : markerMaterial).createBlockData();
        List<int[]> columns = new ArrayList<>();
        if (selection.isComplete()) {
            int y = selection.maxY();
            columns.add(new int[] {selection.first().x(), selection.first().y(), selection.first().z()});
            columns.add(new int[] {selection.second().x(), selection.second().y(), selection.second().z()});
            columns.add(new int[] {selection.first().x(), y, selection.second().z()});
            columns.add(new int[] {selection.second().x(), y, selection.first().z()});
        } else {
            Selection.Corner corner = selection.first() != null ? selection.first() : selection.second();
            columns.add(new int[] {corner.x(), corner.y(), corner.z()});
        }
        for (int[] column : columns) {
            for (int dy = 1; dy <= wandRules.pillarHeight(); dy++) {
                int y = column[1] + dy;
                if (y >= world.getMaxHeight()) {
                    break;
                }
                Location at = new Location(world, column[0], y, column[2]);
                if (at.getBlock().getType().isAir()) {
                    // One block in every few is the marker: a pack that clears glass
                    // would otherwise leave the column invisible.
                    player.sendBlockChange(at, com.lawkeys.hcfcore.claim.view.BorderColumns
                            .isMarker(dy - 1, wandRules.pillarMarkerEvery()) ? marker : pillar);
                    session.shown.add(at);
                }
            }
        }
    }

    private void hidePillars(Player player, Session session) {
        for (Location at : session.shown) {
            if (at.getWorld() != null && at.getWorld().equals(player.getWorld())) {
                player.sendBlockChange(at, at.getBlock().getBlockData());
            }
        }
        session.shown.clear();
    }
}
