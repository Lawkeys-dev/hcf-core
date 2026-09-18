package com.lawkeys.hcfcore.ability;

import com.lawkeys.hcfcore.ability.command.AbilityCommand;
import com.lawkeys.hcfcore.ability.listener.AbilityListener;
import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.events.EventModule;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.pvp.PvpModule;
import com.lawkeys.hcfcore.pvpclass.ArcherTag;
import com.lawkeys.hcfcore.pvpclass.ClassModule;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.util.Cooldowns;
import com.lawkeys.hcfcore.util.Durations;
import com.lawkeys.hcfcore.util.ItemText;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.UseCooldown;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Abilities, or partner items ({@code abilities.yml}; FEATURES.md section 7): items
 * that do something when they are used - right-clicked, thrown, hit with, shot from.
 *
 * <p>The behaviours are built in, one per {@link AbilityType}; which abilities a
 * server runs, and every value they read, are the file's. A {@code commands} ability
 * runs console commands, for anything the types do not cover.
 *
 * <p>Every use goes through the same gate, {@link #mayUse}: the module on, not where
 * abilities are refused ({@code disabled-in}), no shared cooldown running, the
 * ability's own not running. What a player may not harm - a teammate, somebody on a
 * safe zone, during SOTW - is the combat module's to say ({@link PvpModule#judgeHarm}):
 * an ability that reaches an enemy asks it.
 */
public final class AbilityModule {

    /** The item key partner items have always carried: items handed out before still work. */
    public static final String ITEM_KEY = "ability";
    private static final String POCKET_KEY = "pocket_bard";
    private static final String PROJECTILE_KEY = "ability_projectile";
    private static final String FIREWORK_KEY = "ability_firework";
    private static final String PUMPKIN_KEY = "ability_pumpkin";
    private static final String GLOBAL_COOLDOWN = "*";
    public static final String ADMIN_PERMISSION = "hcfcore.ability.admin";

    /** A player who hit another, and when. */
    private record Hit(UUID attacker, long at) {
    }

    /** Where a player threw their last ender pearl from, and when. */
    private record Pearl(Location from, long at) {
    }

    /** A focus: this player's hits on {@code target} are multiplied until {@code until}. */
    private record Focus(UUID target, long until, double multiplier) {
    }

    /** Crafting chaos on a victim: {@code attacker}'s hits may open a crafting table on them. */
    private record Chaos(UUID attacker, long until, double chance) {
    }

    /** Thorns on a player: {@code percent} of what they deal {@code owner} goes back to them. */
    private record Thorns(UUID owner, long until, double percent) {
    }

    /** A Combo Fish running: the players its user hits can be hit again after {@code delayTicks}. */
    private record ComboFish(long until, int delayTicks) {
    }

    /** A helmet taken for a pumpkin, and the task that gives it back. */
    private record Pumpkin(ItemStack helmet, BukkitTask task) {
    }

    /** A combo running: hits counted until {@code until}. */
    private static final class Combo {
        private final Ability ability;
        private final long until;
        private int hits;

        private Combo(Ability ability, long until) {
            this.ability = ability;
            this.until = until;
        }
    }

    private final Plugin plugin;
    private final LangManager lang;
    private final TeamModule teams;
    private final ClaimModule claims;
    private final PvpModule pvp;
    private final ClassModule classes;
    private final EventModule events;

    private final NamespacedKey itemKey;
    private final NamespacedKey pocketKey;
    private final NamespacedKey projectileKey;
    private final NamespacedKey fireworkKey;
    private final NamespacedKey pumpkinKey;
    private final Cooldowns cooldowns = new Cooldowns();
    private final HitCounter hits = new HitCounter();

    private volatile AbilitySettings settings = AbilitySettings.defaults();

    // Main thread only: what running abilities remember.
    private final Map<UUID, Map<String, Long>> windows = new HashMap<>();
    private final Map<UUID, Focus> focuses = new HashMap<>();
    private final Map<UUID, Combo> combos = new HashMap<>();
    private final Map<UUID, Chaos> chaos = new HashMap<>();
    private final Map<UUID, Long> antiBuild = new HashMap<>();
    private final Map<UUID, Map.Entry<Long, List<String>>> berserk = new HashMap<>();
    private final Map<UUID, Ability> invisible = new HashMap<>();
    private final Map<UUID, BukkitTask> teleports = new HashMap<>();
    private final Map<UUID, Hit> lastHit = new ConcurrentHashMap<>();
    /** Whom each player hit last: {@code attacker} is then the one hit. */
    private final Map<UUID, Hit> lastVictim = new ConcurrentHashMap<>();
    private final Map<UUID, Pearl> lastPearl = new HashMap<>();
    private final Map<UUID, Long> noFall = new HashMap<>();
    private final Map<UUID, ComboFish> comboFish = new HashMap<>();
    /** By the player under thorns: the one who put them there is {@code owner}. */
    private final Map<UUID, Thorns> thorns = new HashMap<>();
    private final Map<UUID, Pumpkin> pumpkins = new HashMap<>();
    private BukkitTask ticker;

    public AbilityModule(Plugin plugin, LangManager lang, TeamModule teams, ClaimModule claims, PvpModule pvp,
                         ClassModule classes, EventModule events) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lang = Objects.requireNonNull(lang, "lang");
        this.teams = teams;
        this.claims = claims;
        this.pvp = pvp;
        this.classes = classes;
        this.events = events;
        this.itemKey = new NamespacedKey(plugin, ITEM_KEY);
        this.pocketKey = new NamespacedKey(plugin, POCKET_KEY);
        this.projectileKey = new NamespacedKey(plugin, PROJECTILE_KEY);
        this.fireworkKey = new NamespacedKey(plugin, FIREWORK_KEY);
        this.pumpkinKey = new NamespacedKey(plugin, PUMPKIN_KEY);
    }

    public LangManager getLang() {
        return lang;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public AbilitySettings getSettings() {
        return settings;
    }

    public Cooldowns getCooldowns() {
        return cooldowns;
    }

    public void enable() {
        reloadSettings();
        plugin.getServer().getPluginManager().registerEvents(new AbilityListener(this), plugin);
        PluginCommand command = plugin.getServer().getPluginCommand("ability");
        if (command == null) {
            plugin.getLogger().severe("The 'ability' command is missing from plugin.yml.");
        } else {
            AbilityCommand executor = new AbilityCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        // Once a second: combos that end, and armour kept hidden from players who come near.
        this.ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void disable() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        teleports.values().forEach(BukkitTask::cancel);
        teleports.clear();
        for (UUID id : new ArrayList<>(invisible.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                showArmor(player);
            }
        }
        invisible.clear();
        for (UUID id : new ArrayList<>(pumpkins.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                giveHelmetBack(player, false);
            }
        }
        pumpkins.clear();
        cooldowns.clearAll();
    }

    public void reloadSettings() {
        ConfigurationSection section = ConfigManager.loadFile(plugin, "abilities.yml");
        AbilityConfig config = new AbilityConfig(
                item -> Material.matchMaterial(item) != null && Material.matchMaterial(item).isItem(),
                effect -> Registry.MOB_EFFECT.get(NamespacedKey.minecraft(effect)) != null,
                warning -> plugin.getLogger().warning("abilities.yml: " + warning));
        this.settings = config.parse(section == null ? null : toMap(section));
        plugin.getLogger().info("Loaded " + settings.abilities().size() + " abilit"
                + (settings.abilities().size() == 1 ? "y." : "ies."));
    }

    /** A configuration section as the plain maps and lists {@link AbilityConfig} reads. */
    private static Map<String, Object> toMap(ConfigurationSection section) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection child) {
                out.put(key, toMap(child));
            } else if (value instanceof List<?> list) {
                List<Object> copy = new ArrayList<>();
                for (Object element : list) {
                    copy.add(element instanceof ConfigurationSection child ? toMap(child) : element);
                }
                out.put(key, copy);
            } else {
                out.put(key, value);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Items
    // ------------------------------------------------------------------

    /** @return the ability this item carries, if it is one this server still runs */
    public Optional<Ability> abilityOf(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        String id = item.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        return id == null ? Optional.empty() : settings.ability(id);
    }

    public Optional<PocketBardItem> pocketItemOf(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        String id = item.getPersistentDataContainer().get(pocketKey, PersistentDataType.STRING);
        return id == null ? Optional.empty() : settings.pocketBardItem(id);
    }

    /** Answers the events module: what a Citadel refuses as a partner item. */
    public boolean isPartnerItem(ItemStack item) {
        return abilityOf(item).isPresent() || pocketItemOf(item).isPresent();
    }

    public ItemStack buildItem(Ability ability, int amount) {
        Material material = Objects.requireNonNull(Material.matchMaterial(ability.material()));
        ItemStack item = ItemStack.of(material, Math.max(1, Math.min(material.getMaxStackSize(), amount)));
        item.editMeta(meta -> {
            meta.customName(ItemText.line(LangManager.colorize(ability.name())));
            if (!ability.lore().isEmpty()) {
                meta.lore(ability.lore().stream().map(line -> ItemText.line(LangManager.colorize(line))).toList());
            }
        });
        ability.enchantments().forEach((key, level) -> {
            Enchantment enchantment = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                    .get(NamespacedKey.minecraft(key));
            if (enchantment == null) {
                plugin.getLogger().warning("abilities.yml: " + ability.id() + ": no enchantment '" + key + "'.");
            } else {
                item.addUnsafeEnchantment(enchantment, level);
            }
        });
        if (ability.glow()) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        if (ability.uses() > 0) {
            // The durability bar counts the uses left: see wear().
            item.setData(DataComponentTypes.MAX_DAMAGE, (int) Math.min(Integer.MAX_VALUE, ability.uses()));
            item.setData(DataComponentTypes.DAMAGE, 0);
        }
        if (ability.type() == AbilityType.FAKE_PEARL) {
            // Its own cooldown group, as long as its own cooldown: the real pearls are not held back.
            item.setData(DataComponentTypes.USE_COOLDOWN, UseCooldown.useCooldown((float) Math.max(1L, ability.cooldownSeconds()))
                    .cooldownGroup(new NamespacedKey(plugin, "fake_pearl")).build());
        }
        if (ability.type() == AbilityType.GRAPPLING_HOOK) {
            item.setData(DataComponentTypes.UNBREAKABLE);
        }
        item.editPersistentDataContainer(data -> data.set(itemKey, PersistentDataType.STRING, ability.id()));
        return item;
    }

    public ItemStack buildPocketItem(PocketBardItem pocket) {
        Material material = Objects.requireNonNull(Material.matchMaterial(pocket.material()));
        ItemStack item = ItemStack.of(material, pocket.amount());
        item.editMeta(meta -> {
            meta.customName(ItemText.line(LangManager.colorize(pocket.name())));
            if (!pocket.lore().isEmpty()) {
                meta.lore(pocket.lore().stream().map(line -> ItemText.line(LangManager.colorize(line))).toList());
            }
        });
        item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        item.editPersistentDataContainer(data -> data.set(pocketKey, PersistentDataType.STRING, pocket.id()));
        return item;
    }

    /**
     * The item after a use: with {@code uses}, one use off its durability - broken
     * after the last; otherwise one taken from the stack, if the ability is consumed.
     */
    private static void consume(Player player, Ability ability, ItemStack item) {
        if (item == null || item.isEmpty()) {
            return;
        }
        if (ability.uses() > 0) {
            Integer worn = item.getData(DataComponentTypes.DAMAGE);
            int now = (worn == null ? 0 : worn) + 1;
            if (now >= ability.uses()) {
                item.setAmount(0);
                player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            } else {
                item.setData(DataComponentTypes.DAMAGE, now);
            }
        } else if (ability.consume()) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    /** Whether an item wears only by its ability's uses: the game's own wear leaves it alone. */
    public boolean wearsByUses(ItemStack item) {
        return abilityOf(item).map(ability -> ability.uses() > 0).orElse(false);
    }

    // ------------------------------------------------------------------
    // The gate every use goes through
    // ------------------------------------------------------------------

    /**
     * Whether this player may use this ability here and now; tells them why not.
     * Starts nothing: {@link #started} does, once the ability actually happens.
     */
    public boolean mayUse(Player player, Ability ability, long now) {
        AbilitySettings current = settings;
        if (!current.enabled()) {
            lang.send(player, AbilityMessages.MODULE_OFF);
            return false;
        }
        if (disabledAt(player.getLocation(), current.disabledIn())) {
            lang.send(player, AbilityMessages.DISABLED_HERE);
            return false;
        }
        long global = sharesCooldown(ability) ? cooldowns.remaining(player.getUniqueId(), GLOBAL_COOLDOWN, now) : 0;
        if (global > 0) {
            lang.send(player, AbilityMessages.GLOBAL_COOLDOWN, "time", Durations.formatWithSeconds(global));
            return false;
        }
        long own = cooldowns.remaining(player.getUniqueId(), ability.id(), now);
        if (own > 0) {
            lang.send(player, AbilityMessages.COOLDOWN, "ability", display(ability),
                    "time", Durations.formatWithSeconds(own));
            return false;
        }
        return true;
    }

    /** The ability happened: its cooldown and the shared one start. */
    public void started(Player player, Ability ability, long now) {
        cooldowns.start(player.getUniqueId(), ability.id(), ability.cooldownSeconds(), now);
        if (sharesCooldown(ability)) {
            cooldowns.start(player.getUniqueId(), GLOBAL_COOLDOWN, settings.globalCooldownSeconds(), now);
        }
    }

    /** Every cooldown of a player's ends at once: each ability's, each Pocket Bard set's, the shared one. */
    public void resetCooldowns(UUID playerId) {
        cooldowns.forget(playerId);
    }

    /** The shared cooldown of a player's ends at once; each ability's own runs on. */
    public void resetGlobalCooldown(UUID playerId) {
        cooldowns.clear(playerId, GLOBAL_COOLDOWN);
    }

    /** One ability's cooldown of a player's ends at once. */
    public void resetCooldown(UUID playerId, Ability ability) {
        cooldowns.clear(playerId, ability.id());
    }

    /**
     * Whether an ability waits for, and starts, the shared cooldown. Not the Pocket
     * Bard: it only hands out a set, whose items have a cooldown each.
     */
    private static boolean sharesCooldown(Ability ability) {
        return ability.type() != AbilityType.POCKET_BARD;
    }

    private boolean disabledAt(Location location, AbilitySettings.DisabledIn rules) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        if ((rules.nether() && world.getEnvironment() == World.Environment.NETHER)
                || (rules.end() && world.getEnvironment() == World.Environment.THE_END)) {
            return true;
        }
        if (rules.warzone() && claims != null && claims.getManager() != null
                && claims.getManager().isWarzone(ClaimModule.toChunk(location))) {
            return true;
        }
        if (rules.safezone() && claims != null && claims.getManager() != null
                && claims.getManager().getOwner(ClaimModule.toChunk(location)).map(Team::isSafeZone).orElse(false)) {
            return true;
        }
        if (events != null && rules.citadel() && events.citadelAt(location).isPresent()) {
            return true;
        }
        return events != null && rules.events() && events.inEventZone(location);
    }

    public String display(Ability ability) {
        return LangManager.colorize(ability.name());
    }

    // ------------------------------------------------------------------
    // Right-click abilities
    // ------------------------------------------------------------------

    /**
     * A right-click with an ability's item. The throw, hit and shoot abilities are
     * used another way, and their click does nothing here.
     */
    public void rightClick(Player player, Ability ability, ItemStack item) {
        long now = System.currentTimeMillis();
        if (!mayUse(player, ability, now)) {
            return;
        }
        AbilityParams p = ability.params();
        switch (ability.type()) {
            case COMMANDS -> {
                for (String command : ability.commandsFor(player.getName())) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }
                lang.send(player, AbilityMessages.USED, "ability", display(ability));
            }
            case THUNDERBOLT, LUCKY_MODE, SWITCH_STICK -> {
                long seconds = p.whole("seconds");
                windows.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>())
                        .put(ability.id(), now + seconds * 1000L);
                lang.send(player, AbilityMessages.ACTIVE, "ability", display(ability),
                        "seconds", String.valueOf(seconds));
            }
            case COMBO -> {
                long seconds = p.whole("seconds");
                combos.put(player.getUniqueId(), new Combo(ability, now + seconds * 1000L));
                lang.send(player, AbilityMessages.ACTIVE, "ability", display(ability),
                        "seconds", String.valueOf(seconds));
            }
            case FOCUS_MODE -> {
                Optional<Player> target = recentAttacker(player, lastHit, p.whole("hit-within-seconds"), now);
                if (target.isEmpty()) {
                    lang.send(player, AbilityMessages.NO_ATTACKER, "seconds", String.valueOf(p.whole("hit-within-seconds")));
                    return;
                }
                long seconds = p.whole("seconds");
                focuses.put(player.getUniqueId(), new Focus(target.get().getUniqueId(), now + seconds * 1000L,
                        p.decimal("damage-multiplier")));
                lang.send(player, AbilityMessages.FOCUS, "player", target.get().getName(), "seconds", String.valueOf(seconds));
                lang.send(target.get(), AbilityMessages.FOCUSED, "player", player.getName(), "seconds", String.valueOf(seconds));
            }
            case NINJA, ANTI_TRAP_STAR -> {
                // The Ninja goes to whom you hit; the others to who hit you.
                boolean ninja = ability.type() == AbilityType.NINJA;
                long within = p.whole("hit-within-seconds");
                Optional<Player> target = recentAttacker(player, ninja ? lastVictim : lastHit, within, now);
                if (target.isEmpty()) {
                    lang.send(player, ninja ? AbilityMessages.NO_VICTIM : AbilityMessages.NO_ATTACKER,
                            "seconds", String.valueOf(within));
                    return;
                }
                teleportLater(player, ability, target.get());
            }
            case INVISIBILITY -> {
                apply(player, List.of(p.effect("effect")));
                if (p.bool("hide-armor")) {
                    invisible.put(player.getUniqueId(), ability);
                    hideArmor(player);
                }
                lang.send(player, AbilityMessages.USED, "ability", display(ability));
            }
            case TIME_WARP -> {
                Pearl pearl = lastPearl.get(player.getUniqueId());
                long within = p.whole("pearl-within-seconds");
                if (pearl == null || now - pearl.at() > within * 1000L
                        || !Objects.equals(pearl.from().getWorld(), player.getWorld())) {
                    lang.send(player, AbilityMessages.NO_PEARL, "seconds", String.valueOf(within));
                    return;
                }
                long delay = p.whole("delay-seconds");
                lang.send(player, AbilityMessages.WARPING, "seconds", String.valueOf(delay));
                Location back = pearl.from().clone();
                schedule(player, delay, () -> player.teleport(back, PlayerTeleportEvent.TeleportCause.PLUGIN));
            }
            case POCKET_BARD -> {
                if (settings.pocketBard().isEmpty()) {
                    return;
                }
                // Nothing is spent until a set is picked: closing the menu costs nothing.
                player.openInventory(new PocketBardMenu(this, ability).getInventory());
                return;
            }
            case BERSERK -> {
                apply(player, p.effects("effects"));
                berserk.put(player.getUniqueId(), Map.entry(now + p.whole("seconds") * 1000L, p.strings("denied-potions")));
                lang.send(player, AbilityMessages.USED, "ability", display(ability));
            }
            case CLOSE_CALL -> {
                double maxHealth = p.decimal("max-health-hearts") * 2.0;
                if (player.getHealth() > maxHealth) {
                    lang.send(player, AbilityMessages.HEALTH_TOO_HIGH, "ability", display(ability),
                            "hearts", formatNumber(p.decimal("max-health-hearts")));
                    return;
                }
                apply(player, p.effects("effects"));
                lang.send(player, AbilityMessages.USED, "ability", display(ability));
            }
            case BELCH_BOMB -> {
                List<Player> reached = enemiesAround(player, player.getLocation(), p.decimal("radius"));
                for (Player enemy : reached) {
                    apply(enemy, p.effects("effects"));
                    lang.send(enemy, AbilityMessages.BELCH_BOMB_HIT, "player", player.getName());
                }
                lang.send(player, AbilityMessages.AREA_USED, "ability", display(ability),
                        "count", String.valueOf(reached.size()));
            }
            case EFFECTS -> {
                apply(player, p.effects("effects"));
                lang.send(player, AbilityMessages.USED, "ability", display(ability));
            }
            case LUCKY_BARD -> {
                boolean lucky = AbilityRules.chance(p.decimal("positive-chance"), ThreadLocalRandom.current().nextDouble());
                List<AbilityEffect> effects = p.effects(lucky ? "good-effects" : "bad-effects");
                apply(player, effects);
                lang.send(player, lucky ? AbilityMessages.LUCKY : AbilityMessages.UNLUCKY, "ability", display(ability),
                        "effects", String.join(", ", effects.stream().map(AbilityModule::effectName).toList()));
            }
            case CLEANSE -> {
                int removed = 0;
                for (PotionEffect effect : List.copyOf(player.getActivePotionEffects())) {
                    if (effect.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL) {
                        player.removePotionEffect(effect.getType());
                        removed++;
                    }
                }
                lang.send(player, AbilityMessages.CLEANSED, "count", String.valueOf(removed));
            }
            case NO_FALL -> {
                long seconds = p.whole("seconds");
                noFall.put(player.getUniqueId(), now + seconds * 1000L);
                lang.send(player, AbilityMessages.ACTIVE, "ability", display(ability), "seconds", String.valueOf(seconds));
            }
            case ROCKET -> {
                Vector velocity = player.getVelocity();
                player.setVelocity(new Vector(velocity.getX(), AbilityRules.launchSpeed(p.decimal("height")),
                        velocity.getZ()));
                noFall.put(player.getUniqueId(), now + p.whole("no-fall-seconds") * 1000L);
                player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation(), 30, 0.2, 0.1, 0.2, 0.05);
                lang.send(player, AbilityMessages.USED, "ability", display(ability));
            }
            case HULK_SMASH -> {
                List<Player> reached = enemiesAround(player, player.getLocation(), p.decimal("radius"));
                double up = AbilityRules.launchSpeed(p.decimal("height"));
                for (Player enemy : reached) {
                    Vector away = enemy.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
                    Vector push = away.lengthSquared() < 1e-6 ? new Vector() : away.normalize().multiply(p.decimal("push"));
                    enemy.setVelocity(push.setY(up));
                    lang.send(enemy, AbilityMessages.AREA_HIT, "player", player.getName(), "ability", display(ability));
                }
                player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 3, 1, 0.2, 1);
                lang.send(player, AbilityMessages.AREA_USED, "ability", display(ability),
                        "count", String.valueOf(reached.size()));
            }
            case COMBO_FISH -> {
                long seconds = p.whole("seconds");
                comboFish.put(player.getUniqueId(), new ComboFish(now + seconds * 1000L,
                        (int) Math.max(0, p.whole("hit-delay-ticks"))));
                lang.send(player, AbilityMessages.ACTIVE, "ability", display(ability), "seconds", String.valueOf(seconds));
            }
            case SHOTGUN -> shotgun(player, ability);
            case SUN -> sun(player, ability);
            default -> {
                // Thrown, hit with, shot or reeled in: not a right-click ability.
                return;
            }
        }
        started(player, ability, now);
        consume(player, ability, item);
    }

    /** Eggs scattered in a cone, as a shotgun's pellets, and the user pushed back. */
    private void shotgun(Player player, Ability ability) {
        AbilityParams p = ability.params();
        Location eye = player.getEyeLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (long i = 0; i < p.whole("projectiles"); i++) {
            double[] turn = AbilityRules.pellet(p.decimal("spread"), random.nextDouble(), random.nextDouble());
            Location aim = eye.clone();
            aim.setYaw(eye.getYaw() + (float) turn[0]);
            aim.setPitch((float) Math.max(-90.0, Math.min(90.0, eye.getPitch() + turn[1])));
            Egg egg = player.launchProjectile(Egg.class, aim.getDirection().multiply(p.decimal("speed")));
            egg.getPersistentDataContainer().set(projectileKey, PersistentDataType.STRING, ability.id());
        }
        player.setVelocity(player.getVelocity().add(eye.getDirection().multiply(-p.decimal("recoil"))));
        player.getWorld().spawnParticle(Particle.FLAME, eye.clone().add(eye.getDirection()), 15, 0.2, 0.2, 0.2, 0.05);
    }

    /** Fireworks burst around the user; the enemies in range pay for each other. */
    private void sun(Player player, Ability ability) {
        AbilityParams p = ability.params();
        double radius = p.decimal("radius");
        Location center = player.getLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        FireworkEffect burst = FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.YELLOW, Color.ORANGE).withFade(Color.RED).withFlicker().build();
        for (long i = 0; i < p.whole("fireworks"); i++) {
            Location at = center.clone().add(random.nextDouble(-radius / 2, radius / 2), 1.5 + random.nextDouble(2),
                    random.nextDouble(-radius / 2, radius / 2));
            Firework firework = center.getWorld().spawn(at, Firework.class, spawned -> {
                spawned.getPersistentDataContainer().set(fireworkKey, PersistentDataType.BYTE, (byte) 1);
                spawned.setShooter(player);
                FireworkMeta meta = spawned.getFireworkMeta();
                meta.addEffect(burst);
                spawned.setFireworkMeta(meta);
            });
            firework.detonate();
        }
        // Only the players it may harm are caught, and counted: never the user, nor a teammate.
        List<Player> caught = enemiesAround(player, center, radius);
        double damage = AbilityRules.sunDamage(caught.size(), (int) p.whole("max-players"),
                p.decimal("damage-hearts-per-player"));
        for (Player enemy : caught) {
            trueDamage(enemy, player, damage);
            enemy.setFireTicks((int) Math.max(enemy.getFireTicks(), p.whole("fire-seconds") * 20));
            apply(enemy, List.of(new AbilityEffect("blindness", 1, (int) p.whole("blindness-seconds"))));
            lang.send(enemy, AbilityMessages.AREA_HIT, "player", player.getName(), "ability", display(ability));
        }
        lang.send(player, AbilityMessages.AREA_USED, "ability", display(ability), "count", String.valueOf(caught.size()));
    }

    /** Whether this is one of the Sun's fireworks: they burst, and hurt nobody. */
    public boolean isAbilityFirework(Entity entity) {
        return entity instanceof Firework && entity.getPersistentDataContainer().has(fireworkKey);
    }

    /** Pocket Bard: a set is picked from its menu - now the ability is spent. */
    public void pickPocketBard(Player player, Ability pocketBard, PocketBardItem picked) {
        long now = System.currentTimeMillis();
        player.closeInventory();
        if (!mayUse(player, pocketBard, now)) {
            return;
        }
        ItemStack held = null;
        for (ItemStack candidate : List.of(player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand())) {
            if (abilityOf(candidate).map(a -> a.id().equals(pocketBard.id())).orElse(false)) {
                held = candidate;
                break;
            }
        }
        if (held == null) {
            // The item left the hand while the menu was open.
            return;
        }
        started(player, pocketBard, now);
        consume(player, pocketBard, held);
        player.getInventory().addItem(buildPocketItem(picked)).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        lang.send(player, AbilityMessages.POCKET_BARD_GIVEN, "amount", String.valueOf(picked.amount()),
                "item", LangManager.colorize(picked.name()));
    }

    /**
     * A Pocket Bard item right-clicked: its effect for the teammates in range. Its own
     * cooldown, per set, and the shared one - so Strength II and Resistance III are
     * not given at once - and the zones where abilities are refused.
     */
    public void usePocketItem(Player player, PocketBardItem pocket, ItemStack item) {
        if (!settings.enabled()) {
            lang.send(player, AbilityMessages.MODULE_OFF);
            return;
        }
        if (disabledAt(player.getLocation(), settings.disabledIn())) {
            lang.send(player, AbilityMessages.DISABLED_HERE);
            return;
        }
        long now = System.currentTimeMillis();
        // The shared cooldown too: Strength II and Resistance III are not given at once.
        long global = cooldowns.remaining(player.getUniqueId(), GLOBAL_COOLDOWN, now);
        if (global > 0) {
            lang.send(player, AbilityMessages.GLOBAL_COOLDOWN, "time", Durations.formatWithSeconds(global));
            return;
        }
        String key = "pocket:" + pocket.id();
        long left = cooldowns.remaining(player.getUniqueId(), key, now);
        if (left > 0) {
            lang.send(player, AbilityMessages.COOLDOWN, "ability", LangManager.colorize(pocket.name()),
                    "time", Durations.formatWithSeconds(left));
            return;
        }
        cooldowns.start(player.getUniqueId(), key, pocket.cooldownSeconds(), now);
        cooldowns.start(player.getUniqueId(), GLOBAL_COOLDOWN, settings.globalCooldownSeconds(), now);
        List<Player> reached = teammatesAround(player, pocket.radius(), pocket.includeSelf());
        for (Player teammate : reached) {
            apply(teammate, List.of(pocket.effect()));
        }
        item.setAmount(item.getAmount() - 1);
        lang.send(player, AbilityMessages.POCKET_BARD_USED, "item", LangManager.colorize(pocket.name()),
                "count", String.valueOf(reached.size()));
    }

    // ------------------------------------------------------------------
    // Delayed teleports: Ninja, Anti Trap Star, Time Warp
    // ------------------------------------------------------------------

    private void teleportLater(Player player, Ability ability, Player target) {
        long delay = ability.params().whole("delay-seconds");
        lang.send(player, AbilityMessages.TELEPORTING, "player", target.getName(), "seconds", String.valueOf(delay));
        UUID targetId = target.getUniqueId();
        schedule(player, delay, () -> {
            Player now = Bukkit.getPlayer(targetId);
            if (now == null || !now.getWorld().equals(player.getWorld()) || pvp == null
                    || pvp.judgeHarm(player, now).isPresent()) {
                // Gone, in another world, or where they may not be reached: a safe zone.
                lang.send(player, AbilityMessages.TELEPORT_CANCELLED);
                return;
            }
            player.teleport(now.getLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
            lang.send(player, AbilityMessages.TELEPORTED, "player", now.getName());
        });
    }

    /** Runs this later for this player, replacing a teleport of theirs still waiting. */
    private void schedule(Player player, long delaySeconds, Runnable action) {
        UUID id = player.getUniqueId();
        BukkitTask previous = teleports.remove(id);
        if (previous != null) {
            previous.cancel();
        }
        teleports.put(id, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            teleports.remove(id);
            if (player.isOnline() && !player.isDead()) {
                action.run();
            }
        }, Math.max(1L, delaySeconds * 20L)));
    }

    private Optional<Player> recentAttacker(Player victim, Map<UUID, Hit> source, long withinSeconds, long now) {
        Hit hit = source.get(victim.getUniqueId());
        if (hit == null || now - hit.at() > withinSeconds * 1000L) {
            return Optional.empty();
        }
        return Optional.ofNullable(Bukkit.getPlayer(hit.attacker()));
    }

    /** Remembers who hit whom last: Focus Mode and the teleports go by it - the Ninja by whom one hit. */
    public void recordHit(Player attacker, Player victim) {
        long now = System.currentTimeMillis();
        lastHit.put(victim.getUniqueId(), new Hit(attacker.getUniqueId(), now));
        lastVictim.put(attacker.getUniqueId(), new Hit(victim.getUniqueId(), now));
    }

    public void recordPearl(Player player) {
        lastPearl.put(player.getUniqueId(), new Pearl(player.getLocation().clone(), System.currentTimeMillis()));
    }

    // ------------------------------------------------------------------
    // Throws: Switcher, Rage Ball, Thrown Effects, Fake Pearl - and the Shotgun's eggs
    // ------------------------------------------------------------------

    /** @return whether the throw goes ahead: marked, and its cooldown started */
    public boolean launch(Player player, Ability ability, Projectile projectile) {
        long now = System.currentTimeMillis();
        if (!mayUse(player, ability, now)) {
            return false;
        }
        projectile.getPersistentDataContainer().set(projectileKey, PersistentDataType.STRING, ability.id());
        started(player, ability, now);
        if (ability.type() == AbilityType.RAGE_BALL && ability.params().bool("cooldown-whole-team")) {
            for (Player teammate : teammatesAround(player, Double.MAX_VALUE, false)) {
                cooldowns.start(teammate.getUniqueId(), ability.id(), ability.cooldownSeconds(), now);
            }
        }
        return true;
    }

    public Optional<Ability> thrownAbility(Projectile projectile) {
        String id = projectile.getPersistentDataContainer().get(projectileKey, PersistentDataType.STRING);
        return id == null ? Optional.empty() : settings.ability(id);
    }

    /** A marked projectile landed, on a player or anywhere. */
    public void landed(Projectile projectile, Ability ability, Player hitPlayer) {
        if (!(projectile.getShooter() instanceof Player thrower) || !thrower.isOnline()) {
            return;
        }
        AbilityParams p = ability.params();
        if (ability.type() == AbilityType.SWITCHER) {
            if (hitPlayer == null || hitPlayer.equals(thrower)
                    || !hitPlayer.getWorld().equals(thrower.getWorld())
                    || hitPlayer.getLocation().distance(thrower.getLocation()) > p.decimal("distance")
                    || (pvp != null && pvp.judgeHarm(thrower, hitPlayer).isPresent())) {
                return;
            }
            Location mine = thrower.getLocation().clone();
            Location theirs = hitPlayer.getLocation().clone();
            thrower.teleport(theirs, PlayerTeleportEvent.TeleportCause.PLUGIN);
            hitPlayer.teleport(mine, PlayerTeleportEvent.TeleportCause.PLUGIN);
            lang.send(thrower, AbilityMessages.SWITCHED, "player", hitPlayer.getName());
            lang.send(hitPlayer, AbilityMessages.SWITCHED_VICTIM, "player", thrower.getName());
        } else if (ability.type() == AbilityType.RAGE_BALL) {
            Location at = projectile.getLocation();
            at.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
            double radius = p.decimal("radius");
            int count = 0;
            for (Player teammate : teammatesAround(thrower, at, radius, true)) {
                apply(teammate, p.effects("team-effects"));
                if (!teammate.equals(thrower)) {
                    lang.send(teammate, AbilityMessages.RAGE_BALL_TEAM, "player", thrower.getName());
                }
                count++;
            }
            for (Player enemy : enemiesAround(thrower, at, radius)) {
                apply(enemy, p.effects("enemy-effects"));
                lang.send(enemy, AbilityMessages.RAGE_BALL_ENEMY, "player", thrower.getName());
                count++;
            }
            lang.send(thrower, AbilityMessages.AREA_USED, "ability", display(ability), "count", String.valueOf(count));
        } else if (ability.type() == AbilityType.THROWN_EFFECTS || ability.type() == AbilityType.SHOTGUN) {
            if (hitPlayer == null || hitPlayer.equals(thrower)
                    || (pvp != null && pvp.judgeHarm(thrower, hitPlayer).isPresent())) {
                return;
            }
            if (ability.type() == AbilityType.THROWN_EFFECTS) {
                apply(hitPlayer, p.effects("effects"));
                lang.send(thrower, AbilityMessages.APPLIED, "ability", display(ability), "player", hitPlayer.getName());
                lang.send(hitPlayer, AbilityMessages.RECEIVED, "ability", display(ability), "player", thrower.getName());
            } else {
                trueDamage(hitPlayer, thrower, p.decimal("damage-hearts") * 2.0);
                hitPlayer.setFireTicks((int) Math.max(hitPlayer.getFireTicks(), p.whole("fire-seconds") * 20));
            }
        }
    }

    /** Whether a projectile is a fake pearl: where it comes down, it is gone and nobody moves. */
    public boolean isFakePearl(Projectile projectile) {
        return thrownAbility(projectile).map(a -> a.type() == AbilityType.FAKE_PEARL).orElse(false);
    }

    // ------------------------------------------------------------------
    // Grappling Hook, fall damage
    // ------------------------------------------------------------------

    /** A Grappling Hook reeled in, its hook stuck in a block: the player flies to it. */
    public void grapple(Player player, Ability ability, FishHook hook) {
        long now = System.currentTimeMillis();
        if (!mayUse(player, ability, now)) {
            return;
        }
        Location to = hook.getLocation().clone();
        if (!Objects.equals(player.getWorld(), to.getWorld())) {
            return;
        }
        started(player, ability, now);
        // A tick later, once the reel is done: whether on the ground, jumping or falling.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !player.getWorld().equals(to.getWorld())) {
                return;
            }
            Location from = player.getLocation();
            double[] v = AbilityRules.pullVelocity(to.getX() - from.getX(), to.getY() - from.getY(),
                    to.getZ() - from.getZ(), ability.params().decimal("pull"), ability.params().decimal("lift"),
                    ability.params().decimal("max-speed"));
            player.setFallDistance(0f);
            player.setVelocity(new Vector(v[0], v[1], v[2]));
        });
    }

    /** Whether a hook is held by a block: stuck in one, lying on one, or against one's side. */
    public static boolean hookHeld(FishHook hook) {
        if (hook.isOnGround()) {
            return true;
        }
        Location at = hook.getLocation();
        double[][] around = {{0, 0, 0}, {0.3, 0, 0}, {-0.3, 0, 0}, {0, 0.3, 0}, {0, -0.3, 0}, {0, 0, 0.3}, {0, 0, -0.3}};
        for (double[] d : around) {
            if (at.clone().add(d[0], d[1], d[2]).getBlock().isSolid()) {
                return true;
            }
        }
        return false;
    }

    /** Whether a fall does this player no harm: a Sticky Web or a Rocket running, a Grappling Hook in hand. */
    public boolean cancelsFall(Player player) {
        Long until = noFall.get(player.getUniqueId());
        if (until != null) {
            if (until >= System.currentTimeMillis()) {
                return true;
            }
            noFall.remove(player.getUniqueId());
        }
        for (ItemStack held : List.of(player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand())) {
            Optional<Ability> ability = abilityOf(held);
            if (ability.isPresent() && ability.get().type() == AbilityType.GRAPPLING_HOOK
                    && ability.get().params().bool("no-fall-while-held")) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Hits: the windows, focus, hit-with abilities
    // ------------------------------------------------------------------

    /**
     * A melee hit one player lands on another, not refused: what the running
     * abilities make of it. Returns the damage multiplier it adds (Lucky Mode, Focus
     * Mode) - the caller applies it.
     */
    public double meleeHit(Player attacker, Player victim) {
        long now = System.currentTimeMillis();
        double multiplier = 1.0;
        Map<String, Long> running = windows.get(attacker.getUniqueId());
        if (running != null) {
            running.entrySet().removeIf(entry -> entry.getValue() < now);
            for (String id : running.keySet()) {
                Ability ability = settings.ability(id).orElse(null);
                if (ability == null) {
                    continue;
                }
                AbilityParams p = ability.params();
                ThreadLocalRandom random = ThreadLocalRandom.current();
                switch (ability.type()) {
                    case THUNDERBOLT -> {
                        if (AbilityRules.chance(p.decimal("chance"), random.nextDouble())) {
                            victim.getWorld().strikeLightningEffect(victim.getLocation());
                            trueDamage(victim, attacker, p.decimal("damage-hearts") * 2.0);
                            lang.send(victim, AbilityMessages.STRUCK, "player", attacker.getName());
                        }
                    }
                    case LUCKY_MODE -> multiplier *= AbilityRules.luckyMultiplier(
                            p.decimal("min-percent"), p.decimal("max-percent"), random.nextDouble());
                    case SWITCH_STICK -> {
                        if (AbilityRules.chance(p.decimal("chance"), random.nextDouble())) {
                            Location facing = victim.getLocation();
                            victim.setRotation(facing.getYaw() + (float) p.decimal("degrees"), facing.getPitch());
                            lang.send(attacker, AbilityMessages.TURNED, "player", victim.getName());
                            lang.send(victim, AbilityMessages.TURNED_VICTIM, "player", attacker.getName());
                        }
                    }
                    default -> {
                    }
                }
            }
        }
        Focus focus = focuses.get(attacker.getUniqueId());
        if (focus != null) {
            if (focus.until() < now) {
                focuses.remove(attacker.getUniqueId());
            } else if (focus.target().equals(victim.getUniqueId())) {
                multiplier *= focus.multiplier();
            }
        }
        Combo combo = combos.get(attacker.getUniqueId());
        if (combo != null && combo.until >= now) {
            combo.hits = (int) Math.min(combo.ability.params().whole("max-hits"), combo.hits + 1L);
        }
        ComboFish fish = comboFish.get(attacker.getUniqueId());
        if (fish != null) {
            if (fish.until() < now) {
                comboFish.remove(attacker.getUniqueId());
            } else {
                // The game makes them untouchable for 10 ticks after this hit: shortened, once it has.
                int delay = fish.delayTicks();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (victim.isOnline()) {
                        victim.setNoDamageTicks(Math.max(0, victim.getMaximumNoDamageTicks() / 2 + delay - 1));
                    }
                });
            }
        }
        Chaos under = chaos.get(victim.getUniqueId());
        if (under != null) {
            if (under.until() < now) {
                chaos.remove(victim.getUniqueId());
            } else if (under.attacker().equals(attacker.getUniqueId())
                    && AbilityRules.chance(under.chance(), ThreadLocalRandom.current().nextDouble())) {
                victim.openInventory(MenuType.CRAFTING.builder().checkReachable(false).build(victim));
            }
        }
        hitWithItem(attacker, victim, now);
        return multiplier;
    }

    /** The abilities used by hitting a player with the item in hand, once or {@code hits-required} times. */
    private void hitWithItem(Player attacker, Player victim, long now) {
        ItemStack held = attacker.getInventory().getItemInMainHand();
        Ability ability = abilityOf(held).filter(a -> a.type().trigger() == AbilityType.Trigger.HIT).orElse(null);
        if (ability == null) {
            return;
        }
        AbilityParams p = ability.params();
        // The gate is asked on the first hit of a count.
        long required = Math.max(1, p.whole("hits-required"));
        int count = hits.hit(attacker.getUniqueId(), ability.id(), victim.getUniqueId(), now,
                settings.hitsWithinSeconds() * 1000L);
        if (count == 1 && !mayUse(attacker, ability, now)) {
            hits.reset(attacker.getUniqueId(), ability.id());
            return;
        }
        if (count < required) {
            lang.send(attacker, AbilityMessages.HITS_LEFT, "ability", display(ability),
                    "left", String.valueOf(required - count), "player", victim.getName());
            return;
        }
        hits.reset(attacker.getUniqueId(), ability.id());
        HitOutcome outcome = landHit(attacker, victim, ability, now);
        if (outcome == HitOutcome.REFUSED) {
            return;
        }
        started(attacker, ability, now);
        if (outcome == HitOutcome.LANDED) {
            consume(attacker, ability, held);
        }
    }

    /** What became of a hit ability: nothing, a chance missed, or done. */
    private enum HitOutcome {
        /** It cannot work on that player - not in its class, no helmet: nothing starts. */
        REFUSED,
        /** Its chance missed: the cooldown starts, the item and its uses are kept. */
        MISSED,
        /** It worked: cooldown, and one use or one item spent. */
        LANDED
    }

    /** What a hit ability does to the player hit. */
    private HitOutcome landHit(Player attacker, Player victim, Ability ability, long now) {
        AbilityParams p = ability.params();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String name = display(ability);
        switch (ability.type()) {
            case CRAFTING_CHAOS -> {
                long seconds = p.whole("seconds");
                chaos.put(victim.getUniqueId(), new Chaos(attacker.getUniqueId(), now + seconds * 1000L, p.decimal("chance")));
                lang.send(attacker, AbilityMessages.CHAOS_APPLIED, "player", victim.getName(), "seconds", String.valueOf(seconds));
                lang.send(victim, AbilityMessages.CHAOS_RECEIVED, "player", attacker.getName());
                return HitOutcome.LANDED;
            }
            case ANTI_BUILD -> {
                long seconds = p.whole("seconds");
                applyAntiBuild(victim, seconds);
                apply(attacker, p.effects("user-effects"));
                lang.send(attacker, AbilityMessages.ANTI_BUILD_APPLIED, "player", victim.getName(),
                        "seconds", String.valueOf(seconds));
                return HitOutcome.LANDED;
            }
            case THORNS -> {
                long seconds = p.whole("seconds");
                String percent = formatNumber(p.decimal("reflect-percent"));
                thorns.put(victim.getUniqueId(), new Thorns(attacker.getUniqueId(), now + seconds * 1000L,
                        p.decimal("reflect-percent")));
                lang.send(attacker, AbilityMessages.THORNS_APPLIED, "player", victim.getName(), "percent", percent,
                        "seconds", String.valueOf(seconds));
                lang.send(victim, AbilityMessages.THORNS_RECEIVED, "player", attacker.getName(), "percent", percent,
                        "seconds", String.valueOf(seconds));
                return HitOutcome.LANDED;
            }
            case PUMPKIN -> {
                List<String> allowed = p.strings("classes").stream().map(c -> c.toLowerCase(Locale.ROOT)).toList();
                if (!allowed.isEmpty() && classes != null && classes.getManager() != null) {
                    boolean inClass = classes.getManager().active(victim.getUniqueId())
                            .map(c -> allowed.contains(c.id().toLowerCase(Locale.ROOT))).orElse(false);
                    if (!inClass) {
                        lang.send(attacker, AbilityMessages.WRONG_CLASS, "ability", name,
                                "classes", String.join(", ", p.strings("classes")));
                        return HitOutcome.REFUSED;
                    }
                }
                ItemStack helmet = victim.getInventory().getHelmet();
                if (helmet == null || helmet.isEmpty() || pumpkins.containsKey(victim.getUniqueId())) {
                    lang.send(attacker, AbilityMessages.NO_HELMET, "ability", name, "player", victim.getName());
                    return HitOutcome.REFUSED;
                }
                if (missed(attacker, victim, ability, random)) {
                    return HitOutcome.MISSED;
                }
                pumpkin(victim, helmet, p.whole("seconds"));
            }
            case HIT_EFFECTS -> {
                if (missed(attacker, victim, ability, random)) {
                    return HitOutcome.MISSED;
                }
                apply(victim, p.effects("effects"));
            }
            case DISARM -> {
                if (missed(attacker, victim, ability, random)) {
                    return HitOutcome.MISSED;
                }
                disarm(victim, random);
            }
            case SCRAMBLE -> {
                PlayerInventory inventory = victim.getInventory();
                List<ItemStack> hotbar = new ArrayList<>();
                for (int slot = 0; slot < 9; slot++) {
                    hotbar.add(inventory.getItem(slot));
                }
                Collections.shuffle(hotbar, random);
                for (int slot = 0; slot < 9; slot++) {
                    inventory.setItem(slot, hotbar.get(slot));
                }
            }
            case STARVE -> {
                // Saturation first, or it would eat the Hunger before the food bar does.
                victim.setSaturation(0f);
                victim.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, (int) (p.whole("seconds") * 20),
                        AbilityRules.hungerAmplifier(p.whole("food-lost"), p.whole("seconds"))));
            }
            case GRAB -> {
                double pull = p.decimal("pull");
                double maxSpeed = p.decimal("max-speed");
                // After the hit's own knockback, which would undo it.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!victim.isOnline() || !attacker.isOnline() || !victim.getWorld().equals(attacker.getWorld())) {
                        return;
                    }
                    Location from = victim.getLocation();
                    Location to = attacker.getLocation();
                    double[] v = AbilityRules.pullVelocity(to.getX() - from.getX(), to.getY() - from.getY(),
                            to.getZ() - from.getZ(), pull, 0.0, maxSpeed);
                    victim.setVelocity(new Vector(v[0], v[1], v[2]));
                });
            }
            default -> {
                return HitOutcome.REFUSED;
            }
        }
        lang.send(attacker, AbilityMessages.APPLIED, "ability", name, "player", victim.getName());
        lang.send(victim, AbilityMessages.RECEIVED, "ability", name, "player", attacker.getName());
        return HitOutcome.LANDED;
    }

    /** Rolls an ability's {@code chance}: {@code true}, and told, when it misses. */
    private boolean missed(Player attacker, Player victim, Ability ability, ThreadLocalRandom random) {
        if (AbilityRules.chance(ability.params().decimal("chance"), random.nextDouble())) {
            return false;
        }
        lang.send(attacker, AbilityMessages.NO_LUCK, "ability", display(ability), "player", victim.getName());
        return true;
    }

    /** The weapon in hand swaps places with another item: from the inventory above the hotbar if it can. */
    private static void disarm(Player victim, ThreadLocalRandom random) {
        PlayerInventory inventory = victim.getInventory();
        int hand = inventory.getHeldItemSlot();
        List<Integer> filled = new ArrayList<>();
        List<Integer> any = new ArrayList<>();
        for (int slot = 9; slot < 36; slot++) {
            any.add(slot);
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.isEmpty()) {
                filled.add(slot);
            }
        }
        List<Integer> from = filled.isEmpty() ? any : filled;
        int other = from.get(random.nextInt(from.size()));
        ItemStack weapon = inventory.getItem(hand);
        inventory.setItem(hand, inventory.getItem(other));
        inventory.setItem(other, weapon);
    }

    /** Damage one player dealt another: partly back to them, if the other put thorns on them. */
    public void reflect(Player attacker, Player victim, double damage) {
        Thorns state = thorns.get(attacker.getUniqueId());
        if (state == null) {
            return;
        }
        if (state.until() < System.currentTimeMillis()) {
            thorns.remove(attacker.getUniqueId());
            return;
        }
        double back = damage * state.percent() / 100.0;
        if (state.owner().equals(victim.getUniqueId()) && back > 0) {
            trueDamage(attacker, victim, back);
        }
    }

    // ------------------------------------------------------------------
    // Pumpkin Reaper: a helmet swapped for a pumpkin
    // ------------------------------------------------------------------

    private void pumpkin(Player victim, ItemStack helmet, long seconds) {
        ItemStack pumpkin = ItemStack.of(Material.CARVED_PUMPKIN);
        Enchantment binding = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                .get(NamespacedKey.minecraft("binding_curse"));
        if (binding != null) {
            // Worn until given back: it cannot be taken off.
            pumpkin.addUnsafeEnchantment(binding, 1);
            pumpkin.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);
        }
        pumpkin.editPersistentDataContainer(data -> data.set(pumpkinKey, PersistentDataType.BYTE, (byte) 1));
        victim.getInventory().setHelmet(pumpkin);
        UUID id = victim.getUniqueId();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                giveHelmetBack(player, true);
            }
        }, Math.max(1L, seconds * 20L));
        pumpkins.put(id, new Pumpkin(helmet.clone(), task));
    }

    private boolean isLentPumpkin(ItemStack item) {
        return item != null && !item.isEmpty() && item.getPersistentDataContainer().has(pumpkinKey);
    }

    /** The pumpkin goes, wherever it is; the helmet goes back on - or in the inventory, if the head is taken. */
    private void giveHelmetBack(Player player, boolean tell) {
        Pumpkin lent = pumpkins.remove(player.getUniqueId());
        if (lent == null) {
            return;
        }
        lent.task().cancel();
        PlayerInventory inventory = player.getInventory();
        if (isLentPumpkin(inventory.getHelmet())) {
            inventory.setHelmet(null);
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isLentPumpkin(inventory.getItem(slot))) {
                inventory.setItem(slot, null);
            }
        }
        ItemStack helmet = inventory.getHelmet();
        if (helmet == null || helmet.isEmpty()) {
            inventory.setHelmet(lent.helmet());
        } else {
            inventory.addItem(lent.helmet()).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        if (tell) {
            lang.send(player, AbilityMessages.HELMET_BACK);
        }
    }

    /** A death under a pumpkin: the helmet drops, not the pumpkin - or stays, if the inventory does. */
    public void pumpkinDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (!pumpkins.containsKey(player.getUniqueId())) {
            return;
        }
        if (event.getKeepInventory()) {
            giveHelmetBack(player, false);
            return;
        }
        Pumpkin lent = pumpkins.remove(player.getUniqueId());
        lent.task().cancel();
        event.getDrops().removeIf(this::isLentPumpkin);
        event.getDrops().add(lent.helmet());
    }

    /** Damage that goes through armour, credited to the attacker when it kills. */
    private static void trueDamage(Player victim, Player attacker, double amount) {
        double absorption = victim.getAbsorptionAmount();
        double fromAbsorption = Math.min(absorption, amount);
        victim.setAbsorptionAmount(absorption - fromAbsorption);
        double left = amount - fromAbsorption;
        if (left <= 0) {
            return;
        }
        if (victim.getHealth() - left <= 0) {
            // A death the game credits to the attacker: deathban, DTR, statistics follow.
            victim.damage(Math.max(left, victim.getHealth()) * 1000.0, attacker);
        } else {
            victim.setHealth(victim.getHealth() - left);
            victim.playHurtAnimation(0f);
        }
    }

    private static boolean isBlockName(String name) {
        Material material = Material.getMaterial(name);
        return material != null && material.isBlock();
    }

    // ------------------------------------------------------------------
    // Anti-build, Berserk, Invisibility, Portable Archer
    // ------------------------------------------------------------------

    private void applyAntiBuild(Player victim, long seconds) {
        antiBuild.put(victim.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        lang.send(victim, AbilityMessages.ANTI_BUILD_RECEIVED, "seconds", String.valueOf(seconds));
    }

    /** @return milliseconds left under anti-build, or {@code 0} */
    public long antiBuildLeft(Player player) {
        Long until = antiBuild.get(player.getUniqueId());
        long left = until == null ? 0 : until - System.currentTimeMillis();
        if (left <= 0) {
            antiBuild.remove(player.getUniqueId());
            return 0;
        }
        return left;
    }

    /** The blocks an anti-build refuses to open: the Anti Build ability's list, the first one found. */
    public boolean blockedUnderAntiBuild(Material material) {
        for (Ability ability : settings.abilities()) {
            if (ability.type() == AbilityType.ANTI_BUILD) {
                return AbilityRules.blocked(ability.params().strings("blocked-blocks"), material.name(),
                        AbilityModule::isBlockName);
            }
        }
        return AbilityRules.blocked(AbilityParams.defaults(AbilityType.ANTI_BUILD).strings("blocked-blocks"),
                material.name(), AbilityModule::isBlockName);
    }

    /** @return milliseconds left under berserk if this potion is one it refuses, else {@code 0} */
    public long berserkRefuses(Player player, String potionKey) {
        Map.Entry<Long, List<String>> state = berserk.get(player.getUniqueId());
        if (state == null) {
            return 0;
        }
        long left = state.getKey() - System.currentTimeMillis();
        if (left <= 0) {
            berserk.remove(player.getUniqueId());
            return 0;
        }
        return state.getValue().contains(potionKey) ? left : 0;
    }

    /** A player under this module's invisibility was hit: seen again, if the ability says so. */
    public void hitWhileInvisible(Player player) {
        Ability ability = invisible.get(player.getUniqueId());
        if (ability == null || !ability.params().bool("reveal-on-hit")) {
            return;
        }
        invisible.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        showArmor(player);
        lang.send(player, AbilityMessages.REVEALED);
    }

    private void hideArmor(Player player) {
        Map<EquipmentSlot, ItemStack> nothing = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET)) {
            nothing.put(slot, null);
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(player) && viewer.getWorld().equals(player.getWorld())) {
                viewer.sendEquipmentChange(player, nothing);
            }
        }
    }

    private void showArmor(Player player) {
        Map<EquipmentSlot, ItemStack> worn = new EnumMap<>(EquipmentSlot.class);
        worn.put(EquipmentSlot.HEAD, player.getInventory().getHelmet());
        worn.put(EquipmentSlot.CHEST, player.getInventory().getChestplate());
        worn.put(EquipmentSlot.LEGS, player.getInventory().getLeggings());
        worn.put(EquipmentSlot.FEET, player.getInventory().getBoots());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(player) && viewer.getWorld().equals(player.getWorld())) {
                viewer.sendEquipmentChange(player, worn);
            }
        }
    }

    /** @return whether the shot goes ahead; the arrow is marked to tag whoever it hits */
    public boolean shoot(Player player, Ability ability, Projectile arrow, ItemStack bow) {
        long now = System.currentTimeMillis();
        if (!mayUse(player, ability, now)) {
            return false;
        }
        arrow.getPersistentDataContainer().set(projectileKey, PersistentDataType.STRING, ability.id());
        started(player, ability, now);
        consume(player, ability, bow);
        return true;
    }

    /** A Portable Archer's arrow hit a player, and the hit stood: they are archer-tagged. */
    public void archerTag(Player shooter, Player victim, Ability ability) {
        if (classes == null) {
            return;
        }
        AbilityParams p = ability.params();
        long seconds = p.whole("tag-seconds");
        classes.getArcherTags().tag(victim.getUniqueId(), new ArcherTag((int) seconds, p.decimal("damage-multiplier")),
                System.currentTimeMillis());
        lang.send(shooter, AbilityMessages.ARCHER_TAGGED, "player", victim.getName(), "seconds", String.valueOf(seconds));
        lang.send(victim, AbilityMessages.ARCHER_TAGGED_VICTIM, "player", shooter.getName(),
                "seconds", String.valueOf(seconds));
    }

    // ------------------------------------------------------------------

    /** Once a second: combos that are over give their effect; hidden armour stays hidden. */
    private void tick() {
        long now = System.currentTimeMillis();
        combos.entrySet().removeIf(entry -> {
            Combo combo = entry.getValue();
            if (combo.until >= now) {
                return false;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && combo.hits > 0) {
                AbilityParams p = combo.ability.params();
                AbilityEffect base = p.effect("effect");
                int seconds = (int) Math.max(1, combo.hits * p.whole("seconds-per-hit"));
                apply(player, List.of(new AbilityEffect(base.effect(), base.level(), seconds)));
                lang.send(player, AbilityMessages.COMBO_ENDED, "ability", display(combo.ability),
                        "hits", String.valueOf(combo.hits), "effect", effectName(base), "seconds", String.valueOf(seconds));
            } else if (player != null) {
                lang.send(player, AbilityMessages.ENDED, "ability", display(combo.ability));
            }
            return true;
        });
        invisible.keySet().removeIf(id -> {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                if (player != null) {
                    showArmor(player);
                }
                return true;
            }
            hideArmor(player);
            return false;
        });
    }

    /** Everything a player's abilities remember, dropped: a logout or a death. */
    public void forget(Player player) {
        UUID id = player.getUniqueId();
        windows.remove(id);
        focuses.remove(id);
        combos.remove(id);
        chaos.remove(id);
        antiBuild.remove(id);
        berserk.remove(id);
        noFall.remove(id);
        comboFish.remove(id);
        thorns.remove(id);
        if (invisible.remove(id) != null) {
            showArmor(player);
        }
        BukkitTask teleport = teleports.remove(id);
        if (teleport != null) {
            teleport.cancel();
        }
        hits.forget(id);
    }

    /** On logout only: the cooldowns and what others did to them go as well. */
    public void forgetAll(Player player) {
        giveHelmetBack(player, false);
        forget(player);
        cooldowns.forget(player.getUniqueId());
        lastHit.remove(player.getUniqueId());
        lastVictim.remove(player.getUniqueId());
        lastPearl.remove(player.getUniqueId());
    }

    // ------------------------------------------------------------------
    // Players around, effects
    // ------------------------------------------------------------------

    private Optional<Team> teamOf(Player player) {
        if (teams == null || teams.getManager() == null) {
            return Optional.empty();
        }
        return teams.getManager().getTeamOf(player.getUniqueId());
    }

    private List<Player> teammatesAround(Player player, double radius, boolean includeSelf) {
        return teammatesAround(player, player.getLocation(), radius, includeSelf);
    }

    /** The player's teammates within {@code radius} of {@code center}; the player alone without a team. */
    private List<Player> teammatesAround(Player player, Location center, double radius, boolean includeSelf) {
        List<Player> out = new ArrayList<>();
        Optional<Team> team = teamOf(player);
        for (Player other : player.getWorld().getPlayers()) {
            boolean self = other.equals(player);
            boolean teammate = !self && team.isPresent()
                    && teamOf(other).map(t -> t.getId().equals(team.get().getId())).orElse(false);
            if (self ? !includeSelf : !teammate) {
                continue;
            }
            if (radius == Double.MAX_VALUE || other.getLocation().distanceSquared(center) <= radius * radius) {
                out.add(other);
            }
        }
        return out;
    }

    /** Players within {@code radius} of {@code center} the user could hit - never a teammate, never on a safe zone. */
    private List<Player> enemiesAround(Player user, Location center, double radius) {
        List<Player> out = new ArrayList<>();
        for (Player other : user.getWorld().getPlayers()) {
            if (!other.equals(user) && other.getLocation().distanceSquared(center) <= radius * radius
                    && (pvp == null || pvp.judgeHarm(user, other).isEmpty())) {
                out.add(other);
            }
        }
        return out;
    }

    private void apply(Player player, List<AbilityEffect> effects) {
        for (AbilityEffect effect : effects) {
            PotionEffectType type = Registry.MOB_EFFECT.get(NamespacedKey.minecraft(effect.effect()));
            if (type != null) {
                player.addPotionEffect(new PotionEffect(type, effect.seconds() * 20, effect.amplifier()));
            }
        }
    }

    private static String effectName(AbilityEffect effect) {
        String[] words = effect.effect().split("_");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return name + " " + (effect.level() <= numerals.length ? numerals[effect.level() - 1] : effect.level());
    }

    static String formatNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
