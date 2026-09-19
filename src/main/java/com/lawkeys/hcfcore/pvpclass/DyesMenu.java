package com.lawkeys.hcfcore.pvpclass;

import com.lawkeys.hcfcore.theme.MenuLayout;
import com.lawkeys.hcfcore.theme.MenuStyle;
import com.lawkeys.hcfcore.util.ItemText;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code /dyes} menu: one dye per colour a class's dyed set gives its arrows an
 * effect for - the effect, how long, the chance, and whether the viewer's set is
 * that colour now. Read only: every click in it is refused.
 *
 * <p>This class is the inventory's {@link InventoryHolder}, which is how the listener
 * tells the menu apart from every other window on the server.
 */
public final class DyesMenu implements InventoryHolder {


    /** One colour of one class. */
    public record Entry(PvpClass pvpClass, String colour, DyeEffect effect) {
    }

    private final Inventory inventory;

    private DyesMenu(ClassModule module, Player viewer, List<Entry> entries) {
        // A few classes' colours: one page is plenty, and one more is left out rather than paged.
        MenuLayout layout = MenuStyle.layout(entries.size(), 0);
        this.inventory = Bukkit.createInventory(this, layout.size(),
                MenuStyle.title(module.getLang().get(ClassMessages.DYES_TITLE)));
        Optional<String> worn = module.dyeColour(viewer);
        Optional<String> wornClass = module.getManager().active(viewer.getUniqueId()).map(PvpClass::id);
        for (int i = 0; i < layout.itemSlots().size(); i++) {
            inventory.setItem(layout.itemSlots().get(i), icon(module, entries.get(i), worn, wornClass));
        }
        MenuStyle.decorate(inventory, layout, module.getLang());
    }

    /**
     * Opens the menu.
     *
     * @return {@code false}, opening nothing, when no class gives an effect for a colour
     */
    public static boolean open(ClassModule module, Player viewer) {
        Objects.requireNonNull(module, "module");
        List<Entry> entries = entries(module.getSettings());
        if (entries.isEmpty()) {
            return false;
        }
        viewer.openInventory(new DyesMenu(module, viewer, entries).getInventory());
        return true;
    }

    /** @return every colour with an effect, class by class in file order, colours in the game's dye order */
    public static List<Entry> entries(ClassSettings settings) {
        List<Entry> entries = new ArrayList<>();
        for (PvpClass pvpClass : settings.classes()) {
            pvpClass.dyeEffects().entrySet().stream()
                    .sorted(Comparator.comparingInt(entry -> dyeOrder(entry.getKey())))
                    .forEach(entry -> entries.add(new Entry(pvpClass, entry.getKey(), entry.getValue())));
        }
        return entries;
    }

    private static int dyeOrder(String colour) {
        try {
            return DyeColor.valueOf(colour).ordinal();
        } catch (IllegalArgumentException unknown) {
            return Integer.MAX_VALUE;
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private static ItemStack icon(ClassModule module, Entry entry, Optional<String> worn, Optional<String> wornClass) {
        Material dye = Material.matchMaterial(entry.colour() + "_DYE");
        ItemStack item = ItemStack.of(dye == null ? Material.WHITE_DYE : dye);
        var lang = module.getLang();
        ClassEffect effect = entry.effect().effect();
        String colour = PvpClass.readableItem(entry.colour());
        List<Component> lore = new ArrayList<>();
        Component separator = MenuStyle.separator(lang);
        if (separator != null) {
            lore.add(separator);
        }
        for (String line : List.of(
                lang.get(ClassMessages.DYES_LORE_CLASS, "class", entry.pvpClass().displayName()),
                lang.get(ClassMessages.DYES_LORE_EFFECT, "effect", effect.displayName(),
                        "seconds", String.valueOf(effect.seconds())),
                lang.get(ClassMessages.DYES_LORE_CHANCE, "chance", ClassModule.formatNumber(entry.effect().chance())),
                "",
                lang.get(ClassMessages.DYES_LORE_HOWTO, "colour", colour))) {
            lore.add(line.isEmpty() ? Component.empty() : ItemText.line(line));
        }
        if (worn.map(entry.colour()::equals).orElse(false)
                && wornClass.map(entry.pvpClass().id()::equals).orElse(false)) {
            lore.add(Component.empty());
            lore.add(ItemText.line(lang.get(ClassMessages.DYES_LORE_YOURS)));
        }
        item.editMeta(meta -> {
            meta.customName(ItemText.line(lang.get(ClassMessages.DYES_ITEM_NAME, "colour", colour)));
            meta.lore(lore);
        });
        return item;
    }
}
