package com.lawkeys.hcfcore.economy;

import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.startup.StartupBarrier;
import com.lawkeys.hcfcore.startup.StartupGate;
import com.lawkeys.hcfcore.team.TeamModule;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyModuleTest {

    private Plugin plugin;
    private Server server;
    private PluginManager pluginManager;
    private BukkitScheduler scheduler;
    private CommandMap commandMap;
    private TeamModule teams;
    private LangManager lang;
    private StartupGate startupGate;

    private List<Listener> registeredEvents;
    private Map<String, PluginCommand> pluginCommands;
    private Map<String, org.bukkit.command.Command> knownCommandsMap;
    private AtomicBoolean scheduledTimerCancelled;

    @BeforeEach
    void setUp() throws Exception {
        registeredEvents = new ArrayList<>();
        pluginCommands = new HashMap<>();
        knownCommandsMap = new HashMap<>();
        scheduledTimerCancelled = new AtomicBoolean(false);

        plugin = createPluginDouble();
        pluginManager = createPluginManagerDouble();
        scheduler = createSchedulerDouble();
        commandMap = createCommandMapDouble();
        server = createServerDouble();

        setBukkitServer(server);

        // Pre-create PluginCommands that EconomyModule expects from plugin.yml
        for (String cmdName : List.of("bounty", "shop", "pay", "balance", "eco")) {
            PluginCommand cmd = createPluginCommand(cmdName, plugin);
            pluginCommands.put(cmdName, cmd);
            knownCommandsMap.put(cmdName, cmd);
        }

        lang = new LangManager(plugin);
        startupGate = new StartupGate(plugin, lang);
        teams = new TeamModule(plugin, lang, startupGate);
    }

    @AfterEach
    void tearDown() throws Exception {
        setBukkitServer(null);
    }

    private static void setBukkitServer(Server server) throws Exception {
        Field field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(null, server);
    }

    @Nested
    class Construction {

        @Test
        void validArgumentsExposeDependenciesAndInitialState() {
            EconomyModule module = new EconomyModule(plugin, teams, lang, startupGate);

            assertSame(plugin, module.getPlugin());
            assertSame(teams, module.getTeams());
            assertSame(lang, module.getLang());
            assertSame(startupGate, module.getStartup());
            assertNotNull(module.getSettings());
            assertNull(module.getManager(), "manager should be null before enable() is called");
        }

        @Test
        void nullPluginThrows() {
            assertThrows(NullPointerException.class, () -> new EconomyModule(null, teams, lang, startupGate));
        }

        @Test
        void nullTeamsThrows() {
            assertThrows(NullPointerException.class, () -> new EconomyModule(plugin, null, lang, startupGate));
        }

        @Test
        void nullLangThrows() {
            assertThrows(NullPointerException.class, () -> new EconomyModule(plugin, teams, null, startupGate));
        }

        @Test
        void nullStartupGateThrows() {
            assertThrows(NullPointerException.class, () -> new EconomyModule(plugin, teams, lang, null));
        }
    }

    @Nested
    class AmountParsing {

        @Test
        void parsesValidFiniteAmounts() {
            assertEquals(Optional.of(100.0), EconomyModule.parseAmount("100"));
            assertEquals(Optional.of(12.34), EconomyModule.parseAmount("12.34"));
            assertEquals(Optional.of(0.0), EconomyModule.parseAmount("0"));
            assertEquals(Optional.of(-50.0), EconomyModule.parseAmount("-50.0"));
            assertEquals(Optional.of(1_000_000.0), EconomyModule.parseAmount("1000000"));
        }

        @Test
        void rejectsNonNumericOrInvalidStrings() {
            assertEquals(Optional.empty(), EconomyModule.parseAmount("abc"));
            assertEquals(Optional.empty(), EconomyModule.parseAmount(""));
            assertEquals(Optional.empty(), EconomyModule.parseAmount("12.34.56"));
            assertEquals(Optional.empty(), EconomyModule.parseAmount(" $100 "));
        }

        @Test
        void rejectsNonFiniteDoubleValues() {
            assertEquals(Optional.empty(), EconomyModule.parseAmount("NaN"));
            assertEquals(Optional.empty(), EconomyModule.parseAmount("Infinity"));
            assertEquals(Optional.empty(), EconomyModule.parseAmount("-Infinity"));
        }
    }

    @Nested
    class EnableAndDisable {

        @Test
        void enableInitializesManagerRegistersListenersCommandsAndStartupLoads() {
            EconomyModule module = new EconomyModule(plugin, teams, lang, startupGate);

            module.enable(null, 60L);

            assertNotNull(module.getManager(), "EconomyManager must be initialized");
            assertNotNull(module.getSettings());

            // 4 event listeners are registered: BountyListener, ShopSignListener, ShopMenu.Clicks, KillRewardListener
            assertEquals(4, registeredEvents.size());

            // Verify commands have executors registered
            for (String cmdName : List.of("bounty", "shop", "pay", "balance", "eco")) {
                PluginCommand cmd = pluginCommands.get(cmdName);
                assertNotNull(cmd.getExecutor(), "Command executor must be set for /" + cmdName);
            }

            // Verify startup loads were declared and completed inline by mock scheduler
            assertTrue(startupGate.isReady() || startupGate.state() == StartupBarrier.State.LOADING);

            // Test disable cancels saveTask
            module.disable();
            assertTrue(scheduledTimerCancelled.get(), "Periodic save task should be cancelled on disable");
        }

        @Test
        void enableWithZeroSaveIntervalDoesNotScheduleSaveTask() {
            EconomyModule module = new EconomyModule(plugin, teams, lang, startupGate);

            module.enable(null, 0L);

            assertNotNull(module.getManager());
            module.disable();
            assertFalse(scheduledTimerCancelled.get(), "No periodic task was scheduled to cancel");
        }

        @Test
        void disableBeforeEnableIsSafeNoOp() {
            EconomyModule module = new EconomyModule(plugin, teams, lang, startupGate);
            module.disable();

            assertNull(module.getManager());
        }

        @Test
        void reloadSettingsLoadsDefaultSettingsWhenConfigIsMissing() {
            EconomyModule module = new EconomyModule(plugin, teams, lang, startupGate);
            module.reloadSettings();

            assertNotNull(module.getSettings());
            assertTrue(module.getSettings().enabled());
        }
    }

    // --- Helpers and Stubs for Bukkit Server / Scheduler / PluginManager ---

    private Plugin createPluginDouble() {
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> {
                    if ("getLogger".equals(method.getName())) {
                        return Logger.getLogger("EconomyModuleTest");
                    }
                    if ("getDataFolder".equals(method.getName())) {
                        return new File("build/tmp/test-plugin-data");
                    }
                    if ("getServer".equals(method.getName())) {
                        return server;
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    return null;
                }
        );
    }

    private Server createServerDouble() {
        return (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) {
                        return "MockServer";
                    }
                    if ("getVersion".equals(method.getName())) {
                        return "1.0.0";
                    }
                    if ("getBukkitVersion".equals(method.getName())) {
                        return "26.2";
                    }
                    if ("getPluginManager".equals(method.getName())) {
                        return pluginManager;
                    }
                    if ("getScheduler".equals(method.getName())) {
                        return scheduler;
                    }
                    if ("getCommandMap".equals(method.getName())) {
                        return commandMap;
                    }
                    if ("getPluginCommand".equals(method.getName())) {
                        return pluginCommands.get((String) args[0]);
                    }
                    if ("getLogger".equals(method.getName())) {
                        return Logger.getLogger("EconomyModuleTestServer");
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    return null;
                }
        );
    }

    private CommandMap createCommandMapDouble() {
        return (CommandMap) Proxy.newProxyInstance(
                CommandMap.class.getClassLoader(),
                new Class<?>[]{CommandMap.class},
                (proxy, method, args) -> {
                    if ("getKnownCommands".equals(method.getName())) {
                        return knownCommandsMap;
                    }
                    return null;
                }
        );
    }

    private PluginManager createPluginManagerDouble() {
        return (PluginManager) Proxy.newProxyInstance(
                PluginManager.class.getClassLoader(),
                new Class<?>[]{PluginManager.class},
                (proxy, method, args) -> {
                    if ("registerEvents".equals(method.getName())) {
                        registeredEvents.add((Listener) args[0]);
                        return null;
                    }
                    return null;
                }
        );
    }

    private BukkitScheduler createSchedulerDouble() {
        return (BukkitScheduler) Proxy.newProxyInstance(
                BukkitScheduler.class.getClassLoader(),
                new Class<?>[]{BukkitScheduler.class},
                (proxy, method, args) -> {
                    if ("runTaskAsynchronously".equals(method.getName())) {
                        Runnable runnable = (Runnable) args[1];
                        runnable.run();
                        return null;
                    }
                    if ("runTaskTimerAsynchronously".equals(method.getName())) {
                        return (BukkitTask) Proxy.newProxyInstance(
                                BukkitTask.class.getClassLoader(),
                                new Class<?>[]{BukkitTask.class},
                                (taskProxy, taskMethod, taskArgs) -> {
                                    if ("cancel".equals(taskMethod.getName())) {
                                        scheduledTimerCancelled.set(true);
                                        return null;
                                    }
                                    return null;
                                }
                        );
                    }
                    return null;
                }
        );
    }

    private static PluginCommand createPluginCommand(String name, Plugin owner) throws Exception {
        Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
        constructor.setAccessible(true);
        return constructor.newInstance(name, owner);
    }
}
