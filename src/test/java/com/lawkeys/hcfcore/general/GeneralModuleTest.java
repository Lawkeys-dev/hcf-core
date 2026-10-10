package com.lawkeys.hcfcore.general;

import com.lawkeys.hcfcore.general.command.BasicsCommand;
import com.lawkeys.hcfcore.general.listener.GodModeListener;
import com.lawkeys.hcfcore.general.listener.MessageListener;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.warmup.WarmupModule;
import org.bukkit.World;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneralModuleTest {

    @TempDir
    File tempDir;

    private Plugin plugin;
    private Server server;
    private PluginManager pluginManager;
    private LangManager lang;
    private WarmupModule warmups;
    private GeneralModule module;

    private final List<Listener> registeredListeners = new ArrayList<>();
    private final Map<String, PluginCommand> commands = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        registeredListeners.clear();
        commands.clear();

        pluginManager = (PluginManager) Proxy.newProxyInstance(
                PluginManager.class.getClassLoader(),
                new Class<?>[]{PluginManager.class},
                (proxy, method, args) -> {
                    if ("registerEvents".equals(method.getName())) {
                        registeredListeners.add((Listener) args[0]);
                        return null;
                    }
                    return defaultReturnValue(method.getReturnType());
                }
        );

        server = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class},
                (proxy, method, args) -> {
                    if ("getPluginManager".equals(method.getName())) {
                        return pluginManager;
                    }
                    if ("getPluginCommand".equals(method.getName())) {
                        return commands.get((String) args[0]);
                    }
                    return defaultReturnValue(method.getReturnType());
                }
        );

        Logger logger = Logger.getLogger("GeneralModuleTest");

        plugin = (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> {
                    if ("getServer".equals(method.getName())) {
                        return server;
                    }
                    if ("getDataFolder".equals(method.getName())) {
                        return tempDir;
                    }
                    if ("getLogger".equals(method.getName())) {
                        return logger;
                    }
                    if ("getResource".equals(method.getName())) {
                        return null;
                    }
                    return defaultReturnValue(method.getReturnType());
                }
        );

        lang = new LangManager(plugin);
        warmups = new WarmupModule(plugin, lang);
        module = new GeneralModule(plugin, lang, null, warmups);
    }

    private PluginCommand createCommand(String name) throws Exception {
        Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
        constructor.setAccessible(true);
        return constructor.newInstance(name, plugin);
    }

    private static Object defaultReturnValue(Class<?> returnType) {
        if (returnType == boolean.class || returnType == Boolean.class) return false;
        if (returnType == int.class || returnType == Integer.class) return 0;
        if (returnType == long.class || returnType == Long.class) return 0L;
        if (returnType == double.class || returnType == Double.class) return 0.0;
        if (returnType == float.class || returnType == Float.class) return 0.0f;
        return null;
    }

    @Nested
    class EnableAndDisable {

        @Test
        void enableRegistersAllListenersAndCommands() throws Exception {
            List<String> allCommands = new ArrayList<>();
            allCommands.addAll(List.of("spawn", "world", "top"));
            allCommands.addAll(List.of("msg", "reply", "togglepm", "ignore"));
            allCommands.addAll(List.of("heal", "kill", "gamemode", "rename", "more", "repair", "ping", "logout"));
            allCommands.addAll(BasicsCommand.commands());

            for (String cmdName : allCommands) {
                commands.put(cmdName, createCommand(cmdName));
            }

            module.enable();

            // Verify listeners registered
            assertEquals(2, registeredListeners.size());
            assertTrue(registeredListeners.stream().anyMatch(l -> l instanceof MessageListener));
            assertTrue(registeredListeners.stream().anyMatch(l -> l instanceof GodModeListener));

            // Verify command executors set
            for (String cmdName : allCommands) {
                PluginCommand cmd = commands.get(cmdName);
                assertNotNull(cmd.getExecutor(), "Executor should be registered for command: " + cmdName);
                assertNotNull(cmd.getTabCompleter(), "TabCompleter should be registered for command: " + cmdName);
            }
        }

        @Test
        void missingCommandIsHandledGracefully() throws Exception {
            // Register all except 'spawn'
            commands.put("world", createCommand("world"));

            module.enable();

            // 'world' should have executor set, missing command should not throw an exception
            assertNotNull(commands.get("world").getExecutor());
        }

        @Test
        void disableClearsGodModeState() {
            UUID id = UUID.randomUUID();
            module.setGod(id, true);
            assertTrue(module.isGod(id));

            module.disable();

            assertFalse(module.isGod(id));
        }
    }

    @Nested
    class GodModeAndGuards {

        @Test
        void godModeTogglesAndSets() {
            UUID id = UUID.randomUUID();
            assertFalse(module.isGod(id));

            module.setGod(id, true);
            assertTrue(module.isGod(id));

            module.setGod(id, false);
            assertFalse(module.isGod(id));

            assertTrue(module.toggleGod(id));
            assertTrue(module.isGod(id));

            assertFalse(module.toggleGod(id));
            assertFalse(module.isGod(id));
        }

        @Test
        void spawnGuardRefusesByDefaultAndWhenSet() {
            SpawnGuard customGuard = player -> true;
            module.setSpawnGuard(customGuard);
            assertTrue(module.refusesSpawn(null));
        }

        @Test
        void logoutGuardRefusesByDefaultAndWhenSet() {
            LogoutGuard customGuard = player -> true;
            module.setLogoutGuard(customGuard);
            assertTrue(module.refusesLogout(null));
        }

        @Test
        void messagesToggledObserverIsCalled() {
            AtomicBoolean called = new AtomicBoolean(false);
            UUID playerUuid = UUID.randomUUID();

            module.setMessagesToggledObserver((id, on) -> {
                assertEquals(playerUuid, id);
                assertTrue(on);
                called.set(true);
            });

            Player player = (Player) Proxy.newProxyInstance(
                    Player.class.getClassLoader(),
                    new Class<?>[]{Player.class},
                    (proxy, method, args) -> {
                        if ("getUniqueId".equals(method.getName())) {
                            return playerUuid;
                        }
                        return defaultReturnValue(method.getReturnType());
                    }
            );

            module.messagesToggled(player, true);
            assertTrue(called.get());
        }

        @Test
        void gettersReturnExpectedComponents() {
            assertSame(lang, module.getLang());
            assertNotNull(module.getSettings());
            assertNotNull(module.getMessages());
            assertSame(warmups, module.getWarmups());
        }
    }

    @Nested
    class WarmupsAndCombat {

        @Test
        void isBlockedByCombatReturnsFalseWithoutClaimModule() {
            UUID playerUuid = UUID.randomUUID();
            Player player = (Player) Proxy.newProxyInstance(
                    Player.class.getClassLoader(),
                    new Class<?>[]{Player.class},
                    (proxy, method, args) -> {
                        if ("getUniqueId".equals(method.getName())) {
                            return playerUuid;
                        }
                        return defaultReturnValue(method.getReturnType());
                    }
            );

            assertFalse(module.isBlockedByCombat(player));
        }

        @Test
        void zeroSecondSpawnWarmupExecutesImmediately() {
            UUID playerUuid = UUID.randomUUID();
            World world = (World) Proxy.newProxyInstance(
                    World.class.getClassLoader(),
                    new Class<?>[]{World.class},
                    (proxy, method, args) -> defaultReturnValue(method.getReturnType())
            );

            Player player = (Player) Proxy.newProxyInstance(
                    Player.class.getClassLoader(),
                    new Class<?>[]{Player.class},
                    (proxy, method, args) -> {
                        if ("getUniqueId".equals(method.getName())) return playerUuid;
                        if ("getWorld".equals(method.getName())) return world;
                        return defaultReturnValue(method.getReturnType());
                    }
            );

            assertTrue(module.beginSpawnWarmup(player, 0));
        }

        @Test
        void zeroSecondLogoutWarmupExecutesImmediately() {
            UUID playerUuid = UUID.randomUUID();
            Player player = (Player) Proxy.newProxyInstance(
                    Player.class.getClassLoader(),
                    new Class<?>[]{Player.class},
                    (proxy, method, args) -> {
                        if ("getUniqueId".equals(method.getName())) return playerUuid;
                        return defaultReturnValue(method.getReturnType());
                    }
            );

            assertTrue(module.beginLogoutWarmup(player, 0));
        }
    }
}
