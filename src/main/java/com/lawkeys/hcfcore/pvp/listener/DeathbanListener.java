package com.lawkeys.hcfcore.pvp.listener;

import com.lawkeys.hcfcore.pvp.Deathban;
import com.lawkeys.hcfcore.pvp.PvpMessages;
import com.lawkeys.hcfcore.pvp.PvpModule;
import com.lawkeys.hcfcore.util.ForgivenDeaths;
import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.connection.PlayerLoginConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Applies deathbans, enforces them at login, and punishes combat logging.
 *
 * <p>The login check reads the in-memory cache rather than the database: a login
 * must be answered synchronously, and blocking it on a query would stall the
 * server's accept loop.
 *
 * <p><strong>Why the login check needs no permission.</strong> Paper deprecated
 * {@code PlayerLoginEvent} in 1.21.6 and points at
 * {@link PlayerConnectionValidateLoginEvent} for "pre-login logic (e.g.
 * authentication or ban checks)" - exactly this. The replacement carries a
 * {@link PlayerConnection}, from which only a profile is reachable, so it cannot
 * answer {@code hasPermission}, and Bukkit has no offline permission lookup. That
 * used to keep this listener on the deprecated event, because migrating would have
 * silently dropped the staff bypass.
 *
 * <p>The bypass now lives at the death instead, in
 * {@link PvpModule#applyDeathban}, where a real {@link Player} exists: staff who
 * hold it are never banned, rather than banned and then let back in. The question
 * this event could not answer is therefore no longer asked, and no offline
 * permission seam is needed. The trade is recorded on that method.
 */
public final class DeathbanListener implements Listener {

    private final PvpModule module;

    public DeathbanListener(PvpModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    /**
     * A death another plugin cancels is not one: the player is revived (26.2, the
     * javadoc of {@code EntityDeathEvent}), so no ban, and the tag stays. Without
     * {@code ignoreCancelled} a revived player was banned and kicked.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        // The tag dies with the player: keeping it would punish them twice.
        module.getCombatTags().clear(player.getUniqueId());
        // The King of Kill the King, by default (util/ForgivenDeaths).
        if (!ForgivenDeaths.spares(player.getUniqueId(), ForgivenDeaths.Cost.DEATHBAN)) {
            module.applyDeathban(player);
        }
    }

    /**
     * Refuses the connection of a player who is still deathbanned.
     *
     * <p>Fired twice per join - once in the login phase, once as configuration
     * finishes - and both are checked, since either may be the first point where the
     * profile carries a UUID.
     */
    // HIGH, below the StartupGate's HIGHEST: while the plugin is still loading the
    // gate's "not ready" is the truthful reason, and it sees this refusal and leaves
    // it alone.
    @EventHandler(priority = EventPriority.HIGH)
    public void onValidateLogin(PlayerConnectionValidateLoginEvent event) {
        // Already refused - by the whitelist, a ban, another plugin: that reason is
        // the one to show, and it keeps the player out just the same.
        if (!event.isAllowed()) {
            return;
        }
        UUID uuid = uuidOf(event.getConnection());
        if (uuid == null) {
            // Nothing to look a ban up by. Letting the connection through is the
            // deliberate choice: this event fires again once configuration ends, with
            // an authenticated profile, so the usual path still catches them - and a
            // player who somehow reached the world unchecked is a smaller failure than
            // refusing every login because a profile arrived without an id.
            return;
        }
        Optional<Deathban> ban = module.getDeathbans().getActiveBan(uuid);
        if (ban.isEmpty()) {
            return;
        }
        // A life spent, if the lives module is running and they have one - it lifts
        // the ban itself. Never for a ban until the map ends: EOTW is final.
        if (!ban.get().isUntilMapEnd() && module.getDeathbanWaiver().waive(uuid, ban.get())) {
            return;
        }
        String message = ban.get().isUntilMapEnd()
                ? module.getLang().get(PvpMessages.DEATHBAN_MAP_END_LOGIN_DENIED)
                : module.getLang().get(PvpMessages.DEATHBAN_LOGIN_DENIED,
                        "time", module.formatDuration(ban.get().remainingSeconds(System.currentTimeMillis())));
        // The section-sign form is what LangManager already produces, so this is a
        // straight parse rather than a re-rendering of the message.
        event.kickMessage(com.lawkeys.hcfcore.util.LegacyText.SERIALIZER.deserialize(message));
    }

    /**
     * @return the player's unique id, or {@code null} if this connection cannot yet
     *         name one
     *
     * <p>The two phases expose their profile through different types - the login
     * phase's is authenticated and nullable, the configuration phase's is not - and
     * the javadoc of {@code PlayerProfile#getId} marks the id itself nullable in
     * both. Read in the Paper 26.2 sources rather than assumed (CONTRIBUTING.md section 6).
     */
    private static UUID uuidOf(PlayerConnection connection) {
        PlayerProfile profile = switch (connection) {
            case PlayerLoginConnection login -> login.getAuthenticatedProfile();
            case PlayerConfigurationConnection configuring -> configuring.getProfile();
            default -> null;
        };
        return profile == null ? null : profile.getId();
    }

    /**
     * Combat logging: a player who disconnects while tagged dies anyway.
     *
     * <p>The classic HCF answer, and the reason the tag exists at all - without it
     * a losing fight is escaped by pulling the plug.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!module.getSettings().combatTag().killOnLogout()) {
            return;
        }
        if (!module.getCombatTags().isTagged(player.getUniqueId())) {
            return;
        }
        // Killing the player fires PlayerDeathEvent, which applies the deathban and
        // clears the tag through the handler above - so the DTR cost lands too.
        player.setHealth(0.0);
    }
}
