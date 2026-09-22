package com.lawkeys.hcfcore.events.planning;

import com.lawkeys.hcfcore.events.EventModule;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.theme.MenuLayout;
import com.lawkeys.hcfcore.theme.MenuStyle;
import com.lawkeys.hcfcore.util.ItemText;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@code /schedule} as a window: one item per day of the week ahead, each holding
 * that day's events, hour by hour - the project owner's choice of 22/09/2026, in
 * place of a list in the chat.
 *
 * <p>Read-only: it shows what is planned and answers no click. The chat listing is
 * still there ({@code /schedule chat}, or {@code weekly-schedule.menu.enabled:
 * false}), for the console and for anyone who prefers it.
 */
public final class ScheduleMenu implements InventoryHolder {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final int DAYS = 7;

    private final Inventory inventory;

    private ScheduleMenu(EventModule module, Player viewer, List<WeekAgenda.Entry> week) {
        LangManager lang = module.getLang();
        MenuLayout layout = MenuStyle.layout(DAYS, 0);
        this.inventory = Bukkit.createInventory(this, layout.size(),
                MenuStyle.title(lang.get(PlanningMessages.MENU_TITLE)));
        LocalDate today = LocalDate.now(module.getSettings().timeZone());
        DateTimeFormatter dates = DateTimeFormatter.ofPattern(
                com.lawkeys.hcfcore.util.ColorCodes.strip(lang.get(PlanningMessages.DATE_FORMAT)));
        PlanningController.Settings planning = module.getPlanning().getSettings();

        for (int day = 0; day < DAYS && day < layout.itemSlots().size(); day++) {
            LocalDate date = today.plusDays(day);
            List<WeekAgenda.Entry> entries = week.stream()
                    .filter(entry -> entry.at().toLocalDate().equals(date)).toList();
            inventory.setItem(layout.itemSlots().get(day), icon(module, date, today, dates, entries, planning));
        }
        MenuStyle.decorate(inventory, layout, lang);
        viewer.openInventory(inventory);
    }

    /** Opens the week ahead for this player. */
    public static void open(EventModule module, Player viewer) {
        Objects.requireNonNull(module, "module");
        new ScheduleMenu(module, viewer, module.getPlanning().weekAhead(System.currentTimeMillis()));
    }

    private static ItemStack icon(EventModule module, LocalDate date, LocalDate today, DateTimeFormatter dates,
                                  List<WeekAgenda.Entry> entries, PlanningController.Settings planning) {
        LangManager lang = module.getLang();
        PlanningController.MenuRules rules = planning.menu();
        Material material = Material.matchMaterial(date.equals(today) ? rules.todayMaterial()
                : entries.isEmpty() ? rules.emptyMaterial() : rules.dayMaterial());
        ItemStack item = ItemStack.of(material == null ? Material.PAPER : material);
        String day = lang.get(date.equals(today) ? PlanningMessages.TODAY
                : date.equals(today.plusDays(1)) ? PlanningMessages.TOMORROW
                : PlanningMessages.day(date.getDayOfWeek()));

        List<Component> lore = new ArrayList<>();
        Component separator = MenuStyle.separator(lang);
        if (separator != null) {
            lore.add(separator);
        }
        if (entries.isEmpty()) {
            lore.add(ItemText.line(lang.get(PlanningMessages.MENU_DAY_EMPTY)));
        } else {
            for (WeekAgenda.Entry entry : entries) {
                lore.add(ItemText.line(lang.get(PlanningMessages.MENU_ENTRY,
                        "time", TIME.format(entry.at()),
                        "event", module.getLauncher().displayName(entry.eventId()).orElse(entry.eventId()))));
            }
        }
        item.editMeta(meta -> {
            meta.customName(ItemText.line(lang.get(PlanningMessages.MENU_DAY,
                    "day", day, "date", dates.format(date), "count", String.valueOf(entries.size()))));
            meta.lore(lore);
        });
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** The menu answers no click: it is a page, not a set of buttons. */
    public static final class Clicks implements Listener {

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onClick(InventoryClickEvent event) {
            if (event.getInventory().getHolder(false) instanceof ScheduleMenu) {
                event.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onDrag(InventoryDragEvent event) {
            if (event.getInventory().getHolder(false) instanceof ScheduleMenu) {
                event.setCancelled(true);
            }
        }
    }
}
