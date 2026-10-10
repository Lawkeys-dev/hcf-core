package com.lawkeys.hcfcore.dtr;

import com.lawkeys.hcfcore.api.event.TeamRaidableEvent;
import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.startup.StartupBarrier;
import com.lawkeys.hcfcore.startup.StartupGate;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.warmup.WarmupModule;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DtrModule}.
 */
class DtrModuleTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private File tempFolder;
    private Logger logger;
    private Plugin mockPlugin;
    private Server mockServer;
    private PluginManager mockPluginManager;
    private BukkitScheduler mockScheduler;

    private List<Event> firedEvents;
    private List<Listener> registeredListeners;
    private List<Runnable> asyncTasks;
    private List<Runnable> syncTasks;
    private List<TestTask> timerTasks;
    private List<Player> onlinePlayers;

    private TeamModule teamModule;
    private ClaimModule claimModule;
    private LangManager langManager;
    private StartupGate startupGate;

    @BeforeEach
    void setUp() throws Exception {
        tempFolder = Files.createTempDirectory("dtr-module-test").toFile();
        logger = Logger.getLogger("DtrModuleTest");

        firedEvents = new ArrayList<>();
        registeredListeners = new ArrayList<>();
        asyncTasks = new ArrayList<>();
        syncTasks = new ArrayList<>();
        timerTasks = new ArrayList<>();
        onlinePlayers = new ArrayList<>();

        setupBukkitMocks();

        langManager = new LangManager(mockPlugin);
        startupGate = new StartupGate(mockPlugin, langManager);

        teamModule = new TeamModule(mockPlugin, langManager, startupGate);
        teamModule.enable(null, 0L);
        completeLoadingModule();

        WarmupModule warmupModule = new WarmupModule(mockPlugin, langManager);
        claimModule = new ClaimModule(mockPlugin, teamModule, langManager, startupGate, warmupModule);
        claimModule.enable(null, 0L);
        completeLoadingModule();
    }

    @AfterEach
    void tearDown() throws Exception {
        resetBukkitServer();
        if (tempFolder != null && tempFolder.exists()) {
            deleteRecursively(tempFolder);
        }
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private void resetBukkitServer() throws Exception {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, null);
    }

    private void setupBukkitMocks() throws Exception {
        io.papermc.paper.ServerBuildInfo buildInfo = (io.papermc.paper.ServerBuildInfo) Proxy.newProxyInstance(
                io.papermc.paper.ServerBuildInfo.class.getClassLoader(),
                new Class<?>[]{io.papermc.paper.ServerBuildInfo.class},
                (p, m, a) -> {
                    if ("brandId".equals(m.getName())) {
                        return io.papermc.paper.ServerBuildInfo.BRAND_PAPER_ID;
                    }
                    if ("brandName".equals(m.getName())) {
                        return "Paper";
                    }
                    if ("minecraftVersionId".equals(m.getName())) {
                        return "1.21.4";
                    }
                    if ("minecraftVersionName".equals(m.getName())) {
                        return "1.21.4";
                    }
                    if ("buildNumber".equals(m.getName())) {
                        return java.util.OptionalInt.of(1);
                    }
                    if ("buildTime".equals(m.getName())) {
                        return java.time.Instant.now();
                    }
                    if ("gitCommit".equals(m.getName())) {
                        return java.util.Optional.of("abcdef");
                    }
                    if ("gitBranch".equals(m.getName())) {
                        return java.util.Optional.of("main");
                    }
                    if ("asString".equals(m.getName())) {
                        return "26.2-1";
                    }
                    return defaultValue(m.getReturnType());
                });

        Class<?> holderClass = Class.forName("io.papermc.paper.ServerBuildInfo$1Holder");
        Field instanceField = holderClass.getDeclaredField("INSTANCE");
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        Object base = unsafe.staticFieldBase(instanceField);
        long offset = unsafe.staticFieldOffset(instanceField);
        unsafe.putObject(base, offset, java.util.Optional.of(buildInfo));

        mockPluginManager = (PluginManager) Proxy.newProxyInstance(
                PluginManager.class.getClassLoader(),
                new Class<?>[]{PluginManager.class},
                (proxy, method, args) -> {
                    if ("registerEvents".equals(method.getName())) {
                        registeredListeners.add((Listener) args[0]);
                        return null;
                    }
                    if ("callEvent".equals(method.getName())) {
                        firedEvents.add((Event) args[0]);
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });

        mockScheduler = (BukkitScheduler) Proxy.newProxyInstance(
                BukkitScheduler.class.getClassLoader(),
                new Class<?>[]{BukkitScheduler.class},
                (proxy, method, args) -> {
                    if ("runTaskAsynchronously".equals(method.getName())) {
                        Runnable task = (Runnable) args[1];
                        asyncTasks.add(task);
                        return createDummyTask();
                    }
                    if ("runTask".equals(method.getName())) {
                        Runnable task = (Runnable) args[1];
                        syncTasks.add(task);
                        return createDummyTask();
                    }
                    if ("runTaskTimerAsynchronously".equals(method.getName())) {
                        Runnable task = (Runnable) args[1];
                        TestTask tt = new TestTask(task, (Long) args[2], (Long) args[3], true);
                        timerTasks.add(tt);
                        return tt;
                    }
                    if ("runTaskTimer".equals(method.getName())) {
                        Runnable task = (Runnable) args[1];
                        TestTask tt = new TestTask(task, (Long) args[2], (Long) args[3], false);
                        timerTasks.add(tt);
                        return tt;
                    }
                    return defaultValue(method.getReturnType());
                });

        mockServer = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class},
                (proxy, method, args) -> {
                    if ("getPluginManager".equals(method.getName())) {
                        return mockPluginManager;
                    }
                    if ("getScheduler".equals(method.getName())) {
                        return mockScheduler;
                    }
                    if ("getLogger".equals(method.getName())) {
                        return logger;
                    }
                    if ("getOnlinePlayers".equals(method.getName())) {
                        return onlinePlayers;
                    }
                    if ("getName".equals(method.getName())) {
                        return "Paper";
                    }
                    if ("getVersion".equals(method.getName())) {
                        return "26.2";
                    }
                    if ("getBukkitVersion".equals(method.getName())) {
                        return "26.2-R0.1-SNAPSHOT";
                    }
                    if ("getServerBuildInfo".equals(method.getName())) {
                        return buildInfo;
                    }
                    return defaultValue(method.getReturnType());
                });

        Bukkit.setServer(mockServer);

        mockPlugin = (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> {
                    if ("getLogger".equals(method.getName())) {
                        return logger;
                    }
                    if ("getServer".equals(method.getName())) {
                        return mockServer;
                    }
                    if ("getDataFolder".equals(method.getName())) {
                        return tempFolder;
                    }
                    if ("getName".equals(method.getName())) {
                        return "hcfcore";
                    }
                    if ("namespace".equals(method.getName())) {
                        return "hcfcore";
                    }
                    if ("getPluginMeta".equals(method.getName())) {
                        return Proxy.newProxyInstance(
                                io.papermc.paper.plugin.configuration.PluginMeta.class.getClassLoader(),
                                new Class<?>[]{io.papermc.paper.plugin.configuration.PluginMeta.class},
                                (p, m, a) -> {
                                    if ("getName".equals(m.getName())) {
                                        return "hcfcore";
                                    }
                                    return defaultValue(m.getReturnType());
                                });
                    }
                    if ("getResource".equals(method.getName())) {
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private BukkitTask createDummyTask() {
        return (BukkitTask) Proxy.newProxyInstance(
                BukkitTask.class.getClassLoader(),
                new Class<?>[]{BukkitTask.class},
                (proxy, method, args) -> {
                    if ("cancel".equals(method.getName())) {
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static class TestTask implements BukkitTask {
        final Runnable runnable;
        final long delay;
        final long period;
        final boolean async;
        boolean cancelled;

        TestTask(Runnable runnable, long delay, long period, boolean async) {
            this.runnable = runnable;
            this.delay = delay;
            this.period = period;
            this.async = async;
        }

        @Override
        public int getTaskId() {
            return 1;
        }

        @Override
        public Plugin getOwner() {
            return null;
        }

        @Override
        public boolean isSync() {
            return !async;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        return null;
    }

    private void completeLoadingModule() {
        while (!asyncTasks.isEmpty()) {
            asyncTasks.remove(0).run();
        }
        while (!syncTasks.isEmpty()) {
            syncTasks.remove(0).run();
        }
    }

    @Nested
    class EnableAndDisable {

        @Test
        void enableInitializesManagerAndRegistersListenersAndTasks() {
            DtrModule module = new DtrModule(mockPlugin, teamModule, claimModule, langManager, startupGate);
            assertNull(module.getManager());

            asyncTasks.clear();
            syncTasks.clear();
            timerTasks.clear();
            registeredListeners.clear();

            module.enable(null, 60L);

            assertNotNull(module.getManager());
            assertSame(teamModule, module.getTeams());
            assertSame(langManager, module.getLang());
            assertNotNull(module.getSettings());

            // Async task for loading DTR states
            assertEquals(1, asyncTasks.size(), "Async load task should be scheduled");
            asyncTasks.remove(0).run();

            // Sync task for priming baseline
            assertEquals(1, syncTasks.size(), "Sync task for baseline priming should be scheduled");
            syncTasks.remove(0).run();

            // Verify claim module's raidability policy was set to module.getManager()
            assertSame(module.getManager(), claimModule.getManager().getRaidabilityPolicy());

            // Verify event listener was registered
            assertFalse(registeredListeners.isEmpty());

            // Verify save task and announce task were scheduled
            assertFalse(timerTasks.isEmpty());

            // Test disable cancels tasks and flushes manager
            module.disable();
            for (TestTask task : timerTasks) {
                assertTrue(task.isCancelled());
            }
        }

        @Test
        void enableWithoutClaimModuleHandlesNullGracefully() {
            DtrModule module = new DtrModule(mockPlugin, teamModule, null, langManager, startupGate);

            asyncTasks.clear();
            syncTasks.clear();

            module.enable(null, 0L);

            assertNotNull(module.getManager());
            assertEquals(1, asyncTasks.size());
            asyncTasks.remove(0).run();

            assertEquals(1, syncTasks.size());
            syncTasks.remove(0).run();
        }

        @Test
        void enableWithFailedLoadTriggersFailedLoadOnGate() {
            StartupGate gateForFailure = new StartupGate(mockPlugin, langManager);

            TeamModule teamModuleFail = new TeamModule(mockPlugin, langManager, gateForFailure);
            teamModuleFail.enable(null, 0L);

            DtrModule module = new DtrModule(mockPlugin, teamModuleFail, null, langManager, gateForFailure);

            asyncTasks.clear();
            syncTasks.clear();

            // Failing DataSource
            DataSource failingSource = (DataSource) Proxy.newProxyInstance(
                    DataSource.class.getClassLoader(),
                    new Class<?>[]{DataSource.class},
                    (proxy, method, args) -> {
                        if ("getConnection".equals(method.getName())) {
                            throw new SQLException("Database connection failed");
                        }
                        return defaultValue(method.getReturnType());
                    });

            module.enable(failingSource, 0L);

            // Execute async task which encounters SQLException and calls load.failed()
            assertEquals(1, asyncTasks.size());
            asyncTasks.remove(0).run();

            // Gate state should be FAILED
            assertEquals(StartupBarrier.State.FAILED, gateForFailure.state());
        }
    }

    @Nested
    class DeathCostAndRaidOverride {

        @Test
        void setAndGetDeathCostPolicy() {
            DtrModule module = new DtrModule(mockPlugin, teamModule, claimModule, langManager, startupGate);

            asyncTasks.clear();
            syncTasks.clear();

            module.enable(null, 0L);
            completeLoadingModule();

            AtomicBoolean costCalled = new AtomicBoolean(false);
            DeathCostPolicy customPolicy = () -> {
                costCalled.set(true);
                return false;
            };

            module.setDeathCostPolicy(customPolicy);

            Team wizards = teamModule.getManager().createTeam(UUID.randomUUID(), "Wizards").getTeam().orElseThrow();
            module.applyDeath(wizards, "Alice");

            assertTrue(costCalled.get());
            assertEquals(1.1, module.getManager().getDtr(wizards), 1e-9, "DTR should not decrease when policy returns false");
        }

        @Test
        void setAndGetRaidOverride() {
            DtrModule module = new DtrModule(mockPlugin, teamModule, claimModule, langManager, startupGate);

            asyncTasks.clear();
            syncTasks.clear();

            module.enable(null, 0L);

            assertSame(RaidOverride.NONE, module.getRaidOverride());

            RaidOverride override = () -> true;
            module.setRaidOverride(override);

            assertSame(override, module.getRaidOverride());
        }
    }

    @Nested
    class AnnouncementsAndPolling {

        @Test
        void pollAnnouncementsDoesNothingBeforeStartupIsReady() {
            DtrModule module = new DtrModule(mockPlugin, teamModule, claimModule, langManager, startupGate);

            asyncTasks.clear();
            syncTasks.clear();

            module.enable(null, 0L);

            assertFalse(startupGate.isReady());
            module.pollAnnouncements();

            assertTrue(firedEvents.isEmpty());
        }

        @Test
        void pollAnnouncementsFiresTeamRaidableEventWhenRaidabilityFlips() {
            DtrModule module = new DtrModule(mockPlugin, teamModule, claimModule, langManager, startupGate);

            asyncTasks.clear();
            syncTasks.clear();

            module.enable(null, 0L);
            completeLoadingModule();

            startupGate.seal();
            assertTrue(startupGate.isReady());

            Team wizards = teamModule.getManager().createTeam(UUID.randomUUID(), "Wizards").getTeam().orElseThrow();

            // Make wizards raidable via applyDeath (which internally calls pollAnnouncements)
            firedEvents.clear();
            module.applyDeath(wizards, "Alice");
            module.applyDeath(wizards, "Bob");

            // Event fired during applyDeath when team flipped to raidable
            assertEquals(1, firedEvents.size());
            assertTrue(firedEvents.get(0) instanceof TeamRaidableEvent);
            TeamRaidableEvent event = (TeamRaidableEvent) firedEvents.get(0);
            assertEquals(wizards.getId(), event.getTeam().getId());
            assertTrue(event.isRaidable());
        }

        @Test
        void applyDeathWithRaiderPassesRaiderToEvent() {
            DtrModule module = new DtrModule(mockPlugin, teamModule, claimModule, langManager, startupGate);

            asyncTasks.clear();
            syncTasks.clear();

            module.enable(null, 0L);
            completeLoadingModule();

            startupGate.seal();
            assertTrue(startupGate.isReady());

            Team wizards = teamModule.getManager().createTeam(UUID.randomUUID(), "Wizards").getTeam().orElseThrow();
            Team raiders = teamModule.getManager().createTeam(UUID.randomUUID(), "Raiders").getTeam().orElseThrow();

            firedEvents.clear();
            // First death
            module.applyDeath(wizards, "Alice", raiders.getId());
            // Second death (makes raidable)
            module.applyDeath(wizards, "Bob", raiders.getId());

            assertEquals(1, firedEvents.size());
            TeamRaidableEvent event = (TeamRaidableEvent) firedEvents.get(0);
            assertTrue(event.isRaidable());
            assertTrue(event.getRaider().isPresent());
            assertEquals(raiders.getId(), event.getRaider().get().getId());
        }
    }
}
