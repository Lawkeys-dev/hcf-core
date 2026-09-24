package com.lawkeys.hcfcore.ui.tab;

import com.google.common.collect.ImmutableMultimap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The grid, sent as the server's own list packets - no library (the project owner's
 * choice, 19/09/2026: nothing to install, and the plugin's licence untouched).
 *
 * <p><strong>The one place the plugin reaches past Paper's API.</strong> Paper has no
 * API for a list entry that is not a player, so the two packets the grid needs -
 * {@code ClientboundPlayerInfoUpdatePacket} and {@code ClientboundPlayerInfoRemovePacket}
 * - are built by reflection, by their Mojang names, which the server has carried
 * unobfuscated since Minecraft 26.1. Everything is looked up once, when the plugin
 * starts; a version whose packets are shaped otherwise fails there, and the classic
 * list is shown instead, never a half-drawn grid.
 *
 * <p>Real players are taken off a viewer's list through Paper's own
 * {@link Player#unlistPlayer}, which the server remembers when it sends that viewer a
 * list later - a join, a player coming out of vanish. {@link #keepUnlisted} checks
 * again every redraw, since a player the viewer could not see (vanished staff) cannot
 * be unlisted until they can.
 */
final class ServerGridTab implements GridTab {

    private final Method getHandle;
    private final Field connection;
    private final Method send;
    private final Constructor<?> updatePacket;
    private final Constructor<?> removePacket;
    private final Constructor<?> entry;
    private final Constructor<?> profile;
    private final Constructor<?> propertyMap;
    private final Object emptyProperties;
    private final Constructor<?> property;
    private final Method asVanilla;
    private final Object survival;
    private final EnumSet<?> add;
    private final EnumSet<?> displayName;

    /** Who sees the grid, and the players this unlisted for them. */
    private final Map<UUID, Set<UUID>> viewers = new ConcurrentHashMap<>();

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ServerGridTab() throws ReflectiveOperationException {
        ClassLoader server = Bukkit.getServer().getClass().getClassLoader();
        Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer", true, server);
        Class<?> serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer", true, server);
        Class<?> packet = Class.forName("net.minecraft.network.protocol.Packet", true, server);
        Class<?> update = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket", true, server);
        Class<?> action = Class.forName(update.getName() + "$Action", true, server);
        Class<?> entryType = Class.forName(update.getName() + "$Entry", true, server);
        Class<?> remove = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket", true, server);
        Class<?> gameProfile = Class.forName("com.mojang.authlib.GameProfile", true, server);
        Class<?> properties = Class.forName("com.mojang.authlib.properties.PropertyMap", true, server);
        Class<?> propertyType = Class.forName("com.mojang.authlib.properties.Property", true, server);
        Class<?> gameType = Class.forName("net.minecraft.world.level.GameType", true, server);
        Class<?> vanillaComponent = Class.forName("net.minecraft.network.chat.Component", true, server);
        Class<?> chatSession = Class.forName("net.minecraft.network.chat.RemoteChatSession$Data", true, server);
        Class<?> paperAdventure = Class.forName("io.papermc.paper.adventure.PaperAdventure", true, server);

        this.getHandle = craftPlayer.getMethod("getHandle");
        this.connection = serverPlayer.getField("connection");
        this.send = connection.getType().getMethod("send", packet);
        this.updatePacket = update.getConstructor(EnumSet.class, List.class);
        this.removePacket = remove.getConstructor(List.class);
        this.entry = entryType.getConstructor(UUID.class, gameProfile, boolean.class, int.class, gameType,
                vanillaComponent, boolean.class, int.class, chatSession);
        this.profile = gameProfile.getConstructor(UUID.class, String.class, properties);
        this.propertyMap = properties.getConstructor(com.google.common.collect.Multimap.class);
        this.emptyProperties = properties.getField("EMPTY").get(null);
        this.property = propertyType.getConstructor(String.class, String.class, String.class);
        this.asVanilla = paperAdventure.getMethod("asVanilla", Component.class);
        this.survival = gameType.getField("SURVIVAL").get(null);
        Class<Enum> actions = (Class<Enum>) action;
        this.add = EnumSet.of(Enum.valueOf(actions, "ADD_PLAYER"), Enum.valueOf(actions, "UPDATE_GAME_MODE"),
                Enum.valueOf(actions, "UPDATE_LISTED"), Enum.valueOf(actions, "UPDATE_LATENCY"),
                Enum.valueOf(actions, "UPDATE_DISPLAY_NAME"), Enum.valueOf(actions, "UPDATE_LIST_ORDER"),
                Enum.valueOf(actions, "UPDATE_HAT"));
        this.displayName = EnumSet.of(Enum.valueOf(actions, "UPDATE_DISPLAY_NAME"));
        // Build one entry now: a constructor that exists but refuses these values
        // fails here, at start-up, rather than on the first player to press Tab.
        cell(0, new Cell(Component.empty(), null), new Look(0, "", ""));
    }

    /** @throws ReflectiveOperationException if this server's packets are not the ones known */
    static GridTab start() throws ReflectiveOperationException {
        return new ServerGridTab();
    }

    /**
     * The names each viewer's chat completes, the players they can see. The game
     * completes a name in the chat from the players on its list - here, the grid's
     * cells - so the real players, taken off it, are handed back to it as chat
     * completions (found in game, 24/09/2026: Tab offered the cells, not the players).
     */
    private final Map<UUID, Set<String>> completions = new ConcurrentHashMap<>();

    /** Brings a viewer's chat completions in line with the players they can see now. */
    private void keepCompletions(Player viewer) {
        Set<String> wanted = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (viewer.canSee(player)) {
                wanted.add(player.getName());
            }
        }
        Set<String> had = completions.computeIfAbsent(viewer.getUniqueId(), id -> ConcurrentHashMap.newKeySet());
        List<String> gone = had.stream().filter(name -> !wanted.contains(name)).toList();
        List<String> added = wanted.stream().filter(name -> !had.contains(name)).toList();
        if (!gone.isEmpty()) {
            viewer.removeCustomChatCompletions(gone);
            gone.forEach(had::remove);
        }
        if (!added.isEmpty()) {
            viewer.addCustomChatCompletions(added);
            had.addAll(added);
        }
    }

    @Override
    public void show(Player viewer, List<Cell> cells, Look look) {
        viewers.put(viewer.getUniqueId(), ConcurrentHashMap.newKeySet());
        keepUnlisted(viewer);
        keepCompletions(viewer);
        List<Object> entries = new ArrayList<>(TabGrid.SIZE);
        for (int cell = 0; cell < TabGrid.SIZE; cell++) {
            entries.add(cell(cell, cell < cells.size() ? cells.get(cell) : new Cell(Component.empty(), null), look));
        }
        send(viewer, construct(updatePacket, add, entries));
    }

    @Override
    public void update(Player viewer, Map<Integer, Cell> texts, Map<Integer, Cell> heads, Look look) {
        if (!viewers.containsKey(viewer.getUniqueId())) {
            return;
        }
        keepUnlisted(viewer);
        keepCompletions(viewer);
        if (!heads.isEmpty()) {
            send(viewer, construct(removePacket, heads.keySet().stream().map(TabGrid::id).toList()));
            List<Object> entries = new ArrayList<>(heads.size());
            heads.forEach((cell, value) -> entries.add(cell(cell, value, look)));
            send(viewer, construct(updatePacket, add, entries));
        }
        if (!texts.isEmpty()) {
            List<Object> entries = new ArrayList<>(texts.size());
            texts.forEach((cell, value) -> entries.add(cell(cell, value, look)));
            send(viewer, construct(updatePacket, displayName, entries));
        }
    }

    /**
     * Takes every real player this viewer can see off their list - themselves too.
     * A player they cannot see is not on it; once they can, the next redraw takes
     * them off.
     */
    private void keepUnlisted(Player viewer) {
        Set<UUID> unlisted = viewers.get(viewer.getUniqueId());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (viewer.canSee(player) && viewer.isListed(player) && viewer.unlistPlayer(player) && unlisted != null) {
                unlisted.add(player.getUniqueId());
            }
        }
    }

    @Override
    public void hide(Player viewer) {
        Set<String> completed = completions.remove(viewer.getUniqueId());
        if (completed != null && !completed.isEmpty()) {
            // Back on the list, the players are completed by the game itself.
            viewer.removeCustomChatCompletions(completed);
        }
        Set<UUID> unlisted = viewers.remove(viewer.getUniqueId());
        if (unlisted == null) {
            return;
        }
        List<UUID> ids = new ArrayList<>(TabGrid.SIZE);
        for (int cell = 0; cell < TabGrid.SIZE; cell++) {
            ids.add(TabGrid.id(cell));
        }
        send(viewer, construct(removePacket, ids));
        // Only those this took off: a player some other plugin unlisted stays so.
        for (UUID id : new HashSet<>(unlisted)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                viewer.listPlayer(player);
            }
        }
    }

    @Override
    public void forget(UUID viewer) {
        viewers.remove(viewer);
        completions.remove(viewer);
        for (Set<UUID> unlisted : viewers.values()) {
            unlisted.remove(viewer);
        }
    }

    @Override
    public void stop() {
        for (UUID id : List.copyOf(viewers.keySet())) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer != null) {
                hide(viewer);
            }
        }
        viewers.clear();
    }

    private Object cell(int cell, Cell value, Look look) {
        String texture = value.skin() != null ? value.skin().texture() : look.texture();
        String signature = value.skin() != null ? value.skin().signature() : look.signature();
        Object props = texture == null || texture.isEmpty()
                ? emptyProperties
                : construct(propertyMap, ImmutableMultimap.of("textures", construct(property, "textures", texture,
                        signature == null || signature.isEmpty() ? null : signature)));
        Object gameProfile = construct(profile, TabGrid.id(cell), TabGrid.name(cell), props);
        return construct(entry, TabGrid.id(cell), gameProfile, true, look.latency(), survival,
                invoke(asVanilla, null, value.text()), true, TabGrid.listOrder(cell), null);
    }

    private void send(Player viewer, Object packet) {
        try {
            Object listener = connection.get(getHandle.invoke(viewer));
            if (listener != null) {
                send.invoke(listener, packet);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Could not send a tab list packet", e);
        }
    }

    private static Object construct(Constructor<?> constructor, Object... arguments) {
        try {
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not build " + constructor.getDeclaringClass().getSimpleName(), e);
        }
    }

    private static Object invoke(Method method, Object target, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not call " + method.getName(), e);
        }
    }
}
