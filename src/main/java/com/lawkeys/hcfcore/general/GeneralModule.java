package com.lawkeys.hcfcore.general;

import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.general.command.BasicsCommand;
import com.lawkeys.hcfcore.general.command.MessageCommand;
import com.lawkeys.hcfcore.general.command.TeleportCommand;
import com.lawkeys.hcfcore.general.command.ToolboxCommand;
import com.lawkeys.hcfcore.general.listener.GodModeListener;
import com.lawkeys.hcfcore.general.listener.MessageListener;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.warmup.WarmupModule;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/**
 * The general utility commands of FEATURES.md section 10.
 *
 * <p>Nothing here is HCF-specific, which is why it is its own module rather than
 * scattered through the others - and why it can be turned off whole on a server
 * that already runs an essentials plugin, which most do.
 */
public final class GeneralModule {

    /** Warmup kinds, which are what tell one countdown from another. */
    public static final String WARMUP_SPAWN = "spawn";
    public static final String WARMUP_LOGOUT = "logout";

    private final Plugin plugin;
    private final LangManager lang;
    private final ClaimModule claims;
    private final WarmupModule warmups;

    private volatile GeneralSettings settings = GeneralSettings.defaults();
    private volatile SpawnGuard spawnGuard = SpawnGuard.ALLOW;
    private volatile LogoutGuard logoutGuard = LogoutGuard.ALLOW;
    private final PrivateMessages messages = new PrivateMessages();
    /** Players in /god mode; for the session only, a logout ends it. */
    private final java.util.Set<java.util.UUID> gods = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile java.util.function.BiConsumer<java.util.UUID, Boolean> messagesToggled = (player, on) -> {
    };

    /**
     * @param claims  may be {@code null}; it supplies the combat guard, nothing more
     * @param warmups the countdowns shared with {@code claim/}, so a {@code /spawn}
     *                cannot run on top of a {@code /team hq}
     */
    public GeneralModule(Plugin plugin, LangManager lang, ClaimModule claims, WarmupModule warmups) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lang = Objects.requireNonNull(lang, "lang");
        this.claims = claims;
        this.warmups = Objects.requireNonNull(warmups, "warmups");
    }

    public LangManager getLang() {
        return lang;
    }

    public GeneralSettings getSettings() {
        return settings;
    }

    public PrivateMessages getMessages() {
        return messages;
    }

    /**
     * Told when a player switches private messages with {@code /togglepm}.
     * Installed by the player settings module, which stores the choice, so
     * {@code /togglepm} and {@code /settings} are one switch; until then the choice
     * lasts the session, as it always did.
     */
    public void setMessagesToggledObserver(java.util.function.BiConsumer<java.util.UUID, Boolean> observer) {
        this.messagesToggled = Objects.requireNonNull(observer, "observer");
    }

    /** Called by {@code /togglepm}. */
    public void messagesToggled(Player player, boolean on) {
        messagesToggled.accept(player.getUniqueId(), on);
    }

    public WarmupModule getWarmups() {
        return warmups;
    }

    public void enable() {
        reloadSettings();
        plugin.getServer().getPluginManager().registerEvents(new MessageListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new GodModeListener(this), plugin);

        TeleportCommand teleports = new TeleportCommand(this);
        register("spawn", teleports);
        register("world", teleports);
        register("top", teleports);

        MessageCommand messaging = new MessageCommand(this);
        register("msg", messaging);
        register("reply", messaging);
        register("togglepm", messaging);
        register("ignore", messaging);

        ToolboxCommand toolbox = new ToolboxCommand(this);
        for (String name : new String[] {"heal", "kill", "gamemode", "rename", "more", "repair", "ping", "logout"}) {
            register(name, toolbox);
        }

        BasicsCommand basics = new BasicsCommand(this);
        BasicsCommand.commands().forEach(name -> register(name, basics));
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

    /** Installs the spawn guard; {@code events/} answers it for the King. */
    public void setSpawnGuard(SpawnGuard spawnGuard) {
        this.spawnGuard = Objects.requireNonNull(spawnGuard, "spawnGuard");
    }

    /** @return {@code true} when this player may not be sent to spawn now, having been told why */
    public boolean refusesSpawn(Player player) {
        return spawnGuard.refuse(player);
    }

    /**
     * Installs the logout guard. Called at startup with {@code pvp/}'s answer; until
     * then {@link LogoutGuard#ALLOW} refuses nobody.
     */
    public void setLogoutGuard(LogoutGuard logoutGuard) {
        this.logoutGuard = Objects.requireNonNull(logoutGuard, "logoutGuard");
    }

    /** @return {@code true} when this player may not log out now, having been told why */
    public boolean refusesLogout(Player player) {
        return logoutGuard.refuse(player);
    }

    /**
     * Starts the {@code /spawn} countdown.
     *
     * @return {@code false} if a countdown of any kind is already running for them
     */
    public boolean beginSpawnWarmup(Player player, long seconds) {
        // Asked again at the end: hitting somebody tags the attacker without hurting
        // them, so a countdown can survive into a fight it must not end.
        return warmups.begin(player, WARMUP_SPAWN, seconds, GeneralMessages.SPAWN_CANCELLED, arriving -> {
            if (!isBlockedByCombat(arriving) && !refusesSpawn(arriving)) {
                sendToSpawn(arriving);
            }
        });
    }

    /**
     * Starts the {@code /logout} countdown.
     *
     * @return {@code false} if a countdown of any kind is already running for them
     */
    public boolean beginLogoutWarmup(Player player, long seconds) {
        return warmups.begin(player, WARMUP_LOGOUT, seconds, GeneralMessages.LOGOUT_CANCELLED,
                leaving -> {
                    // Asked again at the end: a blow struck during the countdown tags the
                    // attacker, and a kick while tagged is a death (LogoutGuard).
                    if (!refusesLogout(leaving)) {
                        kickLoggingOut(leaving);
                    }
                });
    }

    /** The safe logout's own kick, with its own reason. */
    public void kickLoggingOut(Player player) {
        player.kick(com.lawkeys.hcfcore.util.LegacyText.SERIALIZER.deserialize(lang.get(GeneralMessages.LOGOUT_KICK)));
    }

    /**
     * Sends a player to spawn.
     *
     * <p>The configured world if one is named, otherwise the world they are in -
     * which is what a multi-world server wants, and what a single-world server
     * cannot tell apart.
     */
    public void sendToSpawn(Player player) {
        String configured = settings.spawn().world();
        World world = configured.isBlank() ? player.getWorld() : Bukkit.getWorld(configured);
        if (world == null) {
            plugin.getLogger().warning("general.yml: spawn.world names '" + configured
                    + "', which does not exist; using the player's own world.");
            world = player.getWorld();
        }
        player.teleportAsync(world.getSpawnLocation());
        lang.send(player, GeneralMessages.SPAWN_ARRIVED);
    }

    /**
     * @return whether a combat tag stops this teleport, the player having been told
     *
     * <p>Asked through the claim module's existing {@code TeleportGuard}, which
     * {@code pvp/} already answers - so this module needs to know nothing about
     * combat, and a server running without the PvP module simply never refuses.
     */
    public boolean isBlockedByCombat(Player player) {
        return claims != null && claims.getTeleportGuard().blockTeleport(player.getUniqueId());
    }

    public void reloadSettings() {
        var section = ConfigManager.loadFile(plugin, "general.yml");
        this.settings = GeneralSettingsLoader.load(section,
                warning -> plugin.getLogger().warning("general.yml: " + warning));
    }

    /** @return whether this player is in /god mode */
    public boolean isGod(java.util.UUID player) {
        return gods.contains(player);
    }

    public void setGod(java.util.UUID player, boolean god) {
        if (god) {
            gods.add(player);
        } else {
            gods.remove(player);
        }
    }

    /** @return whether the player is in god mode now */
    public boolean toggleGod(java.util.UUID player) {
        if (gods.remove(player)) {
            return false;
        }
        gods.add(player);
        return true;
    }

    public void disable() {
        // The countdowns belong to the warmup module, which stops its own.
        gods.clear();
    }
}
