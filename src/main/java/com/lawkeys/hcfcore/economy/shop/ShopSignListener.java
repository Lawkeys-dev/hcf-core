package com.lawkeys.hcfcore.economy.shop;

import com.lawkeys.hcfcore.economy.EconomyManager;
import com.lawkeys.hcfcore.economy.EconomyMessages;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.util.LegacyText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Shop signs: written by staff ({@link #CREATE_PERMISSION}), used by everybody with
 * a right click - one lot, or, sneaking, as many as the player can (all they hold
 * of it, for a sell sign).
 */
public final class ShopSignListener implements Listener {

    /** Writing a shop sign: staff only, or anybody could open a shop selling at a loss. */
    public static final String CREATE_PERMISSION = "hcfcore.economy.shop.admin";

    private final Supplier<ShopRules> rules;
    private final Supplier<EconomyManager> economy;
    private final LangManager lang;
    private final Shop shop;

    public ShopSignListener(Supplier<ShopRules> rules, Supplier<EconomyManager> economy, LangManager lang, Shop shop) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.lang = Objects.requireNonNull(lang, "lang");
        this.shop = Objects.requireNonNull(shop, "shop");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWrite(SignChangeEvent event) {
        ShopRules current = rules.get();
        List<String> lines = new ArrayList<>();
        for (Component line : event.lines()) {
            lines.add(line == null ? "" : PlainTextComponentSerializer.plainText().serialize(line));
        }
        if (!ShopSign.isShopHeader(lines.get(0), current.buyHeader(), current.sellHeader())) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission(CREATE_PERMISSION)) {
            event.setCancelled(true);
            lang.send(player, EconomyMessages.SHOP_SIGN_NO_PERMISSION);
            return;
        }
        Optional<ShopSign> sign = ShopSign.read(lines, current.buyHeader(), current.sellHeader());
        Material material = sign.map(read -> Material.matchMaterial(read.material())).orElse(null);
        if (sign.isEmpty() || material == null || !material.isItem()) {
            event.setCancelled(true);
            lang.send(player, EconomyMessages.SHOP_SIGN_INVALID);
            return;
        }
        EconomyManager money = economy.get();
        String header = sign.get().buy() ? current.buyHeader() : current.sellHeader();
        event.line(0, LegacyText.SERIALIZER.deserialize(lang.get(
                sign.get().buy() ? EconomyMessages.SHOP_SIGN_BUY : EconomyMessages.SHOP_SIGN_SELL, "header", header)));
        event.line(1, Component.text(String.valueOf(sign.get().amount())));
        event.line(2, Component.text(ShopRules.readable(material.name())));
        event.line(3, Component.text(money == null ? String.valueOf(sign.get().price())
                : money.format(sign.get().price())));
        lang.send(player, EconomyMessages.SHOP_SIGN_CREATED);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || event.getHand() != EquipmentSlot.HAND
                || !(block.getState(false) instanceof Sign state)) {
            return;
        }
        ShopRules current = rules.get();
        List<String> lines = new ArrayList<>();
        for (Component line : state.getSide(Side.FRONT).lines()) {
            lines.add(PlainTextComponentSerializer.plainText().serialize(line));
        }
        Optional<ShopSign> sign = ShopSign.read(lines, current.buyHeader(), current.sellHeader());
        if (sign.isEmpty()) {
            return;
        }
        // A shop sign is a button: never the sign editor.
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!current.enabled() || !current.mode().signs()) {
            lang.send(player, EconomyMessages.SHOP_DISABLED);
            return;
        }
        Material material = Material.matchMaterial(sign.get().material());
        if (material == null) {
            lang.send(player, EconomyMessages.SHOP_SIGN_INVALID);
            return;
        }
        ShopSign read = sign.get();
        if (read.buy()) {
            int lots = player.isSneaking() ? Math.max(1, material.getMaxStackSize() / read.amount()) : 1;
            shop.buy(player, material, read.amount(), read.price(), lots);
        } else {
            shop.sell(player, material, read.amount(), read.price(), player.isSneaking());
        }
    }
}
