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
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
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
    private static final String GLOBAL_COOLDOWN = "*";
    public static final String ADMIN_PERMISSION = "hcfcore.ability.admin";
    /** Hits counted towards {@code hits-required} must come this close together. */
    private static final long HIT_WINDOW_MILLIS = 10_000L;

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
    private final Map<UUID, Hit> lastProjectileHit = new ConcurrentHashMap<>();
    private final Map<UUID, Pearl> lastPearl = new HashMap<>();
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
        if (ability.type() == AbilityType.PORTABLE_ARCHER) {
            // Breaks after so many shots: the game wears a bow by one per shot.
            item.setData(DataComponentTypes.MAX_DAMAGE, (int) Math.max(1, ability.params().whole("uses")));
            item.setData(DataComponentTypes.DAMAGE, 0);
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

    /** Takes one from the stack in hand, if the ability is consumed. */
    private static void consume(Ability ability, ItemStack item) {
        if (ability.consume() && item != null && !item.isEmpty()) {
            item.setAmount(item.getAmount() - 1);
        }
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
        long global = cooldowns.remaining(player.getUniqueId(), GLOBAL_COOLDOWN, now);
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
        cooldowns.start(player.getUniqueId(), GLOBAL_COOLDOWN, settings.globalCooldownSeconds(), now);
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
            case NINJA, TELEPORT_EYE, SAMURAI, ANTI_TRAP_STAR -> {
                if (ability.type() == AbilityType.TELEPORT_EYE && !player.isInWater()) {
                    lang.send(player, AbilityMessages.NOT_IN_WATER, "ability", display(ability));
                    return;
                }
                Map<UUID, Hit> source = ability.type() == AbilityType.ANTI_TRAP_STAR ? lastProjectileHit : lastHit;
                long within = p.whole("hit-within-seconds");
                Optional<Player> target = recentAttacker(player, source, within, now);
                if (target.isEmpty()) {
                    lang.send(player, ability.type() == AbilityType.ANTI_TRAP_STAR
                            ? AbilityMessages.NO_SHOOTER : AbilityMessages.NO_ATTACKER, "seconds", String.valueOf(within));
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
            default -> {
                // Thrown, hit with or shot: not a right-click ability.
                return;
            }
        }
        started(player, ability, now);
        consume(ability, item);
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
        consume(pocketBard, held);
        player.getInventory().addItem(buildPocketItem(picked)).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        lang.send(player, AbilityMessages.POCKET_BARD_GIVEN, "amount", String.valueOf(picked.amount()),
                "item", LangManager.colorize(picked.name()));
    }

    /** A Pocket Bard item right-clicked: its effect for the teammates in range. Only the shared rules apply. */
    public void usePocketItem(Player player, PocketBardItem pocket, ItemStack item) {
        if (!settings.enabled()) {
            lang.send(player, AbilityMessages.MODULE_OFF);
            return;
        }
        if (disabledAt(player.getLocation(), settings.disabledIn())) {
            lang.send(player, AbilityMessages.DISABLED_HERE);
            return;
        }
        List<Player> reached = teammatesAround(player, pocket.radius(), pocket.includeSelf());
        for (Player teammate : reached) {
            apply(teammate, List.of(pocket.effect()));
        }
        item.setAmount(item.getAmount() - 1);
        lang.send(player, AbilityMessages.POCKET_BARD_USED, "item", LangManager.colorize(pocket.name()),
                "count", String.valueOf(reached.size()));
    }

    // ------------------------------------------------------------------
    // Delayed teleports: Ninja, Teleport Eye, Samurai, Anti Trap Star, Time Warp
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
            if (ability.type() == AbilityType.SAMURAI) {
                AbilityParams p = ability.params();
                applyAntiBuild(now, p.whole("anti-build-seconds"));
                now.setCooldown(Material.ENDER_PEARL, (int) (p.whole("ender-pearl-cooldown-seconds") * 20));
                apply(player, p.effects("effects"));
            }
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

    /** Remembers who hit whom last: Focus Mode, Ninja and the teleports go by it. */
    public void recordHit(Player attacker, Player victim, boolean projectile) {
        Hit hit = new Hit(attacker.getUniqueId(), System.currentTimeMillis());
        lastHit.put(victim.getUniqueId(), hit);
        if (projectile) {
            lastProjectileHit.put(victim.getUniqueId(), hit);
        }
    }

    public void recordPearl(Player player) {
        lastPearl.put(player.getUniqueId(), new Pearl(player.getLocation().clone(), System.currentTimeMillis()));
    }

    // ------------------------------------------------------------------
    // Throws: Switcher, Rage Ball
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
        }
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

    /** Crafting Chaos, Anti Build, Magic Rock: a hit with the item in hand. */
    private void hitWithItem(Player attacker, Player victim, long now) {
        ItemStack held = attacker.getInventory().getItemInMainHand();
        Ability ability = abilityOf(held).filter(a -> a.type().trigger() == AbilityType.Trigger.HIT).orElse(null);
        if (ability == null) {
            return;
        }
        AbilityParams p = ability.params();
        if (ability.type() == AbilityType.MAGIC_ROCK) {
            if (!mayUse(attacker, ability, now)) {
                return;
            }
            int space = freeBlocksAbove(victim);
            List<AbilityEffect> effects = AbilityRules.magicRock(p.effectTable("effects-by-space"), space);
            if (effects.isEmpty()) {
                lang.send(attacker, AbilityMessages.MAGIC_ROCK_NOTHING, "ability", display(ability),
                        "space", String.valueOf(space), "player", victim.getName());
                return;
            }
            apply(attacker, effects);
            lang.send(attacker, AbilityMessages.MAGIC_ROCK, "ability", display(ability),
                    "space", String.valueOf(space), "player", victim.getName());
            started(attacker, ability, now);
            consume(ability, held);
            return;
        }
        // Crafting Chaos and Anti Build need several hits; the gate is asked on the first.
        long required = Math.max(1, p.whole("hits-required"));
        int count = hits.hit(attacker.getUniqueId(), ability.id(), victim.getUniqueId(), now, HIT_WINDOW_MILLIS);
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
        long seconds = p.whole("seconds");
        if (ability.type() == AbilityType.CRAFTING_CHAOS) {
            chaos.put(victim.getUniqueId(), new Chaos(attacker.getUniqueId(), now + seconds * 1000L, p.decimal("chance")));
            lang.send(attacker, AbilityMessages.CHAOS_APPLIED, "player", victim.getName(), "seconds", String.valueOf(seconds));
            lang.send(victim, AbilityMessages.CHAOS_RECEIVED, "player", attacker.getName());
        } else if (ability.type() == AbilityType.ANTI_BUILD) {
            applyAntiBuild(victim, seconds);
            apply(attacker, p.effects("user-effects"));
            lang.send(attacker, AbilityMessages.ANTI_BUILD_APPLIED, "player", victim.getName(),
                    "seconds", String.valueOf(seconds));
        }
        started(attacker, ability, now);
        consume(ability, held);
    }

    /** Free blocks straight above a player's head, up to ten. */
    private static int freeBlocksAbove(Player player) {
        Block head = player.getEyeLocation().getBlock();
        int free = 0;
        for (int i = 1; i <= 10; i++) {
            if (head.getRelative(0, i, 0).isPassable()) {
                free++;
            } else {
                break;
            }
        }
        return free;
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
    public boolean shoot(Player player, Ability ability, Projectile arrow) {
        long now = System.currentTimeMillis();
        if (!mayUse(player, ability, now)) {
            return false;
        }
        arrow.getPersistentDataContainer().set(projectileKey, PersistentDataType.STRING, ability.id());
        started(player, ability, now);
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
        forget(player);
        cooldowns.forget(player.getUniqueId());
        lastHit.remove(player.getUniqueId());
        lastProjectileHit.remove(player.getUniqueId());
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
