package com.lawkeys.hcfcore.kit.command;

import com.lawkeys.hcfcore.kit.Kit;
import com.lawkeys.hcfcore.kit.KitLayout;
import com.lawkeys.hcfcore.kit.KitMessages;
import com.lawkeys.hcfcore.kit.KitModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The kit layout editor: a kit's items laid out as the player's inventory, to be put
 * where the player wants them. Closing the window saves.
 *
 * <p><strong>The items here are copies, and must never leave the window.</strong> Each
 * carries a mark naming the kit slot it stands for - which is also what keeps two
 * identical stacks from merging, and what the save reads. The listener allows only
 * whole-stack moves inside the window and removes a marked item wherever else it turns
 * up (see {@code KitLayoutListener}).
 *
 * <p>This class is the inventory's {@link InventoryHolder}, which is how the listener
 * tells the editor apart from every other window.
 */
public final class KitLayoutMenu implements InventoryHolder {

    public static final int RESET_SLOT = 44;
    private static final int INFO_SLOT = 40;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final KitModule module;
    private final UUID playerId;
    private final Kit kit;
    private final ItemStack[] items;
    private final Inventory inventory;

    private KitLayoutMenu(KitModule module, Player player, Kit kit, ItemStack[] items) {
        this.module = module;
        this.playerId = player.getUniqueId();
        this.kit = kit;
        this.items = items;
        this.inventory = Bukkit.createInventory(this, KitLayout.EDITOR_SIZE,
                LEGACY.deserialize(module.getLang().get(KitMessages.LAYOUT_TITLE, "kit", kit.displayName())));
        ItemStack filler = button(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int slot = KitLayout.EDITOR_OFF_HAND + 1; slot < KitLayout.EDITOR_SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(INFO_SLOT, button(Material.BOOK,
                module.getLang().get(KitMessages.LAYOUT_INFO_NAME), module.getLang().get(KitMessages.LAYOUT_INFO_LORE)));
        inventory.setItem(RESET_SLOT, button(Material.BARRIER,
                module.getLang().get(KitMessages.LAYOUT_RESET_NAME), module.getLang().get(KitMessages.LAYOUT_RESET_LORE)));
        arrange(module.getManager().layout(playerId, kit.id()));
    }

    /** Opens the editor on this kit, in the order this player last chose. */
    public static void open(KitModule module, Player player, Kit kit, ItemStack[] items) {
        player.openInventory(new KitLayoutMenu(module, player, kit, items).getInventory());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Kit kit() {
        return kit;
    }

    public UUID playerId() {
        return playerId;
    }

    /** Lays the kit out in a layout's order, replacing whatever the window shows. */
    public void arrange(KitLayout layout) {
        for (int slot = 0; slot <= KitLayout.EDITOR_OFF_HAND; slot++) {
            inventory.setItem(slot, null);
        }
        boolean[] filled = filled(items);
        int[] to = layout.placement(filled);
        for (int kitSlot = 0; kitSlot < KitLayout.SLOTS; kitSlot++) {
            int editorSlot = filled[kitSlot] ? KitLayout.toEditorSlot(to[kitSlot]) : -1;
            if (editorSlot >= 0) {
                ItemStack copy = items[kitSlot].clone();
                int marked = kitSlot;
                copy.editPersistentDataContainer(data ->
                        data.set(module.getLayoutKey(), PersistentDataType.INTEGER, marked));
                inventory.setItem(editorSlot, copy);
            }
        }
    }

    /**
     * Reads the arrangement back.
     *
     * @param cursor what the player holds on the cursor as the window closes: a
     *               marked item there is put in the first empty slot, not lost
     */
    public KitLayout read(ItemStack cursor) {
        Map<Integer, Integer> moves = new HashMap<>();
        int firstEmpty = -1;
        for (int editorSlot = 0; editorSlot <= KitLayout.EDITOR_OFF_HAND; editorSlot++) {
            Integer kitSlot = module.layoutSlotOf(inventory.getItem(editorSlot));
            if (kitSlot != null) {
                moves.putIfAbsent(kitSlot, KitLayout.toPlayerSlot(editorSlot));
            } else if (firstEmpty < 0) {
                firstEmpty = editorSlot;
            }
        }
        Integer held = module.layoutSlotOf(cursor);
        if (held != null && firstEmpty >= 0) {
            moves.putIfAbsent(held, KitLayout.toPlayerSlot(firstEmpty));
        }
        return KitLayout.of(moves);
    }

    /** @return which of a kit's slots hold an item */
    public static boolean[] filled(ItemStack[] items) {
        boolean[] filled = new boolean[KitLayout.SLOTS];
        for (int slot = 0; slot < Math.min(items.length, KitLayout.SLOTS); slot++) {
            filled[slot] = items[slot] != null && !items[slot].isEmpty();
        }
        return filled;
    }

    private static ItemStack button(Material material, String name, String lore) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(meta -> {
            meta.customName(line(name));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(List.of(line(lore)));
            }
        });
        return item;
    }

    private static Component line(String legacy) {
        return LEGACY.deserialize(Objects.requireNonNullElse(legacy, "")).decoration(TextDecoration.ITALIC, false);
    }
}
