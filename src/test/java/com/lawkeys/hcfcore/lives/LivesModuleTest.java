package com.lawkeys.hcfcore.lives;

import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.startup.StartupGate;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for LivesModule enable and initialization without Bukkit server execution. */
class LivesModuleTest {

    @TempDir
    Path tempDir;

    private Plugin plugin;
    private LangManager lang;
    private StartupGate startupGate;

    @BeforeEach
    void setUp() throws Exception {
        plugin = createDummyPlugin();
        setBukkitServer(plugin.getServer());
        lang = new LangManager(plugin);
        startupGate = new StartupGate(plugin, lang);
    }

    private static void setBukkitServer(Server server) throws Exception {
        Field field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(null, server);
    }

    @Test
    void getLangReturnsConfiguredLangManager() {
        LivesModule module = new LivesModule(plugin, lang, startupGate, null);
        assertEquals(lang, module.getLang());
    }

    @Test
    void enableWithNullDataSourceUsesNoOpStoreAndSignalsStartupGate() {
        LivesModule module = new LivesModule(plugin, lang, startupGate, null);
        module.enable(null, 0);

        startupGate.seal();
        assertTrue(startupGate.isReady(), "StartupGate should be ready after lives load succeeded");
        assertNotNull(module.getLives());
    }

    @Test
    void enableWithSqliteDataSourceLoadsDataAndSignalsStartupGate() {
        SQLiteDataSource sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("test_lives.db").toAbsolutePath());

        LivesModule module = new LivesModule(plugin, lang, startupGate, null);
        module.enable(sqlite, 0);

        startupGate.seal();
        assertTrue(startupGate.isReady(), "StartupGate should be ready after loading from SQLite database");

        Lives lives = module.getLives();
        assertNotNull(lives);

        UUID playerId = UUID.randomUUID();
        lives.set(playerId, 5);
        assertEquals(5, lives.get(playerId));
    }

    private static Plugin createDummyPlugin() {
        Logger logger = Logger.getLogger("DummyPlugin");
        Server server = createDummyServer(logger);

        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[] { Plugin.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getLogger")) {
                        return logger;
                    }
                    if (method.getName().equals("getServer")) {
                        return server;
                    }
                    if (method.getName().equals("getName")) {
                        return "HCFCore";
                    }
                    if (method.getName().equals("getDataFolder")) {
                        return new File(".");
                    }
                    if (method.getName().equals("getResource")) {
                        return null;
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return true;
                    }
                    return null;
                }
        );
    }

    private static Server createDummyServer(Logger logger) {
        BukkitScheduler scheduler = createDummyScheduler();
        PluginManager pluginManager = createDummyPluginManager();

        return (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[] { Server.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getScheduler")) {
                        return scheduler;
                    }
                    if (method.getName().equals("getPluginManager")) {
                        return pluginManager;
                    }
                    if (method.getName().equals("getPluginCommand")) {
                        return null;
                    }
                    if (method.getName().equals("getLogger")) {
                        return logger;
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    return null;
                }
        );
    }

    private static BukkitScheduler createDummyScheduler() {
        return (BukkitScheduler) Proxy.newProxyInstance(
                BukkitScheduler.class.getClassLoader(),
                new Class<?>[] { BukkitScheduler.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("runTaskAsynchronously")) {
                        Runnable runnable = (Runnable) args[1];
                        runnable.run();
                        return null;
                    }
                    if (method.getName().equals("runTaskTimerAsynchronously")) {
                        return null;
                    }
                    return null;
                }
        );
    }

    private static PluginManager createDummyPluginManager() {
        return (PluginManager) Proxy.newProxyInstance(
                PluginManager.class.getClassLoader(),
                new Class<?>[] { PluginManager.class },
                (proxy, method, args) -> null
        );
    }
}
