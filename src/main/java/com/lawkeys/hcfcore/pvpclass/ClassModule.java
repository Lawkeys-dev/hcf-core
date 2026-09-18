package com.lawkeys.hcfcore.pvpclass;

import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.pvp.PvpModule;
import com.lawkeys.hcfcore.pvpclass.command.ClassCommand;
import com.lawkeys.hcfcore.pvpclass.command.DyesCommand;
import com.lawkeys.hcfcore.pvpclass.listener.ClassListener;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.util.Cooldowns;
import com.lawkeys.hcfcore.util.EffectCaps;
import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Classes: Diamond, Bard, Archer, Rogue, Miner - and any class an operator writes in
 * {@code classes.yml}. A player is in a class while wearing its whole armour set,
 * once the warmup is over (FEATURES.md section 18).
 *
 * <p>The rules that need no server - what a valid class is, the warmup, the energy,
 * the team limit, the archer tag, whether a hit comes from behind - are in
 * {@link ClassConfig}, {@link ClassManager}, {@link ArcherTags} and
 * {@link Backstab}, pure and tested. This class is the server layer: it looks at
 * what players wear, gives and takes back effects, and finds who an effect reaches.
 *
 * <p>Started after {@code team/} and {@code pvp/}, which it reads directly - both
 * exist by then, so no seam is needed (ARCHITECTURE.md section 14). An effect handed
 * to an enemy goes through {@link PvpModule#judgeHarm}: a Bard cannot wither
 * somebody they could not hit.
 */
public final class ClassModule {

    /** How often armour is looked at, in ticks: often enough that a warmup reads true to the second. */
    private static final long TICK_PERIOD = 10L;
    /** Passive effects last this long and are renewed well before they run out. */
    private static final int PASSIVE_TICKS = 400;
    private static final int REFRESH_BELOW_TICKS = 300;

    private final Plugin plugin;
    private final LangManager lang;
    private final TeamModule teams;
    private final PvpModule pvp;
    private final NamespacedKey anyPluginKey;

    private volatile ClassSettings settings = ClassSettings.defaults();
    private final ClassManager manager = new ClassManager(() -> settings);
    private final ArcherTags archerTags = new ArcherTags();
    private final Cooldowns cooldowns = new Cooldowns();
    /** Main thread only: the passive effects given to each player, and at which amplifier. */
    private final Map<UUID, Map<PotionEffectType, Integer>> passiveGiven = new HashMap<>();
    private volatile EffectCaps effectCaps = EffectCaps.NONE;
    private volatile Map<String, PotionEffectType> effectTypes = Map.of();
    private BukkitTask task;
    /** Renews held effects; its interval is {@code held-effect-interval-ticks}. */
    private BukkitTask heldTask;
    private int heldInterval;

    /**
     * @param teams may be {@code null}: every "team" is then the player alone
     * @param pvp   may be {@code null}: nothing then protects a player from a debuff
     */
    public ClassModule(Plugin plugin, LangManager lang, TeamModule teams, PvpModule pvp) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lang = Objects.requireNonNull(lang, "lang");
        this.teams = teams;
        this.pvp = pvp;
        this.anyPluginKey = new NamespacedKey(plugin, "any");
    }

    /** The effect caps of {@code limiters.yml}, asked before giving an effect; filled after startup. */
    public void setEffectCaps(EffectCaps effectCaps) {
        this.effectCaps = Objects.requireNonNull(effectCaps, "effectCaps");
    }

    public LangManager getLang() {
        return lang;
    }

    public ClassSettings getSettings() {
        return settings;
    }

    public ClassManager getManager() {
        return manager;
    }

    public ArcherTags getArcherTags() {
        return archerTags;
    }

    public Cooldowns getCooldowns() {
        return cooldowns;
    }

    public void enable() {
        reloadSettings();
        plugin.getServer().getPluginManager().registerEvents(new ClassListener(this), plugin);
        PluginCommand command = plugin.getServer().getPluginCommand("class");
        if (command == null) {
            plugin.getLogger().severe("The 'class' command is missing from plugin.yml.");
        } else {
            ClassCommand executor = new ClassCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        PluginCommand dyes = plugin.getServer().getPluginCommand("dyes");
        if (dyes == null) {
            plugin.getLogger().severe("The 'dyes' command is missing from plugin.yml.");
        } else {
            DyesCommand executor = new DyesCommand(this);
            dyes.setExecutor(executor);
            dyes.setTabCompleter(executor);
        }
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
        scheduleHeld();
    }

    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (heldTask != null) {
            heldTask.cancel();
            heldTask = null;
        }
        // What this module gave is taken back, so a plugin disabled while the server
        // runs leaves nobody with a class's Speed III.
        for (Player player : Bukkit.getOnlinePlayers()) {
            takeBackPassive(player);
        }
        passiveGiven.clear();
        manager.clear();
        archerTags.clear();
        cooldowns.clearAll();
    }

    // ------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------

    public void reloadSettings() {
        ConfigurationSection section = ConfigManager.loadFile(plugin, "classes.yml");
        if (section == null) {
            plugin.getLogger().warning("classes.yml is missing or empty - no classes.");
            this.settings = ClassSettings.defaults();
            this.effectTypes = Map.of();
            return;
        }
        ClassConfig config = new ClassConfig(ClassModule::isItem, key -> lookUpEffect(key) != null,
                DYE_PALETTE::containsKey, message -> plugin.getLogger().warning("classes.yml: " + message));
        ClassSettings loaded = config.parse(toMap(section));
        Map<String, PotionEffectType> types = new HashMap<>();
        for (PvpClass pvpClass : loaded.classes()) {
            pvpClass.passiveEffects().keySet().forEach(key -> types.put(key, lookUpEffect(key)));
            pvpClass.heldEffects().values().forEach(held -> types.put(held.effect().effect(),
                    lookUpEffect(held.effect().effect())));
            pvpClass.clickEffects().values().forEach(click -> types.put(click.effect().effect(),
                    lookUpEffect(click.effect().effect())));
            pvpClass.dyeEffects().values().forEach(dye -> types.put(dye.effect().effect(),
                    lookUpEffect(dye.effect().effect())));
        }
        types.values().removeIf(Objects::isNull);
        this.effectTypes = Map.copyOf(types);
        this.settings = loaded;
        if (heldTask != null) {
            // A new interval by /hcf reload: the task starts again at it.
            scheduleHeld();
        }
    }

    private static boolean isItem(String name) {
        Material material = Material.matchMaterial(name);
        return material != null && material.isItem() && !material.isAir();
    }

    private static PotionEffectType lookUpEffect(String key) {
        try {
            return Registry.MOB_EFFECT.get(NamespacedKey.minecraft(key));
        } catch (IllegalArgumentException invalidKey) {
            return null;
        }
    }

    /** The YAML tree as plain maps and lists, which is what {@link ClassConfig} reads. */
    private static Map<String, Object> toMap(ConfigurationSection section) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            out.put(key, value instanceof ConfigurationSection child ? toMap(child) : value);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Every half-second: armour, passive effects, held items
    // ------------------------------------------------------------------

    private void tick() {
        long now = System.currentTimeMillis();
        ClassSettings current = settings;
        for (Player player : Bukkit.getOnlinePlayers()) {
            PvpClass wearing = current.enabled() ? wornClass(player, current) : null;
            List<ClassManager.Change> changes = manager.update(player.getUniqueId(), wearing, now,
                    pvpClass -> hasRoom(player, pvpClass));
            tell(player, changes, now);
            Optional<PvpClass> active = manager.active(player.getUniqueId());
            if (active.isEmpty()) {
                takeBackPassive(player);
                continue;
            }
            refreshPassive(player, active.get());
        }
    }

    /**
     * Held items, every {@code held-effect-interval-ticks} (a quarter of a second as
     * shipped): a Bard scrolls through several items in a second, and an item passed
     * over must still give its effect - which also comes at once when the hand
     * changes ({@link #pulseHeld(Player, ItemStack)}).
     */
    private void tickHeld() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            pulseHeld(player, player.getInventory().getItemInMainHand());
        }
    }

    /** Starts the held-effect task, or restarts it when its interval changed. */
    private void scheduleHeld() {
        int interval = settings.heldIntervalTicks();
        if (heldTask != null && interval == heldInterval) {
            return;
        }
        if (heldTask != null) {
            heldTask.cancel();
        }
        heldInterval = interval;
        heldTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickHeld, interval, interval);
    }

    /** @return the class whose whole set this player wears, if they may use it */
    private static PvpClass wornClass(Player player, ClassSettings settings) {
        PlayerInventory inventory = player.getInventory();
        List<String> worn = List.of(name(inventory.getHelmet()), name(inventory.getChestplate()),
                name(inventory.getLeggings()), name(inventory.getBoots()));
        Optional<PvpClass> matching = settings.matching(worn);
        if (matching.isEmpty()) {
            return null;
        }
        String permission = matching.get().permission();
        return permission.isEmpty() || player.hasPermission(permission) ? matching.get() : null;
    }

    private static String name(ItemStack item) {
        return item == null ? "" : item.getType().name();
    }

    /** Whether the player's team has room for one more of this class. */
    private boolean hasRoom(Player player, PvpClass pvpClass) {
        if (pvpClass.maxPerTeam() <= 0) {
            return true;
        }
        Optional<Team> team = teamOf(player);
        if (team.isEmpty()) {
            return true;
        }
        return manager.countIn(team.get().getMemberIds(), pvpClass.id()) < pvpClass.maxPerTeam();
    }

    private void tell(Player player, List<ClassManager.Change> changes, long now) {
        boolean activatedAtOnce = changes.stream().anyMatch(change -> change.kind() == ClassManager.Kind.ACTIVATED);
        for (ClassManager.Change change : changes) {
            String name = change.pvpClass().displayName();
            switch (change.kind()) {
                case WARMUP_STARTED -> {
                    if (!activatedAtOnce) {
                        lang.send(player, ClassMessages.WARMUP, "class", name, "time",
                                Durations.formatWithSeconds(manager.warmupRemaining(player.getUniqueId(), now)));
                    }
                }
                case ACTIVATED -> lang.send(player, ClassMessages.ACTIVATED, "class", name);
                case DEACTIVATED -> {
                    takeBackPassive(player);
                    lang.send(player, ClassMessages.DEACTIVATED, "class", name);
                }
                case REFUSED_TEAM_LIMIT -> lang.send(player, ClassMessages.TEAM_LIMIT, "class", name,
                        "max", String.valueOf(change.pvpClass().maxPerTeam()));
            }
        }
    }

    // ------------------------------------------------------------------
    // Effects
    // ------------------------------------------------------------------

    /**
     * Keeps the class's passive effects on the player - and the Miner's invisibility
     * below its height - never over a stronger effect from elsewhere, and takes back
     * those no longer wanted.
     *
     * <p>This module's passive effects carry a signature - not ambient, no particles,
     * no longer than {@link #PASSIVE_TICKS} - so what is taken back is only what it
     * gave: a potion drunk since has particles, and an enchant's effect is ambient.
     */
    private void refreshPassive(Player player, PvpClass pvpClass) {
        Map<PotionEffectType, Integer> wanted = new HashMap<>();
        pvpClass.passiveEffects().forEach((key, level) -> {
            PotionEffectType type = effectTypes.get(key);
            if (type != null) {
                wanted.merge(type, level - 1, Math::max);
            }
        });
        if (pvpClass.invisibleBelowY() != null && player.getLocation().getY() < pvpClass.invisibleBelowY()) {
            wanted.merge(PotionEffectType.INVISIBILITY, 0, Math::max);
        }
        // Within the effect caps before anything: what is given is then what is
        // recognised as ours, and a forbidden effect is simply not wanted.
        wanted.replaceAll((type, amplifier) -> effectCaps.allowed(type, amplifier));
        wanted.values().removeIf(amplifier -> amplifier < 0);
        Map<PotionEffectType, Integer> given = passiveGiven.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>());
        for (Map.Entry<PotionEffectType, Integer> entry : wanted.entrySet()) {
            PotionEffectType type = entry.getKey();
            int amplifier = entry.getValue();
            PotionEffect current = player.getPotionEffect(type);
            Integer previous = given.get(type);
            if (current != null && previous != null && previous != amplifier && isOurs(current, previous)) {
                // The class's level changed (a reload): replace ours rather than keep it.
                player.removePotionEffect(type);
                current = null;
            }
            boolean ours = current != null && isOurs(current, amplifier);
            if (current != null && !ours && current.getAmplifier() >= amplifier) {
                // Somebody else's effect, as strong or stronger: leave it alone.
                given.remove(type);
                continue;
            }
            if (current == null || !ours || current.getDuration() < REFRESH_BELOW_TICKS) {
                player.addPotionEffect(new PotionEffect(type, PASSIVE_TICKS, amplifier, false, false, true));
            }
            given.put(type, amplifier);
        }
        for (Iterator<Map.Entry<PotionEffectType, Integer>> it = given.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<PotionEffectType, Integer> entry = it.next();
            if (!wanted.containsKey(entry.getKey())) {
                removeIfOurs(player, entry.getKey(), entry.getValue());
                it.remove();
            }
        }
    }

    private void takeBackPassive(Player player) {
        Map<PotionEffectType, Integer> given = passiveGiven.remove(player.getUniqueId());
        if (given != null) {
            given.forEach((type, amplifier) -> removeIfOurs(player, type, amplifier));
        }
    }

    private static void removeIfOurs(Player player, PotionEffectType type, int amplifier) {
        PotionEffect current = player.getPotionEffect(type);
        if (current != null && isOurs(current, amplifier)) {
            player.removePotionEffect(type);
        }
    }

    private static boolean isOurs(PotionEffect effect, int amplifier) {
        // Never an infinite one: its duration reads -1, under any limit - and it is
        // an effect command's (effect-commands.yml), which the class must not take.
        return !effect.isAmbient() && !effect.hasParticles() && effect.getAmplifier() == amplifier
                && !effect.isInfinite() && effect.getDuration() <= PASSIVE_TICKS;
    }

    /**
     * A held item's effect, renewed while it stays in hand: on a timer, and at once
     * when the player's hand changes to {@code item}.
     */
    public void pulseHeld(Player player, ItemStack item) {
        Optional<PvpClass> active = manager.active(player.getUniqueId());
        if (active.isEmpty() || active.get().heldEffects().isEmpty()) {
            return;
        }
        HeldEffect held = active.get().heldEffects().get(name(item));
        if (held == null || blockedBySafeZone(player)) {
            return;
        }
        PotionEffectType type = effectTypes.get(held.effect().effect());
        if (type == null) {
            return;
        }
        for (Player target : targets(player, held.target(), held.radius(), held.includeSelf())) {
            pulse(target, type, held.effect());
        }
    }

    /**
     * Applies an effect for its duration. The server keeps the stronger of two
     * effects, and holds a weaker, longer one back until the stronger ends - so a
     * pulse never cuts a potion short.
     */
    private void pulse(Player target, PotionEffectType type, ClassEffect effect) {
        int amplifier = effectCaps.allowed(type, effect.amplifier());
        if (amplifier < 0) {
            return;
        }
        target.addPotionEffect(new PotionEffect(type, effect.seconds() * 20, amplifier, false, true, true));
    }

    /** @return whether a class ability may not be used here: its user stands in a safe zone */
    public boolean blockedBySafeZone(Player player) {
        return !settings.abilitiesInSafeZones() && pvp != null && pvp.isInSafeZone(player);
    }

    /** @return who an effect from {@code source} reaches */
    public List<Player> targets(Player source, ClassTarget target, double radius) {
        return targets(source, target, radius, true);
    }

    /** @param includeSelf whether a team target reaches the source too */
    public List<Player> targets(Player source, ClassTarget target, double radius, boolean includeSelf) {
        if (target == ClassTarget.SELF) {
            return List.of(source);
        }
        Optional<Team> team = teamOf(source);
        double radiusSquared = radius * radius;
        List<Player> out = new ArrayList<>();
        for (Player other : source.getWorld().getPlayers()) {
            if (other.getLocation().distanceSquared(source.getLocation()) > radiusSquared) {
                continue;
            }
            boolean self = other.getUniqueId().equals(source.getUniqueId());
            if (self && !includeSelf) {
                continue;
            }
            Optional<Team> otherTeam = self ? team : teamOf(other);
            boolean teammate = self || (team.isPresent() && otherTeam.isPresent()
                    && team.get().getId().equals(otherTeam.get().getId()));
            boolean ally = !teammate && team.isPresent() && otherTeam.isPresent()
                    && team.get().isAlliedWith(otherTeam.get().getId());
            boolean reached = switch (target) {
                case TEAM -> teammate;
                case TEAM_AND_ALLIES -> teammate || ally;
                case ENEMIES -> !teammate && !ally && isFightable(source, other);
                case SELF -> self;
            };
            if (reached) {
                out.add(other);
            }
        }
        return out;
    }

    /** An enemy is reached only if the source could hit them: a debuff follows the rules of a blow. */
    private boolean isFightable(Player source, Player other) {
        if (other.getGameMode() != GameMode.SURVIVAL && other.getGameMode() != GameMode.ADVENTURE) {
            return false;
        }
        // Vanished staff are hidden from the source: not a target.
        if (!source.canSee(other)) {
            return false;
        }
        return pvp == null || pvp.judgeHarm(source, other).isEmpty();
    }

    private Optional<Team> teamOf(Player player) {
        if (teams == null || teams.getManager() == null) {
            return Optional.empty();
        }
        return teams.getManager().getTeamOf(player.getUniqueId());
    }

    // ------------------------------------------------------------------
    // Right-click effects
    // ------------------------------------------------------------------

    /**
     * Uses the click effect of the item in hand, if the player's class has one.
     *
     * @return whether the item is one of the class's click items - the caller then
     *         cancels the click, used or refused, so the item is never eaten
     */
    public boolean click(Player player, ItemStack item) {
        if (item == null || !settings.enabled() || isPluginItem(item)) {
            return false;
        }
        Optional<PvpClass> active = manager.active(player.getUniqueId());
        if (active.isEmpty()) {
            return false;
        }
        String itemName = item.getType().name();
        ClickEffect click = active.get().clickEffects().get(itemName);
        if (click == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (blockedBySafeZone(player)) {
            lang.send(player, ClassMessages.SAFE_ZONE);
            return true;
        }
        String cooldownKey = "click:" + itemName;
        long wait = cooldowns.remaining(player.getUniqueId(), cooldownKey, now);
        if (wait > 0) {
            lang.send(player, ClassMessages.COOLDOWN, "time", Durations.formatWithSeconds(wait));
            return true;
        }
        PotionEffectType type = effectTypes.get(click.effect().effect());
        if (type == null) {
            return true;
        }
        if (!manager.spend(player.getUniqueId(), click.energyCost(), now)) {
            lang.send(player, ClassMessages.NOT_ENOUGH_ENERGY,
                    "energy", String.valueOf((int) Math.floor(manager.energy(player.getUniqueId(), now))),
                    "cost", String.valueOf(click.energyCost()));
            return true;
        }
        cooldowns.start(player.getUniqueId(), cooldownKey, click.cooldownSeconds(), now);
        if (click.consume()) {
            item.setAmount(item.getAmount() - 1);
        }
        List<Player> reached = targets(player, click.target(), click.radius(), click.includeSelf());
        String effectName = click.effect().displayName();
        for (Player target : reached) {
            pulse(target, type, click.effect());
            if (!target.getUniqueId().equals(player.getUniqueId())) {
                lang.send(target, click.target() == ClassTarget.ENEMIES
                        ? ClassMessages.EFFECT_INFLICTED : ClassMessages.EFFECT_RECEIVED,
                        "player", player.getName(), "effect", effectName);
            }
        }
        if (click.energyCost() > 0) {
            lang.send(player, ClassMessages.EFFECT_USED_ENERGY, "effect", effectName,
                    "count", String.valueOf(reached.size()), "cost", String.valueOf(click.energyCost()),
                    "energy", String.valueOf((int) Math.floor(manager.energy(player.getUniqueId(), now))),
                    "max", formatNumber(active.get().energy().max()));
        } else {
            lang.send(player, ClassMessages.EFFECT_USED, "effect", effectName,
                    "count", String.valueOf(reached.size()));
        }
        return true;
    }

    /**
     * @return whether the item carries this plugin's data - a partner item, a
     *         crowbar, a custom enchant book - which a class leaves to its own module
     */
    private boolean isPluginItem(ItemStack item) {
        if (!item.hasItemMeta()) {
            return false;
        }
        String namespace = anyPluginKey.getNamespace();
        Set<NamespacedKey> keys = item.getItemMeta().getPersistentDataContainer().getKeys();
        return keys.stream().anyMatch(key -> key.getNamespace().equals(namespace));
    }

    // ------------------------------------------------------------------
    // Dyed sets
    // ------------------------------------------------------------------

    /** Every dye the game has, by name, with the colour one dye gives leather. */
    private static final Map<String, Integer> DYE_PALETTE = dyePalette();

    private static Map<String, Integer> dyePalette() {
        Map<String, Integer> palette = new LinkedHashMap<>();
        for (DyeColor dye : DyeColor.values()) {
            palette.put(dye.name(), dye.getColor().asRGB());
        }
        return Map.copyOf(palette);
    }

    /** @return the dye this player's set reads as - all four pieces dyed, all nearest the same dye */
    public Optional<String> dyeColour(Player player) {
        PlayerInventory inventory = player.getInventory();
        List<Integer> colours = new ArrayList<>();
        for (ItemStack piece : new ItemStack[] {inventory.getHelmet(), inventory.getChestplate(),
                inventory.getLeggings(), inventory.getBoots()}) {
            colours.add(piece != null && piece.getItemMeta() instanceof LeatherArmorMeta leather && leather.isDyed()
                    ? leather.getColor().asRGB() : null);
        }
        return DyeColours.ofSet(colours, DYE_PALETTE);
    }

    /**
     * An arrow from a class with dye effects hit somebody: the effect of the colour
     * the shooter's set is dyed, if the roll allows it. The hit has landed, so the
     * rules of combat have already let it through.
     */
    public void onArrowHit(Player shooter, Player victim) {
        Optional<PvpClass> active = manager.active(shooter.getUniqueId());
        if (active.isEmpty() || active.get().dyeEffects().isEmpty()) {
            return;
        }
        Optional<String> colour = dyeColour(shooter);
        DyeEffect dye = colour.map(active.get().dyeEffects()::get).orElse(null);
        if (dye == null || !dye.applies(ThreadLocalRandom.current().nextDouble())) {
            return;
        }
        PotionEffectType type = effectTypes.get(dye.effect().effect());
        if (type == null) {
            return;
        }
        pulse(victim, type, dye.effect());
        String effectName = dye.effect().displayName();
        lang.send(shooter, ClassMessages.DYE_SHOOTER, "player", victim.getName(), "effect", effectName,
                "seconds", String.valueOf(dye.effect().seconds()));
        lang.send(victim, ClassMessages.DYE_VICTIM, "player", shooter.getName(), "effect", effectName,
                "seconds", String.valueOf(dye.effect().seconds()));
    }

    // ------------------------------------------------------------------
    // Players leaving and dying
    // ------------------------------------------------------------------

    /** Logging out drops the class: the next login warms it up again. */
    public void onQuit(Player player) {
        takeBackPassive(player);
        manager.forget(player.getUniqueId());
        archerTags.forget(player.getUniqueId());
        cooldowns.forget(player.getUniqueId());
    }

    /** Death clears effects by itself; the class and the mark go with it. */
    public void onDeath(Player player) {
        passiveGiven.remove(player.getUniqueId());
        manager.forget(player.getUniqueId());
        archerTags.forget(player.getUniqueId());
    }

    // ------------------------------------------------------------------
    // Scoreboard
    // ------------------------------------------------------------------

    /**
     * The rows this module adds to the scoreboard, empty when there is nothing to
     * show: {@code %class%}, {@code %class_line%}, {@code %class_energy_line%} and
     * {@code %archer_tag_line%}.
     */
    public Map<String, String> placeholders(Player player) {
        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();
        Map<String, String> out = new HashMap<>();
        Optional<PvpClass> active = manager.active(id);
        Optional<PvpClass> pending = manager.pending(id);
        out.put("%class%", active.map(PvpClass::displayName).orElse(""));
        String classLine = "";
        if (active.isPresent()) {
            classLine = lang.get(ClassMessages.SCOREBOARD_CLASS, "class", active.get().displayName());
        } else if (pending.isPresent()) {
            classLine = lang.get(ClassMessages.SCOREBOARD_WARMUP, "class", pending.get().displayName(),
                    "time", Durations.formatWithSeconds(manager.warmupRemaining(id, now)));
        }
        out.put("%class_line%", classLine);
        String energyLine = "";
        if (active.isPresent() && active.get().hasEnergy()) {
            energyLine = lang.get(ClassMessages.SCOREBOARD_ENERGY,
                    "energy", String.valueOf((int) Math.floor(manager.energy(id, now))),
                    "max", formatNumber(active.get().energy().max()));
        }
        out.put("%class_energy_line%", energyLine);
        long tagLeft = archerTags.remaining(id, now);
        out.put("%archer_tag_line%", tagLeft <= 0 ? ""
                : lang.get(ClassMessages.SCOREBOARD_ARCHER_TAG, "time", Durations.formatWithSeconds(tagLeft)));
        return out;
    }

    /** @return {@code 100} rather than {@code 100.0}, and {@code 1.5} as it is */
    public static String formatNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
