package com.lawkeys.hcfcore.schedule;

import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.events.DailySchedule;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.schedule.command.KeyAllCommand;
import com.lawkeys.hcfcore.schedule.command.TimerCommand;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Things that happen on a clock (FEATURES.md sections 9 and 10): rotating tips,
 * announcements and commands at fixed times every day, countdowns staff start by
 * hand, and the key-all.
 *
 * <p>Independent of every other module. The daily times are read with
 * {@link DailySchedule}, which the events module already relies on, so the two
 * subtleties it handles - the next time crossing midnight, a window spanning it -
 * are not written twice.
 *
 * <p><strong>Nothing ships switched on.</strong> Tips are off, like the tab list: a
 * message every five minutes that nobody asked for is something an operator has to
 * go and find to turn off. Schedules, presets and the key-all's commands are empty -
 * what a server announces, and what its keys are, belong to it.
 */
public final class ScheduleModule {

    /** The name of the timer {@code /keyall <countdown>} starts. */
    public static final String KEY_ALL_TIMER = "keyall";

    /** Starting and stopping timers, and the key-all. */
    public static final String ADMIN_PERMISSION = "hcfcore.schedule.admin";

    private final Plugin plugin;
    private final LangManager lang;

    private volatile ScheduleSettings settings = ScheduleSettings.defaults();
    private final CustomTimers timers = new CustomTimers();
    private volatile TipRotation tips = new TipRotation(List.of(), false, new Random());
    private volatile Predicate<Player> wantsTips = player -> true;
    private BukkitTask task;
    private long lastTick;
    private long lastTipAt;

    public ScheduleModule(Plugin plugin, LangManager lang) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lang = Objects.requireNonNull(lang, "lang");
    }

    public LangManager getLang() {
        return lang;
    }

    public ScheduleSettings getSettings() {
        return settings;
    }

    public CustomTimers getTimers() {
        return timers;
    }

    /**
     * Decides who receives a tip. Installed by the player settings module, so a
     * player who turned tips off stops receiving them; until then, everybody does.
     */
    public void setTipFilter(Predicate<Player> filter) {
        this.wantsTips = Objects.requireNonNull(filter, "filter");
    }

    public void enable() {
        reloadSettings();
        long now = System.currentTimeMillis();
        // From now on: a time that passed while the server was off is not replayed.
        this.lastTick = now;
        this.lastTipAt = now;
        register("timer", new TimerCommand(this));
        register("keyall", new KeyAllCommand(this));
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void register(String name, TabExecutor executor) {
        PluginCommand command = plugin.getServer().getPluginCommand(name);
        if (command == null) {
            plugin.getLogger().severe("The '" + name + "' command is missing from plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    public void reloadSettings() {
        var section = ConfigManager.loadFile(plugin, "schedule.yml");
        ScheduleSettings loaded = ScheduleSettingsLoader.load(section,
                warning -> plugin.getLogger().warning("schedule.yml: " + warning));
        this.settings = loaded;
        this.tips = new TipRotation(loaded.tips().messages(), loaded.tips().random(), new Random());
    }

    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        timers.clear();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long from = lastTick;
        lastTick = now;
        ScheduleSettings current = settings;
        if (!current.enabled()) {
            return;
        }
        for (ScheduleSettings.ScheduledAction action : current.schedules()) {
            if (DailySchedule.occursWithin(action.times(), current.zone(), from, now)) {
                run(action.broadcast(), action.commands(), null);
            }
        }
        for (CustomTimers.Timer ended : timers.pollEnded(now)) {
            finish(ended);
        }
        ScheduleSettings.TipRules tipRules = current.tips();
        if (tipRules.enabled() && now - lastTipAt >= tipRules.intervalSeconds() * 1000L) {
            lastTipAt = now;
            tips.next().ifPresent(tip -> sendTip(tipRules.prefix() + tip));
        }
    }

    private void sendTip(String tip) {
        String line = LangManager.colorize(tip);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (wantsTips.test(player)) {
                player.sendMessage(line);
            }
        }
    }

    /**
     * Starts a custom timer.
     *
     * @param label shown instead of the preset's, or of the name, when not blank
     * @return {@code false} if one of that name is already running
     */
    public boolean startTimer(String name, long seconds, String label) {
        String key = CustomTimers.normalize(name);
        String shown;
        if (label != null && !label.isBlank()) {
            shown = label;
        } else if (key.equals(KEY_ALL_TIMER)) {
            shown = settings.keyAll().label();
        } else {
            ScheduleSettings.TimerPreset preset = settings.presets().get(key);
            shown = preset != null ? preset.label() : name;
        }
        return timers.start(key, shown, System.currentTimeMillis() + seconds * 1000L);
    }

    private void finish(CustomTimers.Timer timer) {
        if (timer.name().equals(KEY_ALL_TIMER)) {
            keyAll();
            return;
        }
        ScheduleSettings.TimerPreset preset = settings.presets().get(timer.name());
        if (preset == null) {
            broadcast(lang.get(ScheduleMessages.TIMER_ENDED, "timer", timer.label()));
            return;
        }
        run(preset.endBroadcast().isBlank()
                        ? lang.get(ScheduleMessages.TIMER_ENDED, "timer", timer.label())
                        : preset.endBroadcast(),
                preset.endCommands(), timer.name());
    }

    /**
     * Runs the key-all: its commands once for every player online, then its
     * broadcast.
     *
     * @return how many players it ran for; {@code -1} when no command is configured,
     *         which the caller reports rather than pretending something happened
     */
    public int keyAll() {
        ScheduleSettings.KeyAllRules rules = settings.keyAll();
        if (rules.commands().isEmpty()) {
            plugin.getLogger().warning("A key-all ran with no commands under key-all in schedule.yml; "
                    + "nobody received anything.");
            return -1;
        }
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (String command : rules.commands()) {
                dispatch(command.replace("%player%", player.getName()));
            }
            count++;
        }
        if (!rules.broadcast().isBlank()) {
            broadcast(LangManager.colorize(rules.broadcast().replace("%count%", String.valueOf(count))));
        }
        return count;
    }

    private void run(String broadcast, List<String> commands, String timerName) {
        if (!broadcast.isBlank()) {
            broadcast(LangManager.colorize(broadcast));
        }
        for (String command : commands) {
            dispatch(timerName == null ? command : command.replace("%timer%", timerName));
        }
    }

    private void dispatch(String command) {
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } catch (RuntimeException e) {
            // One broken command must not stop the rest of a schedule.
            plugin.getLogger().log(Level.WARNING, "Scheduled command '" + command + "' failed.", e);
        }
    }

    private static void broadcast(String line) {
        Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(line));
    }
}
