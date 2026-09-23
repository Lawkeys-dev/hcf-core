package com.lawkeys.hcfcore.economy.shop;

import com.lawkeys.hcfcore.economy.EconomyManager;
import com.lawkeys.hcfcore.economy.EconomyMessages;
import com.lawkeys.hcfcore.lang.LangManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** {@code /shop}: opens the menu, when {@code shop.mode} has one. */
public final class ShopCommand implements TabExecutor {

    private final Supplier<ShopRules> rules;
    private final Supplier<EconomyManager> economy;
    private final LangManager lang;

    public ShopCommand(Supplier<ShopRules> rules, Supplier<EconomyManager> economy, LangManager lang) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.lang = Objects.requireNonNull(lang, "lang");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            lang.send(sender, EconomyMessages.SHOP_PLAYERS_ONLY);
            return true;
        }
        ShopRules current = rules.get();
        if (!current.enabled() || !current.mode().menu()) {
            lang.send(player, EconomyMessages.SHOP_MENU_DISABLED);
            return true;
        }
        if (!current.hasItems()) {
            lang.send(player, EconomyMessages.SHOP_EMPTY);
            return true;
        }
        ShopMenu.open(player, current, economy.get(), lang);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
