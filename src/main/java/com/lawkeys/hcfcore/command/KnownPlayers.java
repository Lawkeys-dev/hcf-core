package com.lawkeys.hcfcore.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Players the server knows by name, online or not - for staff and admin commands
 * that act on somebody offline. A player command looks people up through
 * {@link VisiblePlayers} instead: this one sees vanished staff.
 *
 * <p>Offline names go through the server's name cache only. Never
 * {@code Bukkit#getOfflinePlayer(String)}, whose javadoc warns of a blocking web
 * request, nor {@code getOfflinePlayers()}, which lists the player data folder at
 * every call (CONTRIBUTING.md). A name the cache does not know is nobody, as a typo is.
 */
public final class KnownPlayers {

    private KnownPlayers() {
    }

    /** @return the id of the player of exactly that name, online or in the name cache */
    public static Optional<UUID> idOf(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return Optional.of(online.getUniqueId());
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached == null ? Optional.empty() : Optional.of(cached.getUniqueId());
    }
}
