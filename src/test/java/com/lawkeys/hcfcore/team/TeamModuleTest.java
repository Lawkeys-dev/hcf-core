package com.lawkeys.hcfcore.team;

import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.startup.StartupGate;
import com.lawkeys.hcfcore.util.WorldPosition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TeamModule}.
 */
class TeamModuleTest {

    @TempDir
    Path tempDir;

    private static Server server;
    private Plugin plugin;
    private LangManager lang;
    private StartupGate gate;
    private TeamModule module;

    @BeforeAll
    static void initServer() {
        server = DummyServer.create();
        try {
            Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, server);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @AfterAll
    static void resetServer() {
        try {
            Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, null);
        } catch (Exception e) {
            // ignore
        }
    }

    @BeforeEach
    void setUp() {
        this.plugin = DummyPlugin.create(server, tempDir.toFile());
        this.lang = new LangManager(plugin);
        this.gate = new StartupGate(plugin, lang);
        this.module = new TeamModule(plugin, lang, gate);
    }

    @Test
    void initialPermissionAndMenuButtonState() {
        List<TeamPermission> perms = module.getPermissions();
        assertFalse(perms.isEmpty());
        // Verify default permissions contain standard actions
        assertTrue(perms.stream().anyMatch(p -> p.key().equals("invite")));
        assertTrue(perms.stream().anyMatch(p -> p.key().equals("kick")));

        // Register custom permission
        TeamPermission customPerm = new TeamPermission("custom_key", "DIAMOND", () -> TeamRole.OFFICER);
        module.registerPermission(customPerm);
        assertTrue(module.getPermissions().stream().anyMatch(p -> p.key().equals("custom_key")));

        // Duplicate registration should be ignored
        module.registerPermission(customPerm);
        assertEquals(perms.size() + 1, module.getPermissions().size());

        // Custom menu button
        TeamMenuButton customBtn = new DummyMenuButton("btn_key");
        module.registerMenuButton(customBtn);
        assertTrue(module.getMenuButtons().stream().anyMatch(b -> b.key().equals("btn_key")));
        module.registerMenuButton(customBtn);
        assertEquals(1, module.getMenuButtons().size());
    }

    @Test
    void strikeCounterAndSystemLandToolHandlers() {
        UUID teamId = UUID.randomUUID();
        assertEquals(0, module.activeStrikes(teamId));

        module.setStrikeCounter(id -> id.equals(teamId) ? 3 : 0);
        assertEquals(3, module.activeStrikes(teamId));

        AtomicBoolean toolCalled = new AtomicBoolean(false);
        module.setSystemLandTool((player, team) -> toolCalled.set(true));
        module.giveSystemLandTool(null, null);
        assertTrue(toolCalled.get());
    }

    @Test
    void readablePlaceholders() {
        Map<String, String> placeholders = Map.of(
                "role", "CO_LEADER",
                "required", "LEADER",
                "other", "value"
        );
        Map<String, String> readable = module.readable(placeholders);
        assertEquals("value", readable.get("other"));
        assertNotNull(readable.get("role"));
        assertNotNull(readable.get("required"));
    }

    @Test
    void enableWithNullDataSourceRunsInMemoryAndSucceeds() throws Exception {
        module.enable(null, 0);
        gate.seal();

        // Give async load task a moment to finish
        Thread.sleep(200);

        assertNotNull(module.getManager());
        assertTrue(gate.isReady());

        module.disable();
    }

    @Test
    void enableWithDataSourceAndSaveIntervalSchedulesAndLoads() throws Exception {
        SQLiteDataSource sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("teams_test.db").toAbsolutePath());

        // Enable with sqlite datasource and 60 second save interval
        module.enable(sqlite, 60);
        gate.seal();

        Thread.sleep(200);

        assertNotNull(module.getManager());
        assertTrue(gate.isReady());

        // Disabling module flushes pending teams and cancels save task
        module.disable();
    }

    @Test
    void positionAndLocationConversions() {
        WorldPosition pos = new WorldPosition("world", 10.0, 64.0, 20.0, 90.0f, 0.0f);
        // toLocation should return empty if world is not loaded in Bukkit
        Optional<Location> locOpt = TeamModule.toLocation(pos);
        assertTrue(locOpt.isEmpty());
    }

    @Test
    void gettersReturnProvidedDependencies() {
        assertSame(plugin, module.getPlugin());
        assertSame(lang, module.getLang());
        assertSame(gate, module.getStartup());
        assertNotNull(module.getSettings());
    }

    // --- Dummy test doubles ---

    private static class DummyServer {
        static Server create() {
            PluginManager pm = (PluginManager) java.lang.reflect.Proxy.newProxyInstance(
                    PluginManager.class.getClassLoader(),
                    new Class<?>[]{PluginManager.class},
                    (proxy, method, args) -> null
            );

            BukkitScheduler scheduler = (BukkitScheduler) java.lang.reflect.Proxy.newProxyInstance(
                    BukkitScheduler.class.getClassLoader(),
                    new Class<?>[]{BukkitScheduler.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("runTaskAsynchronously")) {
                            Runnable runnable = (Runnable) args[1];
                            new Thread(runnable).start();
                            return null;
                        }
                        if (method.getName().equals("runTaskTimerAsynchronously")) {
                            BukkitTask task = (BukkitTask) java.lang.reflect.Proxy.newProxyInstance(
                                    BukkitTask.class.getClassLoader(),
                                    new Class<?>[]{BukkitTask.class},
                                    (taskProxy, taskMethod, taskArgs) -> null
                            );
                            return task;
                        }
                        if (method.getName().equals("runTask")) {
                            Runnable runnable = (Runnable) args[1];
                            runnable.run();
                            return null;
                        }
                        return null;
                    }
            );

            CommandMap commandMap = (CommandMap) java.lang.reflect.Proxy.newProxyInstance(
                    CommandMap.class.getClassLoader(),
                    new Class<?>[]{CommandMap.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("register")) {
                            return true;
                        }
                        if (method.getName().equals("getKnownCommands")) {
                            return new HashMap<>();
                        }
                        return null;
                    }
            );

            return (Server) java.lang.reflect.Proxy.newProxyInstance(
                    Server.class.getClassLoader(),
                    new Class<?>[]{Server.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getPluginManager")) {
                            return pm;
                        }
                        if (method.getName().equals("getScheduler")) {
                            return scheduler;
                        }
                        if (method.getName().equals("getCommandMap")) {
                            return commandMap;
                        }
                        if (method.getName().equals("getOnlinePlayers")) {
                            return Collections.emptyList();
                        }
                        if (method.getName().equals("getPluginCommand")) {
                            String cmdName = (String) args[0];
                            Plugin dummyPlugin = DummyPlugin.create(null, null);
                            try {
                                Constructor<PluginCommand> ctor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
                                ctor.setAccessible(true);
                                return ctor.newInstance(cmdName, dummyPlugin);
                            } catch (Exception e) {
                                return null;
                            }
                        }
                        if (method.getName().equals("getLogger")) {
                            return Logger.getLogger("ServerLogger");
                        }
                        if (method.getName().equals("getUnsafe")) {
                            return null;
                        }
                        return null;
                    }
            );
        }
    }

    private static class DummyPlugin {
        static Plugin create(Server server, File dataFolder) {
            PluginDescriptionFile description = new PluginDescriptionFile("HCFCore", "1.0", "com.lawkeys.hcfcore.HCFCore");
            return (Plugin) java.lang.reflect.Proxy.newProxyInstance(
                    Plugin.class.getClassLoader(),
                    new Class<?>[]{Plugin.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getLogger")) {
                            return Logger.getLogger("TestLogger");
                        }
                        if (method.getName().equals("getServer")) {
                            return server;
                        }
                        if (method.getName().equals("getDescription")) {
                            return description;
                        }
                        if (method.getName().equals("getName")) {
                            return "HCFCore";
                        }
                        if (method.getName().equals("getDataFolder")) {
                            return dataFolder != null ? dataFolder : new File(".");
                        }
                        return null;
                    }
            );
        }
    }

    private static class DummyMenuButton implements TeamMenuButton {
        private final String key;

        DummyMenuButton(String key) {
            this.key = key;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public String defaultIcon() {
            return "PAPER";
        }

        @Override
        public String name(Team team, Player viewer) {
            return "Test Button";
        }

        @Override
        public List<String> lore(Team team, Player viewer) {
            return List.of("Lore");
        }

        @Override
        public void click(Team team, Player viewer, ClickType click) {}
    }
}
