package com.lawkeys.hcfcore.pvp.deathsign;

import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.pvp.PvpMessages;
import com.lawkeys.hcfcore.util.ItemText;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

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
        ItemStack item = ItemStack.of(material);
        item.editMeta(meta -> {
            if (meta instanceof BlockStateMeta stateMeta && stateMeta.getBlockState() instanceof Sign state) {
                for (int line = 0; line < 4; line++) {
                    state.getSide(Side.FRONT).line(line, ItemText.line(lines.get(line)));
                }
                // Placed, it cannot be edited: a death sign is a record, not a note.
                state.setWaxed(true);
                stateMeta.setBlockState(state);
            }
            meta.customName(ItemText.line(lang.get(PvpMessages.DEATH_SIGN_NAME, placeholders)));
            meta.lore(lines.stream().map(ItemText::line).toList());
        });
        return item;
    }
}
