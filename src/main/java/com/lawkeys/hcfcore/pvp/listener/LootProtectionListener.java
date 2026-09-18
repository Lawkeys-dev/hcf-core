package com.lawkeys.hcfcore.pvp.listener;

import com.lawkeys.hcfcore.pvp.LootClaim;
import com.lawkeys.hcfcore.pvp.PvpModule;
import com.lawkeys.hcfcore.pvp.PvpSettings;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Anticlean: a dead player's loot is the killer's - and the killer's team's - for a
 * few seconds (the rule is {@link LootClaim}, pure and tested).
 *
 * <p>The drops are taken over rather than found: {@code getDrops()} is emptied, which
 * its javadoc gives as the way to stop items dropping, and each is dropped again by
 * {@code World#dropItemNaturally}, whose function runs "before the entity is
 * spawned" (26.2) - so the claim is on the item before anybody could touch it.
 * {@code MONITOR}, after every listener that edits the drops (Kill the King's among
 * them), and never for a cancelled death: a death undone after its drops were
 * spawned would duplicate them.
 *
 * <p>The claim lives in the item entity's own data, so there is nothing to clean up;
 * it is simply read as expired once its time is up. Every way an item leaves the
 * ground is covered: a player ({@code PlayerAttemptPickupItemEvent}, before the item
 * flies), any entity ({@code EntityPickupItemEvent} - mobs, allays), a hopper
 * ({@code InventoryPickupItemEvent}), and a merge with a stack under another claim or
 * none. Each of those four events declares its own handler list (26.2 sources), so
 * each has its own handler.
 */
public final class LootProtectionListener implements Listener {

    private final PvpModule module;
    private final NamespacedKey killerKey;
    private final NamespacedKey teamKey;
    private final NamespacedKey untilKey;

    public LootProtectionListener(PvpModule module) {
        this.module = Objects.requireNonNull(module, "module");
        this.killerKey = new NamespacedKey(module.getPlugin(), "loot_killer");
        this.teamKey = new NamespacedKey(module.getPlugin(), "loot_team");
        this.untilKey = new NamespacedKey(module.getPlugin(), "loot_until");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        PvpSettings settings = module.getSettings();
        PvpSettings.LootProtectionRules rules = settings.lootProtection();
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (!settings.enabled() || !rules.enabled() || rules.seconds() <= 0
                || killer == null || killer.equals(victim) || event.getDrops().isEmpty()) {
            return;
        }
        UUID team = rules.teamShares() ? module.teamOf(killer.getUniqueId()) : null;
        LootClaim claim = new LootClaim(killer.getUniqueId(), team,
                System.currentTimeMillis() + rules.seconds() * 1000L);
        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        event.getDrops().clear();
        Location at = victim.getLocation();
        for (ItemStack drop : drops) {
            if (drop != null && !drop.isEmpty()) {
                at.getWorld().dropItemNaturally(at, drop, item -> write(item, claim));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPickup(PlayerAttemptPickupItemEvent event) {
        LootClaim claim = read(event.getItem());
        if (claim != null && !mayPickUp(claim, event.getPlayer())) {
            event.setFlyAtPlayer(false);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPickup(EntityPickupItemEvent event) {
        LootClaim claim = read(event.getItem());
        if (claim != null && !(event.getEntity() instanceof Player player ? mayPickUp(claim, player)
                : claim.mayPickUp(null, null, System.currentTimeMillis()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopper(InventoryPickupItemEvent event) {
        LootClaim claim = read(event.getItem());
        if (claim != null && !claim.mayPickUp(null, null, System.currentTimeMillis())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMerge(ItemMergeEvent event) {
        if (!LootClaim.mayMerge(read(event.getEntity()), read(event.getTarget()), System.currentTimeMillis())) {
            event.setCancelled(true);
        }
    }

    private boolean mayPickUp(LootClaim claim, Player player) {
        UUID id = player.getUniqueId();
        return claim.mayPickUp(id, module.teamOf(id), System.currentTimeMillis());
    }

    private void write(Item item, LootClaim claim) {
        PersistentDataContainer data = item.getPersistentDataContainer();
        data.set(killerKey, PersistentDataType.STRING, claim.killer().toString());
        if (claim.team() != null) {
            data.set(teamKey, PersistentDataType.STRING, claim.team().toString());
        }
        data.set(untilKey, PersistentDataType.LONG, claim.until());
    }

    /** @return the claim on a dropped item, or {@code null} for none (or one this build cannot read) */
    private LootClaim read(Item item) {
        PersistentDataContainer data = item.getPersistentDataContainer();
        String killer = data.get(killerKey, PersistentDataType.STRING);
        Long until = data.get(untilKey, PersistentDataType.LONG);
        if (killer == null || until == null) {
            return null;
        }
        try {
            String team = data.get(teamKey, PersistentDataType.STRING);
            return new LootClaim(UUID.fromString(killer), team == null ? null : UUID.fromString(team), until);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
