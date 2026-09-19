package com.lawkeys.hcfcore.command;

import com.lawkeys.hcfcore.ability.Ability;
import com.lawkeys.hcfcore.ability.AbilityModule;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.pvp.PvpModule;
import com.lawkeys.hcfcore.pvp.PvpSettings;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code /cooldown reset <player> [what]}: ends a player's cooldowns, whichever module
 * keeps them - partner items ({@code abilities.yml}), the ender pearl and the item
 * cooldowns ({@code pvp.yml}). From the console too. What is reset:
 * {@code all} (the default), {@code abilities}, {@code global} (the shared ability
 * one), {@code pearl}, {@code items}, or one ability's or one item's id.
 */
public final class CooldownCommand implements TabExecutor {

    public static final String PERMISSION = "hcfcore.cooldown.admin";
    private static final List<String> GROUPS = List.of("all", "abilities", "global", "pearl", "items");

    private final LangManager lang;
    /** Either may be {@code null}: its module is not running. */
    private final AbilityModule abilities;
    private final PvpModule pvp;

    public CooldownCommand(LangManager lang, AbilityModule abilities, PvpModule pvp) {
        this.lang = Objects.requireNonNull(lang, "lang");
        this.abilities = abilities;
        this.pvp = pvp;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            lang.send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("reset")) {
            lang.send(sender, "general-commands.usage", "usage",
                    "/" + label + " reset <player> [all|abilities|global|pearl|items|<ability>|<item>]");
            return true;
        }
        Optional<Player> target = VisiblePlayers.find(sender, args[1]);
        if (target.isEmpty()) {
            lang.send(sender, "general-commands.player-not-found", "player", args[1]);
            return true;
        }
        Player player = target.get();
        String what = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "all";
        String which = reset(player, what);
        if (which == null) {
            lang.send(sender, CooldownMessages.UNKNOWN, "input", args[2], "options", String.join(", ", options()));
            return true;
        }
        lang.send(sender, CooldownMessages.RESET, "which", which, "player", player.getName());
        if (!player.equals(sender)) {
            lang.send(player, CooldownMessages.RESET_TARGET, "which", which);
        }
        return true;
    }

    /** @return the name of what was reset, or {@code null} if {@code what} names nothing */
    private String reset(Player player, String what) {
        switch (what) {
            case "all" -> {
                resetAbilities(player);
                resetPearl(player);
                resetItems(player);
                return lang.get(CooldownMessages.WHICH_ALL);
            }
            case "abilities" -> {
                resetAbilities(player);
                return lang.get(CooldownMessages.WHICH_ABILITIES);
            }
            case "global" -> {
                if (abilities != null) {
                    abilities.resetGlobalCooldown(player.getUniqueId());
                }
                return lang.get(CooldownMessages.WHICH_GLOBAL);
            }
            case "pearl" -> {
                resetPearl(player);
                return lang.get(CooldownMessages.WHICH_PEARL);
            }
            case "items" -> {
                resetItems(player);
                return lang.get(CooldownMessages.WHICH_ITEMS);
            }
            default -> {
                Optional<Ability> ability = abilities == null ? Optional.empty() : abilities.getSettings().ability(what);
                if (ability.isPresent()) {
                    abilities.resetCooldown(player.getUniqueId(), ability.get());
                    return abilities.display(ability.get());
                }
                Optional<PvpSettings.ItemCooldown> item = itemCooldowns().stream()
                        .filter(cooldown -> cooldown.id().equals(what)).findFirst();
                if (item.isPresent()) {
                    pvp.clearItemCooldown(player, item.get());
                    return LangManager.colorize(item.get().name());
                }
                return null;
            }
        }
    }

    private void resetAbilities(Player player) {
        if (abilities != null) {
            abilities.resetCooldowns(player.getUniqueId());
        }
    }

    private void resetPearl(Player player) {
        if (pvp != null) {
            pvp.resetPearl(player.getUniqueId());
            player.setCooldown(org.bukkit.Material.ENDER_PEARL, 0);
        }
    }

    private void resetItems(Player player) {
        for (PvpSettings.ItemCooldown item : itemCooldowns()) {
            pvp.clearItemCooldown(player, item);
        }
    }

    private List<PvpSettings.ItemCooldown> itemCooldowns() {
        return pvp == null ? List.of() : pvp.getSettings().itemCooldowns().items();
    }

    /** Everything {@code [what]} can be: the groups, every ability, every item. */
    private List<String> options() {
        List<String> out = new ArrayList<>(GROUPS);
        if (abilities != null) {
            abilities.getSettings().abilities().forEach(ability -> out.add(ability.id()));
        }
        itemCooldowns().forEach(item -> out.add(item.id()));
        return out;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return "reset".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("reset") : List.of();
        }
        if (!args[0].equalsIgnoreCase("reset")) {
            return List.of();
        }
        if (args.length == 2) {
            return VisiblePlayers.names(sender, args[1]);
        }
        if (args.length == 3) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return options().stream().filter(option -> option.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
