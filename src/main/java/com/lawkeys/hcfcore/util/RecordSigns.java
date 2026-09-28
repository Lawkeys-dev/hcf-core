package com.lawkeys.hcfcore.util;

import io.papermc.paper.event.player.PlayerOpenSignEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;

/**
 * Signs that are a record, not a note - a death sign, an event won - whose four
 * lines are set by the plugin and stay exactly as they are once placed.
 *
 * <p><strong>Why the text is written again at placing.</strong> The game copies a
 * block's data from its item only for a player allowed to set block data - an
 * operator: anybody else placed a death sign as a blank sign and was handed the
 * editor (found in the review of 28/09/2026 - on the test server every tester was
 * an operator). So the lines travel in the item's own data, and the placed sign is
 * written and waxed by the plugin, for everybody; the editor is refused, and so is
 * any rewriting.
 */
public final class RecordSigns implements Listener {

    /** The four lines, joined by a line feed, in the item's data and on the placed sign. */
    public static final NamespacedKey LINES = new NamespacedKey("hcfcore", "record_sign");

    private final Plugin plugin;

    public RecordSigns(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * @param lines four lines, coloured already ({@code LangManager#colorize})
     * @param name  the item's name, coloured already
     * @return a sign that writes itself with these lines when placed, or
     *         {@code null} when the material is not a sign
     */
    public static ItemStack item(Material material, List<String> lines, String name, List<String> lore) {
        if (material == null || !material.name().endsWith("_SIGN")) {
            return null;
        }
        ItemStack item = ItemStack.of(material);
        item.editMeta(meta -> {
            if (meta instanceof BlockStateMeta stateMeta && stateMeta.getBlockState() instanceof Sign state) {
                // For an operator the game copies this itself; for anybody else,
                // onPlace below writes it.
                for (int line = 0; line < 4; line++) {
                    state.getSide(Side.FRONT).line(line, ItemText.line(line < lines.size() ? lines.get(line) : ""));
                }
                state.setWaxed(true);
                stateMeta.setBlockState(state);
            }
            meta.customName(ItemText.line(name));
            meta.lore(lore.stream().map(ItemText::line).toList());
            meta.getPersistentDataContainer().set(LINES, PersistentDataType.STRING, String.join("\n", lines));
        });
        return item;
    }

    private static String linesOf(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(LINES, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String lines = linesOf(event.getItemInHand());
        if (lines == null) {
            return;
        }
        Location at = event.getBlockPlaced().getLocation();
        write(at.getBlock(), lines);
        // And again once the placing is over: whatever the game wrote after this
        // event, the record is what stays.
        Bukkit.getScheduler().runTask(plugin, () -> write(at.getBlock(), lines));
    }

    private static void write(Block block, String lines) {
        if (!(block.getState() instanceof Sign sign)) {
            return;
        }
        String[] split = lines.split("\\n", -1);
        for (int line = 0; line < 4; line++) {
            sign.getSide(Side.FRONT).line(line, LegacyText.of(line < split.length ? split[line] : ""));
            sign.getSide(Side.BACK).line(line, net.kyori.adventure.text.Component.empty());
        }
        sign.setWaxed(true);
        sign.getPersistentDataContainer().set(LINES, PersistentDataType.STRING, lines);
        sign.update(true, false);
    }

    /** No editor for a record: at placing, or on a click. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(PlayerOpenSignEvent event) {
        Player player = event.getPlayer();
        boolean placingOne = event.getCause() == PlayerOpenSignEvent.Cause.PLACE
                && (linesOf(player.getInventory().getItemInMainHand()) != null
                || linesOf(player.getInventory().getItemInOffHand()) != null);
        if (placingOne || event.getSign().getPersistentDataContainer().has(LINES, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    /** And no rewriting, whatever reaches the server. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEdit(SignChangeEvent event) {
        if (event.getBlock().getState(false) instanceof Sign sign
                && sign.getPersistentDataContainer().has(LINES, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }
}
