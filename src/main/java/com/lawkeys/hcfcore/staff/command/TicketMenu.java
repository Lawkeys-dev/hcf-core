package com.lawkeys.hcfcore.staff.command;

import com.lawkeys.hcfcore.staff.StaffMessages;
import com.lawkeys.hcfcore.staff.StaffModule;
import com.lawkeys.hcfcore.staff.ticket.Ticket;
import com.lawkeys.hcfcore.staff.ticket.TicketStatus;
import com.lawkeys.hcfcore.staff.ticket.TicketType;
import com.lawkeys.hcfcore.util.ColorCodes;
import com.lawkeys.hcfcore.util.TextWrap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

    /** A double chest. More tickets than this are listed, not shown. */
    static final int MAX_SLOTS = 54;
    /** Characters per lore line: the client never wraps lore by itself. */
    private static final int LORE_WIDTH = 40;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final TicketCommand actions;
    private final long[] ticketAtSlot;
    private final Inventory inventory;

    private TicketMenu(StaffModule module, TicketCommand actions, List<Ticket> queue) {
        this.actions = actions;
        int shown = Math.min(queue.size(), MAX_SLOTS);
        int size = Math.max(9, (shown + 8) / 9 * 9);
        this.ticketAtSlot = new long[size];
        Arrays.fill(ticketAtSlot, -1L);
        this.inventory = Bukkit.createInventory(this, size, LEGACY.deserialize(
                module.getLang().get(StaffMessages.TICKET_MENU_TITLE, "count", String.valueOf(queue.size()))));
        long now = System.currentTimeMillis();
        for (int slot = 0; slot < shown; slot++) {
            Ticket ticket = queue.get(slot);
            ticketAtSlot[slot] = ticket.id();
            inventory.setItem(slot, icon(module, ticket, now));
        }
    }

    /** Opens the menu, or says the queue is empty rather than showing an empty chest. */
    public static void open(StaffModule module, TicketCommand actions, Player viewer) {
        Objects.requireNonNull(actions, "actions");
        List<Ticket> queue = module.getTickets().queue();
        if (queue.isEmpty()) {
            module.getLang().send(viewer, StaffMessages.TICKET_LIST_EMPTY);
            return;
        }
        viewer.openInventory(new TicketMenu(module, actions, queue).getInventory());
        if (queue.size() > MAX_SLOTS) {
            module.getLang().send(viewer, StaffMessages.TICKET_MENU_MORE,
                    "shown", String.valueOf(MAX_SLOTS), "count", String.valueOf(queue.size() - MAX_SLOTS));
        }
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
            components.add(LEGACY.deserialize(line));
        }
        item.editMeta(meta -> {
            meta.customName(LEGACY.deserialize(name));
            meta.lore(components);
        });
        return item;
    }
}
