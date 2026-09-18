package com.lawkeys.hcfcore.kit;

import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.database.dao.JdbcKitStore;
import com.lawkeys.hcfcore.kit.command.KitCommand;
import com.lawkeys.hcfcore.kit.command.KitLayoutMenu;
import com.lawkeys.hcfcore.kit.listener.KitLayoutListener;
import com.lawkeys.hcfcore.kit.listener.KitSignListener;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.startup.StartupBarrier;
import com.lawkeys.hcfcore.startup.StartupGate;
import com.lawkeys.hcfcore.util.Cooldowns;
import com.lawkeys.hcfcore.util.ItemText;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Kits, partner items and refill signs (FEATURES.md section 7).
 *
 * <p>A kit is created by saving somebody's inventory rather than by writing items
 * into YAML: a full HCF loadout is enchanted armour, brewed potions and named
 * items, and describing that by hand in configuration is a job nobody finishes
 * correctly.
 */
public final class KitModule {

    private final Plugin plugin;
    private final LangManager lang;
    private final StartupGate startup;

    private volatile KitSettings settings = KitSettings.defaults();
    private KitManager manager;
    private NamespacedKey layoutKey;
    private BukkitTask saveTask;

    public KitModule(Plugin plugin, LangManager lang, StartupGate startup) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lang = Objects.requireNonNull(lang, "lang");
        this.startup = Objects.requireNonNull(startup, "startup");
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public LangManager getLang() {
        return lang;
    }

    public StartupGate getStartup() {
        return startup;
    }

    public KitSettings getSettings() {
        return settings;
    }

    public KitManager getManager() {
        return manager;
    }

    public void enable(DataSource dataSource, long saveIntervalSeconds) {
        reloadSettings();
        this.layoutKey = new NamespacedKey(plugin, "kit_layout_slot");

        KitStore store = dataSource == null
                ? KitStore.NO_OP
                : new JdbcKitStore(dataSource, message -> plugin.getLogger().info(message));
        this.manager = new KitManager(store);

        StartupBarrier.Load load = startup.expect("kits");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                manager.loadAll();
                plugin.getLogger().info("Loaded " + manager.size() + " kits.");
                load.succeeded();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load kits.", e);
                load.failed();
            }
        });

        if (saveIntervalSeconds > 0) {
            long ticks = saveIntervalSeconds * 20L;
            this.saveTask = Bukkit.getScheduler()
                    .runTaskTimerAsynchronously(plugin, this::flushQuietly, ticks, ticks);
        }

        plugin.getServer().getPluginManager().registerEvents(new KitSignListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new KitLayoutListener(this), plugin);

        PluginCommand command = plugin.getServer().getPluginCommand("kit");
        if (command == null) {
            plugin.getLogger().severe("The 'kit' command is missing from plugin.yml.");
            return;
        }
        KitCommand executor = new KitCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    /**
     * Hands a kit to a player, in the layout they chose for it ({@link KitLayout}).
     *
     * <p>Anything that will not fit is dropped at their feet rather than silently
     * lost - a kit that quietly eats half of itself because somebody had a full
     * inventory is the kind of bug players report as "the kit is broken". Without
     * {@code clear-before-giving}, an item goes to its slot when that slot is free and
     * joins the inventory like a picked-up item when it is not.
     */
    public void give(Player player, Kit kit) {
        ItemStack[] items = read(kit);
        if (items == null) {
            lang.send(player, KitMessages.DISABLED);
            return;
        }
        boolean[] filled = KitLayoutMenu.filled(items);
        int[] to = (settings.layoutEditor() ? manager.layout(player.getUniqueId(), kit.id()) : KitLayout.NONE)
                .placement(filled);
        ItemStack[] arranged = new ItemStack[KitLayout.SLOTS];
        for (int slot = 0; slot < KitLayout.SLOTS; slot++) {
            if (filled[slot]) {
                arranged[to[slot]] = items[slot];
            }
        }
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> rest = new ArrayList<>();
        if (settings.clearBeforeGiving()) {
            inventory.clear();
            inventory.setContents(arranged);
        } else {
            for (int slot = 0; slot < KitLayout.SLOTS; slot++) {
                ItemStack current = inventory.getItem(slot);
                if (arranged[slot] == null) {
                    continue;
                }
                if (current == null || current.isEmpty()) {
                    inventory.setItem(slot, arranged[slot]);
                } else {
                    rest.add(arranged[slot]);
                }
            }
        }
        // A saved inventory is 41 slots today; anything past that is never dropped.
        for (int slot = KitLayout.SLOTS; slot < items.length; slot++) {
            if (items[slot] != null && !items[slot].isEmpty()) {
                rest.add(items[slot]);
            }
        }
        for (ItemStack item : rest) {
            for (ItemStack leftover : inventory.addItem(item).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    /** @return a kit's items, or {@code null} (logged) when they cannot be read */
    private ItemStack[] read(Kit kit) {
        try {
            return ItemStack.deserializeItemsFromBytes(kit.contents());
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Kit '" + kit.id() + "' could not be read.", e);
            return null;
        }
    }

    // ------------------------------------------------------------------
    // The layout editor
    // ------------------------------------------------------------------

    public NamespacedKey getLayoutKey() {
        return layoutKey;
    }

    /** Opens the layout editor on a kit; tells the player when the kit cannot be read. */
    public void openLayoutEditor(Player player, Kit kit) {
        ItemStack[] items = read(kit);
        if (items == null) {
            lang.send(player, KitMessages.DISABLED);
            return;
        }
        KitLayoutMenu.open(this, player, kit, items);
    }

    /** @return the kit slot a layout editor copy stands for, or {@code null} for any other item */
    public Integer layoutSlotOf(ItemStack item) {
        if (item == null || item.isEmpty() || layoutKey == null) {
            return null;
        }
        return item.getPersistentDataContainer().get(layoutKey, PersistentDataType.INTEGER);
    }

    /**
     * Destroys every layout editor copy this player holds outside the editor - the net
     * under {@code KitLayoutListener}: a copy is never a real item.
     */
    public void purgeLayoutCopies(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (layoutSlotOf(contents[slot]) != null) {
                inventory.setItem(slot, null);
            }
        }
        boolean editing = player.getOpenInventory().getTopInventory().getHolder(false) instanceof KitLayoutMenu;
        if (!editing && layoutSlotOf(player.getItemOnCursor()) != null) {
            player.setItemOnCursor(null);
        }
    }

    public void reloadSettings() {
        var section = ConfigManager.loadFile(plugin, "kits.yml");
        this.settings = KitSettingsLoader.load(section,
                warning -> plugin.getLogger().warning("kits.yml: " + warning));
    }

    public void disable() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (manager == null) {
            return;
        }
        try {
            int written = manager.flush();
            if (written > 0) {
                plugin.getLogger().info("Saved " + written + " kit changes on shutdown.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save kits on shutdown", e);
        }
    }

    /** Writes a kit change now rather than at the next periodic save. */
    public void flushSoon() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::flushQuietly);
    }

    private void flushQuietly() {
        try {
            manager.purgeExpired();
            manager.flush();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Periodic kit save failed; the affected rows stay queued.", e);
        }
    }
}
