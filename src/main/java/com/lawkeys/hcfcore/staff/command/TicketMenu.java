package com.lawkeys.hcfcore.staff.command;

import com.lawkeys.hcfcore.staff.StaffMessages;
import com.lawkeys.hcfcore.staff.StaffModule;
import com.lawkeys.hcfcore.staff.ticket.Ticket;
import com.lawkeys.hcfcore.staff.ticket.TicketStatus;
import com.lawkeys.hcfcore.staff.ticket.TicketType;
import com.lawkeys.hcfcore.theme.MenuLayout;
import com.lawkeys.hcfcore.theme.MenuMessages;
import com.lawkeys.hcfcore.theme.MenuStyle;
import com.lawkeys.hcfcore.util.ColorCodes;
import com.lawkeys.hcfcore.util.TextWrap;
import com.lawkeys.hcfcore.util.ItemText;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The ticket management menu: one item per ticket, oldest first.
 *
 * <p>Left-click takes a ticket and teleports to it, right-click closes it. Both go
 * through {@link TicketCommand}, so the menu and the typed subcommands cannot
 * disagree about what claiming or closing means.
 *
 * <p>The menu is a snapshot, as a chest window has to be. Two staff with it open
 * each see the queue as it was when they opened it; acting on a ticket the other
 * has already closed is answered with "no open ticket", because every click goes
 * back to the queue by id rather than trusting the item it came from.
 *
 * <p>This class is the inventory's {@link InventoryHolder}, which is how the click
 * listener tells the menu apart from every other window on the server.
 */
public final class TicketMenu implements InventoryHolder {

    /** Characters per lore line: the client never wraps lore by itself. */
    private static final int LORE_WIDTH = 40;

    private final StaffModule module;
    private final TicketCommand actions;
    private final MenuLayout layout;
    private final long[] ticketAtSlot;
    private final Inventory inventory;

    private TicketMenu(StaffModule module, TicketCommand actions, List<Ticket> queue, int page) {
        this.module = module;
        this.actions = actions;
        this.layout = MenuStyle.layout(queue.size(), page);
        this.ticketAtSlot = new long[layout.size()];
        Arrays.fill(ticketAtSlot, -1L);
        this.inventory = Bukkit.createInventory(this, layout.size(), MenuStyle.title(
                module.getLang().get(StaffMessages.TICKET_MENU_TITLE, "count", String.valueOf(queue.size()))));
        long now = System.currentTimeMillis();
        for (int i = 0; i < layout.itemSlots().size(); i++) {
            Ticket ticket = queue.get(layout.firstItem() + i);
            int slot = layout.itemSlots().get(i);
            ticketAtSlot[slot] = ticket.id();
            inventory.setItem(slot, icon(module, ticket, now));
        }
        MenuStyle.decorate(inventory, layout, module.getLang());
    }

    /** Opens the menu, or says the queue is empty rather than showing an empty chest. */
    public static void open(StaffModule module, TicketCommand actions, Player viewer) {
        open(module, actions, viewer, 0);
    }

    /** Opens a page of the menu: the oldest tickets first. */
    public static void open(StaffModule module, TicketCommand actions, Player viewer, int page) {
        Objects.requireNonNull(actions, "actions");
        List<Ticket> queue = module.getTickets().queue();
        if (queue.isEmpty()) {
            module.getLang().send(viewer, StaffMessages.TICKET_LIST_EMPTY);
            return;
        }
        viewer.openInventory(new TicketMenu(module, actions, queue, page).getInventory());
    }

    /** @return the page this slot's arrow leads to, or {@code -1} if it holds none */
    public int pageAt(int rawSlot) {
        if (rawSlot >= 0 && rawSlot == layout.previous()) {
            return layout.page() - 1;
        }
        return rawSlot >= 0 && rawSlot == layout.next() ? layout.page() + 1 : -1;
    }

    /** Opens another page of this menu. */
    public void turnTo(Player staff, int page) {
        open(module, actions, staff, page);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** @return the ticket shown in this slot of the menu, or {@code -1} for none */
    public long ticketAt(int rawSlot) {
        return rawSlot >= 0 && rawSlot < ticketAtSlot.length ? ticketAtSlot[rawSlot] : -1L;
    }

    /**
     * Acts on a click. Called a tick after the click itself: the javadoc of
     * {@code InventoryClickEvent} forbids closing the window, or teleporting its
     * viewer, from inside the handler.
     */
    public void act(Player staff, long ticketId, boolean leftClick) {
        staff.closeInventory();
        if (leftClick) {
            actions.visit(staff, ticketId);
        } else {
            actions.close(staff, ticketId);
        }
    }

    private static ItemStack icon(StaffModule module, Ticket ticket, long now) {
        Material material = ticket.status() == TicketStatus.CLAIMED ? Material.MAP
                : ticket.type() == TicketType.REPORT ? Material.PAPER : Material.BOOK;
        ItemStack item = ItemStack.of(material);
        String id = String.valueOf(ticket.id());
        String name = ticket.type() == TicketType.REPORT
                ? module.getLang().get(StaffMessages.TICKET_MENU_REPORT_NAME,
                        "id", id, "target", TicketCommand.targetName(ticket))
                : module.getLang().get(StaffMessages.TICKET_MENU_REQUEST_NAME,
                        "id", id, "player", ticket.openedName());

        List<String> lore = new ArrayList<>();
        lore.add(module.getLang().get(MenuMessages.SEPARATOR));
        lore.add(module.getLang().get(StaffMessages.TICKET_MENU_FROM, "player", ticket.openedName()));
        if (ticket.type() == TicketType.REPORT) {
            lore.add(module.getLang().get(StaffMessages.TICKET_MENU_ABOUT,
                    "target", TicketCommand.targetName(ticket)));
        }
        lore.add(module.getLang().get(StaffMessages.TICKET_MENU_STATUS,
                "status", TicketCommand.statusName(ticket)));
        lore.add(module.getLang().get(StaffMessages.TICKET_MENU_AGE, "age", TicketCommand.age(ticket, now)));
        lore.add("");
        for (String line : TextWrap.wrap(ticket.message(), LORE_WIDTH)) {
            lore.add(module.getLang().get(StaffMessages.TICKET_MENU_MESSAGE_LINE,
                    "line", ColorCodes.escape(line)));
        }
        lore.add("");
        lore.add(module.getLang().get(StaffMessages.TICKET_MENU_LEFT_CLICK));
        lore.add(module.getLang().get(StaffMessages.TICKET_MENU_RIGHT_CLICK));

        List<Component> components = new ArrayList<>(lore.size());
        for (String line : lore) {
            components.add(ItemText.line(line));
        }
        item.editMeta(meta -> {
            meta.customName(ItemText.line(name));
            meta.lore(components);
        });
        return item;
    }
}
