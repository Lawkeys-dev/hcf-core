package com.lawkeys.hcfcore.kit.listener;

import com.lawkeys.hcfcore.kit.Kit;
import com.lawkeys.hcfcore.kit.KitMessages;
import com.lawkeys.hcfcore.kit.KitModule;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.util.Cooldowns;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

/**
 * Refill signs: click a sign, get a kit (FEATURES.md section 7, kitmap).
 *
 * <p>A sign whose first line is {@code [Kit]} and whose second names a kit hands
 * that kit over. The classic kitmap fixture, and the reason it is a sign rather
 * than a command is that a player re-kitting between deaths wants one click, not a
 * command and a tab-complete.
 *
 * <p>The anti-spam wait is separate from the kit's own cooldown on purpose: a
 * kitmap kit usually has no cooldown at all, and the sign still must not be
 * clickable sixty times a second.
 *
 * <p><strong>Clicks are read before territory protection.</strong> Refill signs sit
 * at spawn, and spawn is server land, where protection refuses every interaction:
 * read after it, a sign there did nothing for any player, and only staff who build
 * through protection ever saw one work (found 13/09/2026). Only staff holding
 * {@link #CREATE_PERMISSION} can make a refill sign, so using one is never an
 * intrusion into somebody's land. A freeze still comes first.
 */
public final class KitSignListener implements Listener {

    public static final String CREATE_PERMISSION = "hcfcore.kit.sign";

    private final KitModule module;
    private static final String COOLDOWN_KEY = "refill-sign";

    private final Cooldowns signCooldowns = new Cooldowns();

    public KitSignListener(KitModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    /** Turns a written sign into a refill sign, if the player may make one. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!module.getSettings().enabled() || !module.getSettings().signsEnabled()) {
            return;
        }
        String first = plain(event.line(0));
        if (!first.equalsIgnoreCase("[" + module.getSettings().signLine() + "]")) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission(CREATE_PERMISSION)) {
            module.getLang().send(player, "general.no-permission");
            event.setCancelled(true);
            return;
        }
        String kitId = plain(event.line(1));
        if (module.getManager() == null || module.getManager().get(kitId).isEmpty()) {
            module.getLang().send(player, KitMessages.SIGN_UNKNOWN_KIT, "kit", kitId);
            event.setCancelled(true);
            return;
        }
        // Colour the header so a finished sign is visibly different from a typo.
        event.line(0, net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize(LangManager.colorize("&1[" + module.getSettings().signLine() + "]")));
        module.getLang().send(player, KitMessages.SIGN_CREATED, "kit", kitId);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (!module.getSettings().enabled() || !module.getSettings().signsEnabled()) {
            return;
        }
        // The type first, then the sign without a copy: getState() copies a block
        // entity - a chest's contents, a furnace's - and this ran on every right click
        // on the server (noted in the listener review, 14/09/2026). Reading a sign's
        // lines needs no copy (getState(false), "to operate on real block data").
        Block clicked = event.getClickedBlock();
        if (!Tag.ALL_SIGNS.isTagged(clicked.getType()) || !(clicked.getState(false) instanceof Sign sign)) {
            return;
        }
        String header = plain(sign.getSide(org.bukkit.block.sign.Side.FRONT).line(0));
        if (!header.equalsIgnoreCase("[" + module.getSettings().signLine() + "]")) {
            return;
        }
        Player player = event.getPlayer();
        String kitId = plain(sign.getSide(org.bukkit.block.sign.Side.FRONT).line(1));
        Kit kit = module.getManager() == null ? null : module.getManager().get(kitId).orElse(null);
        // A refill sign is not for editing, even when its kit is gone.
        event.setCancelled(true);
        if (kit == null) {
            module.getLang().send(player, KitMessages.SIGN_UNKNOWN_KIT, "kit", kitId);
            return;
        }

        long now = System.currentTimeMillis();
        if (signCooldowns.isWaiting(player.getUniqueId(), COOLDOWN_KEY, now)) {
            module.getLang().send(player, KitMessages.SIGN_TOO_FAST);
            return;
        }
        if (!kit.isAllowed(player::hasPermission)) {
            module.getLang().send(player, KitMessages.NO_PERMISSION, "kit", kit.displayName());
            return;
        }
        long kitWait = module.getManager().remainingCooldown(player.getUniqueId(), kit.id());
        if (kitWait > 0) {
            module.getLang().send(player, KitMessages.ON_COOLDOWN,
                    "kit", kit.displayName(),
                    "time", com.lawkeys.hcfcore.util.Durations.format(kitWait));
            return;
        }
        signCooldowns.start(player.getUniqueId(), COOLDOWN_KEY, module.getSettings().signCooldownSeconds(), now);
        module.give(player, kit);
        module.getManager().markUsed(player.getUniqueId(), kit);
        module.flushSoon();
        module.getLang().send(player, KitMessages.RECEIVED, "kit", kit.displayName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        signCooldowns.forget(event.getPlayer().getUniqueId());
    }

    private static String plain(net.kyori.adventure.text.Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component).trim();
    }
}
