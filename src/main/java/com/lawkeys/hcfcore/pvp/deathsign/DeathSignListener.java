package com.lawkeys.hcfcore.pvp.deathsign;

import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.pvp.PvpMessages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A kill by a player leaves a sign: the four lines of {@code pvp.death-sign},
 * written on the sign itself so they stay when it is placed, and waxed so nobody
 * can rewrite them.
 */
public final class DeathSignListener implements Listener {

    private final LangManager lang;
    private final Supplier<DeathSignRules> rules;

    public DeathSignListener(LangManager lang, Supplier<DeathSignRules> rules) {
        this.lang = Objects.requireNonNull(lang, "lang");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        DeathSignRules current = rules.get();
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (!current.enabled() || killer == null || killer.equals(victim)) {
            return;
        }
        ItemStack sign = sign(current, victim.getName(), killer.getName());
        if (sign == null) {
            return;
        }
        if (!current.toKiller()) {
            event.getDrops().add(sign);
            return;
        }
        for (ItemStack left : killer.getInventory().addItem(sign).values()) {
            killer.getWorld().dropItemNaturally(killer.getLocation(), left);
        }
    }

    /** A combat logger's stand-in killed: its sign falls with its items, or goes to the killer. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoggerDeath(com.lawkeys.hcfcore.api.event.CombatLoggerDeathEvent event) {
        DeathSignRules current = rules.get();
        Player killer = event.getKiller().orElse(null);
        if (!current.enabled() || killer == null) {
            return;
        }
        ItemStack sign = sign(current, event.getVictimName(), killer.getName());
        if (sign == null) {
            return;
        }
        if (!current.toKiller()) {
            event.getLocation().getWorld().dropItemNaturally(event.getLocation(), sign);
            return;
        }
        for (ItemStack left : killer.getInventory().addItem(sign).values()) {
            killer.getWorld().dropItemNaturally(killer.getLocation(), left);
        }
    }

    private ItemStack sign(DeathSignRules current, String victim, String killer) {
        Material material = Material.matchMaterial(current.material());
        if (material == null) {
            return null;
        }
        Map<String, String> placeholders = Map.of("victim", victim, "killer", killer,
                "date", DateTimeFormatter.ofPattern(current.dateFormat()).format(ZonedDateTime.now()));
        List<String> lines = List.of(
                lang.get(PvpMessages.DEATH_SIGN_LINE_1, placeholders),
                lang.get(PvpMessages.DEATH_SIGN_LINE_2, placeholders),
                lang.get(PvpMessages.DEATH_SIGN_LINE_3, placeholders),
                lang.get(PvpMessages.DEATH_SIGN_LINE_4, placeholders));
        // A record: written on the sign by the plugin when placed, for everybody, and
        // never editable (util/RecordSigns).
        return com.lawkeys.hcfcore.util.RecordSigns.item(material, lines,
                lang.get(PvpMessages.DEATH_SIGN_NAME, placeholders), lines);
    }
}
