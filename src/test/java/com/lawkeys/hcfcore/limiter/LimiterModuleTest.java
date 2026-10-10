package com.lawkeys.hcfcore.limiter;

import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.startup.StartupGate;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for LimiterModule functionality including enable/disable lifecycle. */
class LimiterModuleTest {

    @TempDir
    Path tempDir;

    private DummyPlugin plugin;
    private LangManager lang;
    private StartupGate startup;

    @BeforeEach
    void setUp() {
        plugin = new DummyPlugin();
        Bukkit.setServer(plugin.server);
        lang = new LangManager(plugin);
        startup = new StartupGate(plugin, lang);
    }

    @Test
    void constructorAndGetters() {
        LimiterModule module = new LimiterModule(plugin, lang, startup, null);

        assertSame(plugin, module.getPlugin());
        assertSame(lang, module.getLang());
        assertNotNull(module.getSettings());
        assertNotNull(module.getClaimBlocks());
        assertFalse(module.getClaimBlocks().isActive());
    }

    @Test
    void keyNormalizationForKeyed() {
        Keyed keyed = () -> NamespacedKey.minecraft("speed");
        assertEquals("minecraft:speed", LimiterModule.key(keyed));
    }

    @Test
    void displayFormatting() {
        assertEquals("redstone wire", LimiterModule.display("REDSTONE_WIRE"));
        assertEquals("hopper", LimiterModule.display("HOPPER"));
    }

    @Test
    void enableWithoutClaimsAndNullDataSource() {
        LimiterModule module = new LimiterModule(plugin, lang, startup, null);
        module.enable(null, 0);

        assertEquals(1, plugin.pluginManager.registeredListeners.size());
        module.disable();
    }

    @Test
    void enableWithDataSourceAndNullClaims() throws Exception {
        SQLiteDataSource sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("limiter_test.db").toAbsolutePath());

        LimiterModule module = new LimiterModule(plugin, lang, startup, null);
        module.enable(sqlite, 60);

        assertEquals(1, plugin.pluginManager.registeredListeners.size());

        // startup gate state should remain READY/unblocked since claims is null
        startup.seal();
        assertTrue(startup.isReady());

        module.disable();
    }

    @Test
    void disableWithoutEnableDoesNotThrow() {
        LimiterModule module = new LimiterModule(plugin, lang, startup, null);
        module.disable();
    }

    @Test
    void isLimitedChecksClaimBlocks() {
        LimiterModule module = new LimiterModule(plugin, lang, startup, null);
        assertFalse(module.isLimited(org.bukkit.Material.HOPPER));
    }

    private static class DummyPlugin implements Plugin {
        private final Logger logger = Logger.getLogger("DummyPlugin");
        final DummyPluginManager pluginManager = new DummyPluginManager();
        final DummyServer server = new DummyServer(pluginManager);

        @Override
        public Logger getLogger() {
            return logger;
        }

        @Override
        public Server getServer() {
            return server;
        }

        @Override
        public java.io.File getDataFolder() {
            return new java.io.File("build/tmp/test-data");
        }

        @Override
        public org.bukkit.configuration.file.FileConfiguration getConfig() {
            return new org.bukkit.configuration.file.YamlConfiguration();
        }

        @Override
        public java.io.InputStream getResource(String filename) {
            return null;
        }

        @Override
        public void saveConfig() {}

        @Override
        public void saveDefaultConfig() {}

        @Override
        public void saveResource(String resourcePath, boolean replace) {}

        @Override
        public void reloadConfig() {}

        @Override
        public org.bukkit.plugin.PluginDescriptionFile getDescription() {
            return null;
        }

        @Override
        public org.bukkit.generator.ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
            return null;
        }

        @Override
        public org.bukkit.generator.BiomeProvider getDefaultBiomeProvider(String worldName, String id) {
            return null;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void onDisable() {}

        @Override
        public void onLoad() {}

        @Override
        public void onEnable() {}

        @Override
        public boolean isNottable() {
            return false;
        }

        @Override
        public void setNottable(boolean nottable) {}

        @Override
        public boolean isNillable() {
            return false;
        }

        @Override
        public void setNillable(boolean nillable) {}

        @Override
        public org.bukkit.plugin.PluginLoader getPluginLoader() {
            return null;
        }

        @Override
        public String getName() {
            return "DummyPlugin";
        }

        @Override
        public io.papermc.paper.plugin.configuration.PluginMeta getPluginMeta() {
            return null;
        }

        @Override
        public org.bukkit.configuration.file.FileConfiguration getLifecycleConfiguration() {
            return null;
        }
    }

    private static class DummyTask implements BukkitTask {
        private final Plugin plugin;
        private final int id;
        private boolean cancelled;

        DummyTask(Plugin plugin, int id) {
            this.plugin = plugin;
            this.id = id;
        }

        @Override public int getTaskId() { return id; }
        @Override public Plugin getOwner() { return plugin; }
        @Override public boolean isSync() { return true; }
        @Override public boolean isCancelled() { return cancelled; }
        @Override public void cancel() { cancelled = true; }
    }

    private static class DummyScheduler implements BukkitScheduler {
        private int nextId = 1;

        @Override
        public BukkitTask runTask(Plugin plugin, Runnable task) {
            task.run();
            return new DummyTask(plugin, nextId++);
        }

        @Override
        public BukkitTask runTaskAsynchronously(Plugin plugin, Runnable task) {
            task.run();
            return new DummyTask(plugin, nextId++);
        }

        @Override
        public BukkitTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period) {
            return new DummyTask(plugin, nextId++);
        }

        @Override
        public BukkitTask runTaskTimerAsynchronously(Plugin plugin, Runnable task, long delay, long period) {
            return new DummyTask(plugin, nextId++);
        }

        @Override
        public BukkitTask runTaskLater(Plugin plugin, Runnable task, long delay) {
            return new DummyTask(plugin, nextId++);
        }

        @Override public int scheduleSyncDelayedTask(Plugin plugin, Runnable task, long delay) { return nextId++; }
        @Override public int scheduleSyncDelayedTask(Plugin plugin, Runnable task) { return nextId++; }
        @Override public int scheduleSyncRepeatingTask(Plugin plugin, Runnable task, long delay, long period) { return nextId++; }
        @Override public int scheduleAsyncDelayedTask(Plugin plugin, Runnable task, long delay) { return nextId++; }
        @Override public int scheduleAsyncDelayedTask(Plugin plugin, Runnable task) { return nextId++; }
        @Override public int scheduleAsyncRepeatingTask(Plugin plugin, Runnable task, long delay, long period) { return nextId++; }
        @Override public <T> Future<T> callSyncMethod(Plugin plugin, Callable<T> task) { return null; }
        @Override public void cancelTask(int taskId) {}
        @Override public void cancelTasks(Plugin plugin) {}
        @Override public boolean isCurrentlyRunning(int taskId) { return false; }
        @Override public boolean isQueued(int taskId) { return false; }
        @Override public List<BukkitWorker> getActiveWorkers() { return List.of(); }
        @Override public List<BukkitTask> getPendingTasks() { return List.of(); }
        @Override public BukkitTask runTaskLaterAsynchronously(Plugin plugin, Runnable task, long delay) { return new DummyTask(plugin, nextId++); }
        @Override public void runTask(Plugin plugin, java.util.function.Consumer<BukkitTask> task) { task.accept(new DummyTask(plugin, nextId++)); }
        @Override public void runTaskAsynchronously(Plugin plugin, java.util.function.Consumer<BukkitTask> task) { task.accept(new DummyTask(plugin, nextId++)); }
        @Override public void runTaskLater(Plugin plugin, java.util.function.Consumer<BukkitTask> task, long delay) { task.accept(new DummyTask(plugin, nextId++)); }
        @Override public void runTaskLaterAsynchronously(Plugin plugin, java.util.function.Consumer<BukkitTask> task, long delay) { task.accept(new DummyTask(plugin, nextId++)); }
        @Override public void runTaskTimer(Plugin plugin, java.util.function.Consumer<BukkitTask> task, long delay, long period) { task.accept(new DummyTask(plugin, nextId++)); }
        @Override public void runTaskTimerAsynchronously(Plugin plugin, java.util.function.Consumer<BukkitTask> task, long delay, long period) { task.accept(new DummyTask(plugin, nextId++)); }
    }

    private static class DummyServer implements Server {
        private final PluginManager pluginManager;
        private final BukkitScheduler scheduler = new DummyScheduler();

        DummyServer(PluginManager pluginManager) {
            this.pluginManager = pluginManager;
        }

        @Override
        public PluginManager getPluginManager() {
            return pluginManager;
        }

        @Override
        public BukkitScheduler getScheduler() {
            return scheduler;
        }

        // Default interface stubs unsupported operations or nulls for non-tested server calls
        @Override public String getName() { return "DummyServer"; }
        @Override public String getVersion() { return "1.0"; }
        @Override public String getBukkitVersion() { return "1.0"; }
        @Override public java.util.Collection<? extends org.bukkit.entity.Player> getOnlinePlayers() { return List.of(); }
        @Override public int getMaxPlayers() { return 100; }
        @Override public int getPort() { return 25565; }
        @Override public int getViewDistance() { return 10; }
        @Override public String getIp() { return "127.0.0.1"; }
        @Override public String getWorldType() { return "NORMAL"; }
        @Override public boolean getGenerateStructures() { return false; }
        @Override public boolean getAllowEnd() { return false; }
        @Override public boolean getAllowNether() { return false; }
        @Override public boolean getAllowFlight() { return false; }
        @Override public boolean hasWhitelist() { return false; }
        @Override public void setWhitelist(boolean value) {}
        @Override public java.util.Set<org.bukkit.OfflinePlayer> getWhitelistedPlayers() { return java.util.Set.of(); }
        @Override public void reloadWhitelist() {}
        @Override public void broadcastMessage(String message) {}
        @Override public String getUpdateFolder() { return ""; }
        @Override public java.io.File getUpdateFolderFile() { return new java.io.File("."); }
        @Override public long getConnectionThrottle() { return 0; }
        @Override public int getTicksPerAnimalSpawns() { return 0; }
        @Override public int getTicksPerMonsterSpawns() { return 0; }
        @Override public int getTicksPerWaterSpawns() { return 0; }
        @Override public int getTicksPerWaterAmbientSpawns() { return 0; }
        @Override public int getTicksPerWaterUndergroundCreatureSpawns() { return 0; }
        @Override public int getTicksPerAmbientSpawns() { return 0; }
        @Override public int getTicksPerAxolotlSpawns() { return 0; }
        @Override public int getTicksPerDragonFightSpawns() { return 0; }
        @Override public org.bukkit.entity.Player getPlayer(String name) { return null; }
        @Override public org.bukkit.entity.Player getPlayerExact(String name) { return null; }
        @Override public List<org.bukkit.entity.Player> matchPlayer(String name) { return List.of(); }
        @Override public org.bukkit.entity.Player getPlayer(java.util.UUID id) { return null; }
        @Override public java.util.List<org.bukkit.World> getWorlds() { return List.of(); }
        @Override public org.bukkit.World createWorld(org.bukkit.WorldCreator creator) { return null; }
        @Override public boolean unloadWorld(String name, boolean save) { return false; }
        @Override public boolean unloadWorld(org.bukkit.World world, boolean save) { return false; }
        @Override public org.bukkit.World getWorld(String name) { return null; }
        @Override public org.bukkit.World getWorld(java.util.UUID uid) { return null; }
        @Override public org.bukkit.map.MapView getMap(int id) { return null; }
        @Override public org.bukkit.map.MapView createMap(org.bukkit.World world) { return null; }
        @Override public org.bukkit.map.MapView getMap(short id) { return null; }
        @Override public void reload() {}
        @Override public void reloadData() {}
        @Override public Logger getLogger() { return Logger.getLogger("Server"); }
        @Override public org.bukkit.command.PluginCommand getPluginCommand(String name) { return null; }
        @Override public void savePlayers() {}
        @Override public boolean dispatchCommand(org.bukkit.command.CommandSender sender, String commandLine) { return false; }
        @Override public boolean addRecipe(org.bukkit.inventory.Recipe recipe) { return false; }
        @Override public List<org.bukkit.inventory.Recipe> getRecipesFor(org.bukkit.inventory.ItemStack result) { return List.of(); }
        @Override public java.util.Iterator<org.bukkit.inventory.Recipe> recipeIterator() { return List.of().iterator(); }
        @Override public void clearRecipes() {}
        @Override public void resetRecipes() {}
        @Override public java.util.Map<String, String me.clip.placeholderapi.PlaceholderHook> getPlaceholders() { return java.util.Map.of(); }
        @Override public java.util.Map<String, String> getCommandAliases() { return java.util.Map.of(); }
        @Override public int getSpawnRadius() { return 0; }
        @Override public void setSpawnRadius(int value) {}
        @Override public boolean getOnlineMode() { return false; }
        @Override public boolean getAllowFlightMode() { return false; }
        @Override public boolean isHardcore() { return false; }
        @Override public void shutdown() {}
        @Override public int broadcast(String message, String permission) { return 0; }
        @Override public int broadcast(net.kyori.adventure.text.Component message) { return 0; }
        @Override public int broadcast(net.kyori.adventure.text.Component message, String permission) { return 0; }
        @Override public org.bukkit.OfflinePlayer getOfflinePlayer(String name) { return null; }
        @Override public org.bukkit.OfflinePlayer getOfflinePlayer(java.util.UUID id) { return null; }
        @Override public org.bukkit.OfflinePlayer getOfflinePlayerIfCached(String name) { return null; }
        @Override public java.util.Set<String> getIPBans() { return java.util.Set.of(); }
        @Override public void banIP(String address) {}
        @Override public void unbanIP(String address) {}
        @Override public java.util.Set<org.bukkit.OfflinePlayer> getBannedPlayers() { return java.util.Set.of(); }
        @Override public java.util.Set<org.bukkit.OfflinePlayer> getOperators() { return java.util.Set.of(); }
        @Override public org.bukkit.GameMode getDefaultGameMode() { return org.bukkit.GameMode.SURVIVAL; }
        @Override public void setDefaultGameMode(org.bukkit.GameMode mode) {}
        @Override public org.bukkit.command.ConsoleCommandSender getConsoleSender() { return null; }
        @Override public java.io.File getWorldContainer() { return new java.io.File("."); }
        @Override public org.bukkit.OfflinePlayer[] getOfflinePlayers() { return new org.bukkit.OfflinePlayer[0]; }
        @Override public org.bukkit.messenger.Messenger getMessenger() { return null; }
        @Override public org.bukkit.help.HelpMap getHelpMap() { return null; }
        @Override public org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder owner, org.bukkit.event.inventory.InventoryType type) { return null; }
        @Override public org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder owner, org.bukkit.event.inventory.InventoryType type, net.kyori.adventure.text.Component title) { return null; }
        @Override public org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder owner, int size) { return null; }
        @Override public org.bukkit.inventory.Inventory createInventory(org.bukkit.inventory.InventoryHolder owner, int size, net.kyori.adventure.text.Component title) { return null; }
        @Override public org.bukkit.inventory.Merchant createMerchant(net.kyori.adventure.text.Component title) { return null; }
        @Override public int MonsterSpawnLimit() { return 0; }
        @Override public int AnimalSpawnLimit() { return 0; }
        @Override public int WaterAnimalSpawnLimit() { return 0; }
        @Override public int WaterAmbientSpawnLimit() { return 0; }
        @Override public int WaterUndergroundCreatureSpawnLimit() { return 0; }
        @Override public int AmbientSpawnLimit() { return 0; }
        @Override public int AxolotlSpawnLimit() { return 0; }
        @Override public boolean isPrimaryThread() { return true; }
        @Override public net.kyori.adventure.text.Component motd() { return net.kyori.adventure.text.Component.empty(); }
        @Override public String getMotd() { return ""; }
        @Override public net.kyori.adventure.text.Component shutdownMessage() { return net.kyori.adventure.text.Component.empty(); }
        @Override public String getShutdownMessage() { return ""; }
        @Override public org.bukkit.Warning.WarningState getWarningState() { return org.bukkit.Warning.WarningState.DEFAULT; }
        @Override public org.bukkit.inventory.ItemFactory getItemFactory() { return null; }
        @Override public org.bukkit.scoreboard.ScoreboardManager getScoreboardManager() { return null; }
        @Override public org.bukkit.structure.StructureManager getStructureManager() { return null; }
        @Override public org.bukkit.inventory.ItemStack createExplorerMap(org.bukkit.World world, org.bukkit.Location location, org.bukkit.generator.structure.StructureType structureType) { return null; }
        @Override public org.bukkit.inventory.ItemStack createExplorerMap(org.bukkit.World world, org.bukkit.Location location, org.bukkit.generator.structure.StructureType structureType, int radius, boolean findUnexplored) { return null; }
        @Override public boolean getHideOnlinePlayersMode() { return false; }
        @Override public boolean isPVPEnabled() { return true; }
        @Override public boolean getAllowFlight() { return false; }
        @Override public void reloadData(java.util.Collection<String> collection) {}
        @Override public void updateRecipes() {}
        @Override public boolean isCommandBlockEnabled() { return false; }
        @Override public int getContainerCount() { return 0; }
        @Override public int getIdleTimeout() { return 0; }
        @Override public void setIdleTimeout(int threshold) {}
        @Override public org.bukkit.UnsafeValues getUnsafe() { return null; }
        @Override public java.awt.image.BufferedImage getServerIcon() { return null; }
        @Override public org.bukkit.profile.PlayerProfile createProfile(java.util.UUID uuid) { return null; }
        @Override public org.bukkit.profile.PlayerProfile createProfile(String name) { return null; }
        @Override public org.bukkit.profile.PlayerProfile createProfile(java.util.UUID uuid, String name) { return null; }
        @Override public org.bukkit.profile.PlayerProfile createProfileExact(java.util.UUID uuid, String name) { return null; }
        @Override public double[] getTPS() { return new double[]{20.0, 20.0, 20.0}; }
        @Override public long[] getTickTimes() { return new long[0]; }
        @Override public double getAverageTickTime() { return 0.0; }
        @Override public org.bukkit.command.CommandMap getCommandMap() { return null; }
        @Override public org.bukkit.advancement.Advancement getAdvancement(NamespacedKey key) { return null; }
        @Override public java.util.Iterator<org.bukkit.advancement.Advancement> advancementIterator() { return List.<org.bukkit.advancement.Advancement>of().iterator(); }
        @Override public org.bukkit.block.data.BlockData createBlockData(String data) { return null; }
        @Override public org.bukkit.block.data.BlockData createBlockData(org.bukkit.Material material) { return null; }
        @Override public org.bukkit.block.data.BlockData createBlockData(org.bukkit.Material material, String data) { return null; }
        @Override public org.bukkit.block.data.BlockData createBlockData(org.bukkit.Material material, java.util.function.Consumer<org.bukkit.block.data.BlockData> consumer) { return null; }
        @Override public org.bukkit.block.data.BlockData createBlockData(String data, org.bukkit.Material material) { return null; }
        @Override public <T extends Keyed> org.bukkit.Registry<T> getRegistry(Class<T> type) { return null; }
        @Override public io.papermc.paper.datapack.DatapackManager getDatapackManager() { return null; }
        @Override public io.papermc.paper.plugin.Permissionmanager getPermissionManager() { return null; }
        @Override public <T extends org.bukkit.Keyed> T get(org.bukkit.RegistryKey<T> registryKey, NamespacedKey namespacedKey) { return null; }
        @Override public <T extends org.bukkit.Keyed> org.bukkit.Registry<T> getRegistry(org.bukkit.RegistryKey<T> registryKey) { return null; }
        @Override public boolean isStopping() { return false; }
        @Override public io.papermc.paper.threadedregions.scheduler.AsyncScheduler getAsyncScheduler() { return null; }
        @Override public io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler getGlobalRegionScheduler() { return null; }
        @Override public io.papermc.paper.threadedregions.scheduler.RegionScheduler getRegionScheduler() { return null; }
        @Override public io.papermc.paper.entity.TeleportFlag.EntityState getEntityState() { return null; }
        @Override public int getSimulationDistance() { return 10; }
        @Override public boolean getHideOnlinePlayers() { return false; }
        @Override public org.bukkit.entity.Entity getEntity(java.util.UUID uuid) { return null; }
        @Override public void sendPluginMessage(Plugin source, String channel, byte[] message) {}
        @Override public java.util.Set<String> getListeningPluginChannels() { return java.util.Set.of(); }
    }

    private static class DummyPluginManager implements PluginManager {
        final List<Listener> registeredListeners = new ArrayList<>();

        @Override
        public void registerEvents(Listener listener, Plugin plugin) {
            registeredListeners.add(listener);
        }

        @Override public void registerInterface(Class<? extends org.bukkit.plugin.EventExecutor> executor) {}
        @Override public void registerEvent(Class<? extends org.bukkit.event.Event> event, Listener listener, org.bukkit.event.EventPriority priority, org.bukkit.plugin.EventExecutor executor, Plugin plugin) {}
        @Override public void registerEvent(Class<? extends org.bukkit.event.Event> event, Listener listener, org.bukkit.event.EventPriority priority, org.bukkit.plugin.EventExecutor executor, Plugin plugin, boolean ignoreCancelled) {}
        @Override public void enablePlugin(Plugin plugin) {}
        @Override public void disablePlugin(Plugin plugin) {}
        @Override public void disablePlugins() {}
        @Override public void clearPlugins() {}
        @Override public void callEvent(org.bukkit.event.Event event) {}
        @Override public Plugin getPlugin(String name) { return null; }
        @Override public Plugin[] getPlugins() { return new Plugin[0]; }
        @Override public boolean isPluginEnabled(String name) { return true; }
        @Override public boolean isPluginEnabled(Plugin plugin) { return true; }
        @Override public void loadPlugin(java.io.File file) {}
        @Override public Plugin[] loadPlugins(java.io.File directory) { return new Plugin[0]; }
        @Override public void unsubscribeFromPermission(String permission, org.bukkit.permissions.Permissible permissible) {}
        @Override public void unsubscribeFromDefaultPerms(boolean op, org.bukkit.permissions.Permissible permissible) {}
        @Override public void subscribeToPermission(String permission, org.bukkit.permissions.Permissible permissible) {}
        @Override public void subscribeToDefaultPerms(boolean op, org.bukkit.permissions.Permissible permissible) {}
        @Override public java.util.Set<org.bukkit.permissions.Permission> getPermissions() { return java.util.Set.of(); }
        @Override public void removePermission(org.bukkit.permissions.Permission perm) {}
        @Override public void removePermission(String name) {}
        @Override public void addPermission(org.bukkit.permissions.Permission perm) {}
        @Override public org.bukkit.permissions.Permission getPermission(String name) { return null; }
        @Override public java.util.Set<org.bukkit.permissions.PermissionAttachmentInfo> getDefaultPermissions(boolean op) { return java.util.Set.of(); }
        @Override public void recalculatePermissionDefaults(org.bukkit.permissions.Permission perm) {}
        @Override public java.util.Set<org.bukkit.permissions.Permissible> getPermissionSubscriptions(String permission) { return java.util.Set.of(); }
        @Override public java.util.Set<org.bukkit.permissions.Permissible> getDefaultPermSubscriptions(boolean op) { return java.util.Set.of(); }
        @Override public boolean useTimings() { return false; }
    }
}
