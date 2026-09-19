package com.lawkeys.hcfcore.ui.tab;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The grid, through PacketEvents - the only class that names its types.
 *
 * <p>Every real player is kept off a grid viewer's list: once when the grid is shown,
 * and on every later list packet the server sends that viewer, whose "listed" flag
 * is turned off on its way out. A player who joins is thereby never listed, and
 * nothing depends on the order in which the server and this plugin act on a join.
 * This plugin's own packets are sent silently, past that listener.
 */
final class PacketGridTab implements GridTab {

    private static final EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> ADD = EnumSet.of(
            WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE,
            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY,
            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME,
            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LIST_ORDER,
            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_HAT);

    /** Read on Netty threads by the listener, written on the main thread. */
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
    private final PacketListenerCommon listener;

    private PacketGridTab() {
        this.listener = PacketEvents.getAPI().getEventManager()
                .registerListener(new Unlister(), PacketListenerPriority.HIGH);
    }

    static GridTab start() {
        return new PacketGridTab();
    }

    @Override
    public void show(Player viewer, List<Cell> cells, Look look) {
        viewers.add(viewer.getUniqueId());
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> unlisted = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            WrapperPlayServerPlayerInfoUpdate.PlayerInfo info =
                    new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(player.getUniqueId());
            info.setListed(false);
            unlisted.add(info);
        }
        send(viewer, new WrapperPlayServerPlayerInfoUpdate(
                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED, unlisted));
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries = new ArrayList<>(TabGrid.SIZE);
        for (int cell = 0; cell < TabGrid.SIZE; cell++) {
            entries.add(cell(cell, cell < cells.size() ? cells.get(cell) : new Cell(Component.empty(), null), look));
        }
        send(viewer, new WrapperPlayServerPlayerInfoUpdate(ADD, entries));
    }

    @Override
    public void update(Player viewer, Map<Integer, Cell> texts, Map<Integer, Cell> heads, Look look) {
        if (!viewers.contains(viewer.getUniqueId())) {
            return;
        }
        if (!heads.isEmpty()) {
            send(viewer, new WrapperPlayServerPlayerInfoRemove(heads.keySet().stream().map(TabGrid::id).toList()));
            List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries = new ArrayList<>(heads.size());
            heads.forEach((cell, value) -> entries.add(cell(cell, value, look)));
            send(viewer, new WrapperPlayServerPlayerInfoUpdate(ADD, entries));
        }
        if (!texts.isEmpty()) {
            List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries = new ArrayList<>(texts.size());
            texts.forEach((cell, value) -> entries.add(cell(cell, value, look)));
            send(viewer, new WrapperPlayServerPlayerInfoUpdate(
                    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME, entries));
        }
    }

    @Override
    public void hide(Player viewer) {
        if (!viewers.remove(viewer.getUniqueId())) {
            return;
        }
        List<UUID> ids = new ArrayList<>(TabGrid.SIZE);
        for (int cell = 0; cell < TabGrid.SIZE; cell++) {
            ids.add(TabGrid.id(cell));
        }
        send(viewer, new WrapperPlayServerPlayerInfoRemove(ids));
        // Back as the server has them: a vanished player it keeps off this viewer's
        // list stays off it.
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> listed = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.equals(viewer) || (viewer.canSee(player) && viewer.isListed(player))) {
                WrapperPlayServerPlayerInfoUpdate.PlayerInfo info =
                        new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(player.getUniqueId());
                info.setListed(true);
                listed.add(info);
            }
        }
        send(viewer, new WrapperPlayServerPlayerInfoUpdate(
                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED, listed));
    }

    @Override
    public void forget(UUID viewer) {
        viewers.remove(viewer);
    }

    @Override
    public void stop() {
        for (UUID id : List.copyOf(viewers)) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer != null) {
                hide(viewer);
            }
        }
        viewers.clear();
        PacketEvents.getAPI().getEventManager().unregisterListener(listener);
    }

    private static WrapperPlayServerPlayerInfoUpdate.PlayerInfo cell(int cell, Cell value, Look look) {
        String texture = value.skin() != null ? value.skin().texture() : look.texture();
        String signature = value.skin() != null ? value.skin().signature() : look.signature();
        List<TextureProperty> textures = texture == null || texture.isEmpty()
                ? List.of()
                : List.of(new TextureProperty("textures", texture,
                        signature == null || signature.isEmpty() ? null : signature));
        Component text = value.text();
        UserProfile profile = new UserProfile(TabGrid.id(cell), TabGrid.name(cell), textures);
        return new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(profile, true, look.latency(), GameMode.SURVIVAL,
                text, null, TabGrid.listOrder(cell), true);
    }

    private static void send(Player viewer, PacketWrapper<?> packet) {
        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(viewer, packet);
    }

    /** Turns off "listed" for real players in the list packets a grid viewer is sent. */
    private final class Unlister implements PacketListener {

        @Override
        public void onPacketSend(PacketSendEvent event) {
            if (event.getPacketType() != PacketType.Play.Server.PLAYER_INFO_UPDATE) {
                return;
            }
            User user = event.getUser();
            if (user == null || user.getUUID() == null || !viewers.contains(user.getUUID())) {
                return;
            }
            WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate(event);
            if (!packet.getActions().contains(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED)) {
                return;
            }
            boolean changed = false;
            for (WrapperPlayServerPlayerInfoUpdate.PlayerInfo info : packet.getEntries()) {
                if (info.isListed() && !TabGrid.isCell(info.getProfileId())) {
                    info.setListed(false);
                    changed = true;
                }
            }
            if (changed) {
                event.markForReEncode(true);
            }
        }
    }
}
