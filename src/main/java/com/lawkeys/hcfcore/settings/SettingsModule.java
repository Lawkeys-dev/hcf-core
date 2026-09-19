package com.lawkeys.hcfcore.settings;

import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.database.dao.JdbcPlayerSettingsStore;
import com.lawkeys.hcfcore.general.GeneralModule;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.schedule.ScheduleModule;
import com.lawkeys.hcfcore.settings.command.SettingsCommand;
import com.lawkeys.hcfcore.settings.listener.SettingsListener;
import com.lawkeys.hcfcore.startup.StartupBarrier;
import com.lawkeys.hcfcore.startup.StartupGate;
import com.lawkeys.hcfcore.ui.UiModule;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

/**
 * What players can switch off for themselves (FEATURES.md section 9): the
 * scoreboard, private messages, tips, and picking up cobblestone.
 *
 * <p>Started after the modules it switches, whose seams it answers - the board
 * filter of {@code ui/}, the tip filter of {@code schedule/}, the {@code /togglepm}
 * observer of {@code general/} - so none of them knows this module exists. Each is
 * optional: a setting whose module is not running simply has nothing to switch.
 *
 * <p>Stored, unlike most session toggles in the plugin: a player who switched the
 * scoreboard off should not have to do it again after every restart.
 */
public final class SettingsModule {

    private final Plugin plugin;
    private final LangManager lang;
    private final StartupGate startup;
    private final UiModule ui;
    private final GeneralModule general;
    private final ScheduleModule schedule;

    private volatile boolean enabled = true;
    private volatile Set<PlayerSetting> offered = EnumSet.allOf(PlayerSetting.class);
    private volatile Set<Material> cobblestone = EnumSet.of(Material.COBBLESTONE, Material.COBBLED_DEEPSLATE);
    private PlayerSettings settings;
    private BukkitTask saveTask;

    /** Any of {@code ui}, {@code general} and {@code schedule} may be {@code null}. */
    public SettingsModule(Plugin plugin, LangManager lang, StartupGate startup, UiModule ui,
                          GeneralModule general, ScheduleModule schedule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lang = Objects.requireNonNull(lang, "lang");
        this.startup = Objects.requireNonNull(startup, "startup");
        this.ui = ui;
        this.general = general;
        this.schedule = schedule;
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

    public boolean isEnabled() {
        return enabled;
    }

    /** @return the settings players can change, in menu order */
    public List<PlayerSetting> offered() {
        List<PlayerSetting> list = new ArrayList<>();
        for (PlayerSetting setting : PlayerSetting.values()) {
            if (offered.contains(setting)) {
                list.add(setting);
            }
        }
        return list;
    }

    public boolean isCobblestone(Material material) {
        return cobblestone.contains(material);
    }

    /**
     * @return whether a setting is on for this player. One that is not offered - or
     *         with the module off - is always on: taking a toggle away must not leave
     *         players stuck with the choice they made while it existed
     */
    public boolean isOn(Player player, PlayerSetting setting) {
        return !enabled || !offered.contains(setting) || settings == null
                || settings.isOn(player.getUniqueId(), setting);
    }

    public void enable(DataSource dataSource, long saveIntervalSeconds) {
        reloadSettings();
        PlayerSettingsStore store = dataSource == null
                ? PlayerSettingsStore.NO_OP
                : new JdbcPlayerSettingsStore(dataSource, message -> plugin.getLogger().info(message));
        this.settings = new PlayerSettings(store);

        StartupBarrier.Load load = startup.expect("player settings");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                settings.loadAll();
                load.succeeded();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load player settings.", e);
                load.failed();
            }
        });
        if (saveIntervalSeconds > 0) {
            long ticks = saveIntervalSeconds * 20L;
            this.saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushQuietly, ticks, ticks);
        }

        // The seams of the modules this one switches (ARCHITECTURE.md section 14).
        if (ui != null) {
            ui.setBoardFilter(player -> isOn(player, PlayerSetting.SCOREBOARD));
            // A tagged row whose section has no setting stays shown: a typo hides nothing.
            ui.setRowFilter((player, section) -> PlayerSetting.scoreboardSection(section)
                    .map(setting -> isOn(player, setting)).orElse(true));
        }
        if (schedule != null) {
            schedule.setTipFilter(player -> isOn(player, PlayerSetting.TIPS));
        }
        if (general != null) {
            general.setMessagesToggledObserver((playerId, on) -> {
                settings.set(playerId, PlayerSetting.PRIVATE_MESSAGES, on);
                flushSoon();
            });
        }

        plugin.getServer().getPluginManager().registerEvents(new SettingsListener(this), plugin);
        SettingsCommand command = new SettingsCommand(this);
        for (String name : new String[] {"settings", "cobble"}) {
            PluginCommand registered = plugin.getServer().getPluginCommand(name);
            if (registered == null) {
                plugin.getLogger().severe("The '" + name + "' command is missing from plugin.yml.");
                continue;
            }
            registered.setExecutor(command);
            registered.setTabCompleter(command);
        }
    }

    public void reloadSettings() {
        SettingsConfig config = SettingsConfigLoader.load(ConfigManager.loadFile(plugin, "settings.yml"),
                warning -> plugin.getLogger().warning("settings.yml: " + warning));
        Set<Material> readCobble = EnumSet.noneOf(Material.class);
        for (String name : config.cobblestone()) {
            Material material = Material.matchMaterial(name);
            if (material == null || !material.isItem()) {
                plugin.getLogger().warning("settings.yml: cobblestone.materials lists '" + name
                        + "', which is not an item; ignored.");
            } else {
                readCobble.add(material);
            }
        }
        this.enabled = config.enabled();
        this.offered = config.offered().isEmpty()
                ? EnumSet.noneOf(PlayerSetting.class)
                : EnumSet.copyOf(config.offered());
        this.cobblestone = readCobble;
        // A setting just taken away, or given back, applies to the board at once.
        if (ui != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                ui.refresh(player);
            }
        }
    }

    /** Flips one setting for a player and applies it at once. @return whether it is on now */
    public boolean toggle(Player player, PlayerSetting setting) {
        boolean on = settings.toggle(player.getUniqueId(), setting);
        apply(player, setting);
        flushSoon();
        return on;
    }

    /** Sets one setting outright and applies it at once. */
    public void set(Player player, PlayerSetting setting, boolean on) {
        settings.set(player.getUniqueId(), setting, on);
        apply(player, setting);
        flushSoon();
    }

    /** Puts a player's stored choices back in force - at join, since the other modules forget them on quit. */
    public void restore(Player player) {
        apply(player, PlayerSetting.PRIVATE_MESSAGES);
    }

    private void apply(Player player, PlayerSetting setting) {
        switch (setting) {
            case SCOREBOARD -> {
                if (ui != null) {
                    ui.refresh(player);
                }
            }
            case PRIVATE_MESSAGES -> {
                if (general != null) {
                    general.getMessages().setMessagesOn(player.getUniqueId(),
                            isOn(player, PlayerSetting.PRIVATE_MESSAGES));
                }
            }
            case TIPS, COBBLESTONE -> {
                // Read when they happen; nothing to push.
            }
        }
    }

    /** @return the name of a setting as players read it */
    public String displayName(PlayerSetting setting) {
        return lang.get(SettingsMessages.name(setting));
    }

    /** @return the offered setting a player typed, by its key */
    public java.util.Optional<PlayerSetting> parse(String input) {
        return PlayerSetting.byKey(input).filter(offered::contains);
    }

    private void flushSoon() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::flushQuietly);
    }

    private void flushQuietly() {
        try {
            settings.flush();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Saving player settings failed; the changes stay queued.", e);
        }
    }

    public void disable() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (settings == null) {
            return;
        }
        try {
            int written = settings.flush();
            if (written > 0) {
                plugin.getLogger().info("Saved " + written + " player settings on shutdown.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save player settings on shutdown", e);
        }
    }
}
