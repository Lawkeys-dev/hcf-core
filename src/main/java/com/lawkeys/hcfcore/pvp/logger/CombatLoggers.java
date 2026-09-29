package com.lawkeys.hcfcore.pvp.logger;

import com.lawkeys.hcfcore.api.event.CombatLoggerDeathEvent;
import com.lawkeys.hcfcore.pvp.PvpMessages;
import com.lawkeys.hcfcore.pvp.PvpModule;
import com.lawkeys.hcfcore.util.LegacyText;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.logging.Level;

/**
 * The stand-in a combat-tagged player leaves when they disconnect ({@code pvp.yml},
 * {@code combat-tag.logout: npc}, the owner's request of 28/09/2026).
 *
 * <p>Where they stood, at full health, with their name: it never walks or trades - a
 * blow pushes it back, as it would them ({@code logger.knockback}) - and cannot be
 * hurt by anything but a player - and a player only by the rules of a blow at the
 * one it stands for. A hit starts both combat tags over. Killed, the player's items
 * fall there, they are deathbanned, and {@link CombatLoggerDeathEvent} lets the rest of
 * the plugin count the death (DTR, points, statistics); they come back to an empty
 * inventory. Still standing when the tag runs out, it goes and they keep everything;
 * back before that, they take its place, with the health they left with less the
 * damage it took.
 *
 * <p>Nothing of a stand-in survives a restart: the entities are never saved, and a
 * player whose stand-in outlived the server keeps their things. Only "died while away"
 * is kept, in {@code combat-loggers.yml}, until they are back.
 */
public final class CombatLoggers implements Listener {

    /** What a stand-in stands for, the player's own state when they left. */
    private record Logger(UUID playerId, String name, UUID entityId, ItemStack[] items, Location at,
                          List<String> tiers, boolean bypass, double healthLeftWith) {
    }

    private final PvpModule module;
    private final NamespacedKey marker;
    private final File deadFile;
    private final Map<UUID, Logger> byPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> byEntity = new ConcurrentHashMap<>();
    /** Players whose stand-in died: emptied at their next login. */
    private final Set<UUID> diedAway = ConcurrentHashMap.newKeySet();
    private volatile BiFunction<UUID, ItemStack, ItemStack> dropRule = (player, item) -> item;
    private BukkitTask task;

    public CombatLoggers(PvpModule module) {
        this.module = Objects.requireNonNull(module, "module");
        this.marker = new NamespacedKey(module.getPlugin(), "combat_logger");
        this.deadFile = new File(module.getPlugin().getDataFolder(), "combat-loggers.yml");
        loadDiedAway();
    }

    /**
     * What a stand-in's death lets fall: everything, until another module says
     * otherwise (the King's kit). The rule returns the item to drop, or {@code null}.
     */
    public void setDropRule(BiFunction<UUID, ItemStack, ItemStack> rule) {
        this.dropRule = Objects.requireNonNull(rule, "rule");
    }

    public void start() {
        stop();
        // Once a second: a stand-in whose tag has run out goes.
        this.task = Bukkit.getScheduler().runTaskTimer(module.getPlugin(), this::expire, 20L, 20L);
    }

    /** Server stopping: every stand-in goes, and its player keeps their things. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Logger logger : List.copyOf(byPlayer.values())) {
            remove(logger);
        }
    }

    /** A tagged player leaving: their stand-in where they stood. */
    public void spawn(Player player) {
        var rules = module.getSettings().combatTag();
        EntityType type = Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(rules.loggerEntity().toLowerCase(java.util.Locale.ROOT)));
        if (type == null || type.getEntityClass() == null || !type.isAlive()) {
            type = EntityType.VILLAGER;
        }
        Location at = player.getLocation();
        ItemStack[] items = player.getInventory().getContents().clone();
        for (int i = 0; i < items.length; i++) {
            items[i] = items[i] == null ? null : items[i].clone();
        }
        String display = module.getLang().get(PvpMessages.LOGGER_NAME, "player", player.getName());
        Entity entity = at.getWorld().spawn(at, type.getEntityClass(), spawned -> {
            spawned.customName(LegacyText.of(display));
            spawned.setCustomNameVisible(true);
            spawned.setPersistent(false);
            spawned.setSilent(true);
            spawned.getPersistentDataContainer().set(marker, PersistentDataType.STRING, player.getUniqueId().toString());
            if (spawned instanceof Mob mob) {
                if (rules.loggerKnockback()) {
                    // Unaware: no goal, no brain - it never walks - but still physics,
                    // so a blow knocks it back and it falls.
                    mob.setAware(false);
                } else {
                    mob.setAI(false);
                }
            }
            if (spawned instanceof LivingEntity living) {
                living.setRemoveWhenFarAway(false);
                var max = living.getAttribute(Attribute.MAX_HEALTH);
                if (max != null) {
                    max.setBaseValue(rules.loggerHealth());
                }
                living.setHealth(rules.loggerHealth());
            }
        });
        at.getChunk().addPluginChunkTicket(module.getPlugin());
        Logger logger = new Logger(player.getUniqueId(), player.getName(), entity.getUniqueId(), items, at.clone(),
                module.tierPermissionsOf(player), player.hasPermission(PvpModule.BYPASS_PERMISSION),
                player.getHealth());
        byPlayer.put(player.getUniqueId(), logger);
        byEntity.put(entity.getUniqueId(), player.getUniqueId());
    }

    private void expire() {
        for (Logger logger : List.copyOf(byPlayer.values())) {
            Entity entity = Bukkit.getEntity(logger.entityId());
            if (entity == null || !entity.isValid() || !module.getCombatTags().isTagged(logger.playerId())) {
                remove(logger);
            }
        }
    }

    private void remove(Logger logger) {
        byPlayer.remove(logger.playerId(), logger);
        byEntity.remove(logger.entityId());
        Entity entity = Bukkit.getEntity(logger.entityId());
        if (entity != null) {
            entity.remove();
        }
        release(logger);
    }

    /** The chunk is let go once no other stand-in stands in it. */
    private void release(Logger logger) {
        Chunk chunk = logger.at().getChunk();
        boolean shared = byPlayer.values().stream().anyMatch(other -> other.at().getWorld().equals(chunk.getWorld())
                && other.at().getBlockX() >> 4 == chunk.getX() && other.at().getBlockZ() >> 4 == chunk.getZ());
        if (!shared) {
            chunk.removePluginChunkTicket(module.getPlugin());
        }
    }

    /** Lightning makes a witch of a villager: a stand-in stays what it is. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTransform(EntityTransformEvent event) {
        if (loggerOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    private Logger loggerOf(Entity entity) {
        UUID player = byEntity.get(entity.getUniqueId());
        return player == null ? null : byPlayer.get(player);
    }

    /** Nothing but a player hurts it, and a player only as they could hurt its player. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Logger logger = loggerOf(event.getEntity());
        if (logger == null) {
            return;
        }
        Player attacker = event instanceof EntityDamageByEntityEvent byEntity ? attackerOf(byEntity) : null;
        if (attacker == null) {
            if (event.getCause() != EntityDamageEvent.DamageCause.VOID
                    && event.getCause() != EntityDamageEvent.DamageCause.KILL) {
                event.setCancelled(true);
            }
            return;
        }
        var refusal = module.judgeHarm(attacker, logger.playerId(), event.getEntity().getLocation(), logger.name());
        if (refusal.isPresent()) {
            event.setCancelled(true);
            refusal.get().tell(module.getLang(), attacker);
            return;
        }
        // A hit starts both tags over: the fight goes on without them.
        module.getCombatTags().tag(logger.playerId());
        module.getCombatTags().tag(attacker.getUniqueId());
    }

    private static Player attackerOf(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return event.getDamageSource().getCausingEntity() instanceof Player causing ? causing : null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (loggerOf(event.getRightClicked()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (loggerOf(event.getRightClicked()) != null) {
            event.setCancelled(true);
        }
    }

    /** Killed: its player's items fall there and its player dies, though away. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent event) {
        Logger logger = loggerOf(event.getEntity());
        if (logger == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        Location at = event.getEntity().getLocation();
        for (ItemStack item : logger.items()) {
            ItemStack drop = item == null || item.isEmpty() ? null : dropRule.apply(logger.playerId(), item);
            if (drop != null && !drop.isEmpty()) {
                at.getWorld().dropItemNaturally(at, drop);
            }
        }
        byPlayer.remove(logger.playerId(), logger);
        byEntity.remove(logger.entityId());
        release(logger);
        diedAway.add(logger.playerId());
        saveDiedAway();
        module.getCombatTags().clear(logger.playerId());
        Player killer = event.getEntity().getKiller();
        module.applyDeathban(logger.playerId(), logger.tiers(), logger.bypass());
        Bukkit.getPluginManager().callEvent(new CombatLoggerDeathEvent(logger.playerId(), logger.name(), killer, at));
        String announce = killer == null
                ? module.getLang().get(PvpMessages.LOGGER_DIED, "player", logger.name())
                : module.getLang().get(PvpMessages.LOGGER_KILLED, "player", logger.name(), "killer", killer.getName());
        if (!announce.isEmpty()) {
            Bukkit.getOnlinePlayers().forEach(online -> online.sendMessage(announce));
            Bukkit.getConsoleSender().sendMessage(announce);
        }
    }

    /**
     * Back while the stand-in stands: it goes, and they take its place - with the
     * health they left with, less what it lost, never below half a heart.
     * Back after it died: an empty inventory, full health, the spawn, and a word why.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        Logger logger = byPlayer.get(id);
        if (logger != null) {
            Entity entity = Bukkit.getEntity(logger.entityId());
            if (entity instanceof LivingEntity living && living.isValid()) {
                var standInMax = living.getAttribute(Attribute.MAX_HEALTH);
                double lost = (standInMax == null ? living.getHealth() : standInMax.getValue()) - living.getHealth();
                Location at = living.getLocation();
                player.teleport(at);
                var max = player.getAttribute(Attribute.MAX_HEALTH);
                double top = max == null ? 20.0 : max.getValue();
                player.setHealth(Math.max(1.0, Math.min(top, logger.healthLeftWith() - Math.max(0.0, lost))));
            }
            remove(logger);
        }
        if (diedAway.remove(id)) {
            saveDiedAway();
            player.getInventory().clear();
            player.setExp(0f);
            player.setLevel(0);
            var max = player.getAttribute(Attribute.MAX_HEALTH);
            player.setHealth(max == null ? 20.0 : max.getValue());
            player.setFoodLevel(20);
            player.setFireTicks(0);
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
            // Where a death would have sent them: their bed or anchor, else the world's spawn.
            Location respawn = player.getRespawnLocation();
            player.teleport(respawn != null ? respawn : Bukkit.getWorlds().getFirst().getSpawnLocation());
            module.getLang().send(player, PvpMessages.LOGGER_DIED_AWAY);
        }
    }

    /** @return whether this entity is a combat logger's stand-in */
    public boolean isStandIn(Entity entity) {
        return entity.getPersistentDataContainer().has(marker, PersistentDataType.STRING);
    }

    private void loadDiedAway() {
        if (!deadFile.exists()) {
            return;
        }
        YamlConfiguration file = YamlConfiguration.loadConfiguration(deadFile);
        for (String raw : file.getStringList("died-away")) {
            try {
                diedAway.add(UUID.fromString(raw));
            } catch (IllegalArgumentException e) {
                module.getPlugin().getLogger().warning("combat-loggers.yml: '" + raw + "' is not a player id; ignored.");
            }
        }
    }

    private void saveDiedAway() {
        YamlConfiguration file = new YamlConfiguration();
        Set<String> ids = new HashSet<>();
        diedAway.forEach(id -> ids.add(id.toString()));
        file.set("died-away", List.copyOf(ids));
        try {
            file.save(deadFile);
        } catch (IOException e) {
            module.getPlugin().getLogger().log(Level.WARNING, "Could not save combat-loggers.yml.", e);
        }
    }
}
