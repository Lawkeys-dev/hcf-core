package com.lawkeys.hcfcore.hologram;

import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.startup.StartupBarrier;
import com.lawkeys.hcfcore.startup.StartupGate;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HologramModuleTest {

    @TempDir
    Path tempDir;

    private Plugin plugin;
    private LangManager lang;
    private StartupBarrier barrier;
    private StartupGate startup;
    private HologramModule module;

    @BeforeEach
    void setUp() throws Exception {
        plugin = createMockPlugin(tempDir.toFile());
        lang = new LangManager(plugin);
        barrier = new StartupBarrier(StartupBarrier.Observer.NONE);
        startup = new StartupGate(plugin, lang);

        Field barrierField = StartupGate.class.getDeclaredField("barrier");
        barrierField.setAccessible(true);
        barrierField.set(startup, barrier);

        setupMockBukkitServer();

        module = new HologramModule(plugin, lang, startup, null);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (module != null) {
            module.disable();
        }
        resetBukkitServer();
    }

    private Plugin createMockPlugin(File dataFolder) {
        Logger logger = Logger.getLogger("MockPlugin");
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> {
                    if ("getDataFolder".equals(method.getName())) {
                        return dataFolder;
                    }
                    if ("getLogger".equals(method.getName())) {
                        return logger;
                    }
                    if ("getName".equals(method.getName())) {
                        return "HCFCore";
                    }
                    if ("isEnabled".equals(method.getName())) {
                        return true;
                    }
                    if ("getConfig".equals(method.getName())) {
                        return new YamlConfiguration();
                    }
                    if ("getServer".equals(method.getName())) {
                        return Bukkit.getServer();
                    }
                    return null;
                }
        );
    }

    private void setupMockBukkitServer() throws Exception {
        Server server = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class},
                (proxy, method, args) -> {
                    if ("getPluginManager".equals(method.getName())) {
                        return createPluginManager();
                    }
                    if ("getPluginCommand".equals(method.getName())) {
                        return createPluginCommand("hologram");
                    }
                    if ("getScheduler".equals(method.getName())) {
                        return createScheduler();
                    }
                    if ("getLogger".equals(method.getName())) {
                        return plugin.getLogger();
                    }
                    if ("getName".equals(method.getName())) {
                        return "MockServer";
                    }
                    if ("getVersion".equals(method.getName())) {
                        return "1.0";
                    }
                    if ("getBukkitVersion".equals(method.getName())) {
                        return "1.21-R0.1-SNAPSHOT";
                    }
                    return null;
                }
        );

        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);
    }

    private void resetBukkitServer() throws Exception {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, null);
    }

    private PluginManager createPluginManager() {
        return (PluginManager) Proxy.newProxyInstance(
                PluginManager.class.getClassLoader(),
                new Class<?>[]{PluginManager.class},
                (proxy, method, args) -> null
        );
    }

    private PluginCommand createPluginCommand(String name) throws Exception {
        Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
        constructor.setAccessible(true);
        return constructor.newInstance(name, plugin);
    }

    private BukkitScheduler createScheduler() {
        return (BukkitScheduler) Proxy.newProxyInstance(
                BukkitScheduler.class.getClassLoader(),
                new Class<?>[]{BukkitScheduler.class},
                (proxy, method, args) -> {
                    if ("runTaskAsynchronously".equals(method.getName()) && args != null && args.length >= 2 && args[1] instanceof Runnable task) {
                        task.run();
                        return createDummyTask();
                    }
                    if ("runTask".equals(method.getName()) && args != null && args.length >= 2 && args[1] instanceof Runnable task) {
                        task.run();
                        return createDummyTask();
                    }
                    if ("runTaskTimer".equals(method.getName()) || "runTaskTimerAsynchronously".equals(method.getName())) {
                        return createDummyTask();
                    }
                    return null;
                }
        );
    }

    private BukkitTask createDummyTask() {
        return (BukkitTask) Proxy.newProxyInstance(
                BukkitTask.class.getClassLoader(),
                new Class<?>[]{BukkitTask.class},
                (proxy, method, args) -> null
        );
    }

    @Test
    void constructorAndGetters() {
        assertSame(lang, module.getLang());
        assertSame(startup, module.getStartup());
        assertTrue(module.isEnabled());
        assertNull(module.getHolograms());
    }

    @Test
    void enableWithNullDataSourceUsesNoOpStoreAndSignalsBarrier() {
        module.enable(null, 60L);

        assertNotNull(module.getHolograms());
        assertEquals(StartupBarrier.State.LOADING, barrier.state());
        barrier.seal();
        assertEquals(StartupBarrier.State.READY, barrier.state());
    }

    @Test
    void enableWithDatabaseLoadsHologramsAndSignalsBarrier() throws Exception {
        SQLiteDataSource sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("module_test.db").toAbsolutePath());

        // Pre-populate database using store
        HologramStore store = new com.lawkeys.hcfcore.database.dao.JdbcHologramStore(sqlite, msg -> {});
        store.initSchema();
        store.save(new Hologram("spawn", "world", 10.0, 64.0, -10.0, List.of("&aSpawn Hologram")));

        module.enable(sqlite, 30L);

        assertNotNull(module.getHolograms());
        assertTrue(module.getHolograms().get("spawn").isPresent());
        assertEquals(List.of("&aSpawn Hologram"), module.getHolograms().get("spawn").get().lines());

        barrier.seal();
        assertEquals(StartupBarrier.State.READY, barrier.state());
    }

    @Test
    void enableHandlesDatabaseFailureAndSignalsBarrierFailure() throws Exception {
        SQLiteDataSource badSqlite = new SQLiteDataSource();
        badSqlite.setUrl("jdbc:sqlite:/invalid_path/non_existent.db");

        module.enable(badSqlite, 30L);

        barrier.seal();
        assertEquals(StartupBarrier.State.FAILED, barrier.state());
    }

    @Test
    void addSourceAndRefreshSourcesHandlesBrokenSourceGracefully() {
        AtomicInteger count = new AtomicInteger();
        HologramSource failingSource = () -> {
            count.incrementAndGet();
            throw new RuntimeException("Test exception in source");
        };

        module.addSource(failingSource);
        module.enable(null, 0L);

        assertTrue(module.isEnabled());
    }

    @Test
    void reloadSettingsAndDisableLifecycle() {
        module.enable(null, 30L);
        module.reloadSettings();
        assertTrue(module.isEnabled());

        module.disable();
        module.disable();
    }
}
