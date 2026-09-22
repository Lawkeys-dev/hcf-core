package com.lawkeys.hcfcore.pvp;

import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.database.dao.JdbcDeathbanStore;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.startup.StartupBarrier;
import com.lawkeys.hcfcore.startup.StartupGate;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.util.Durations;
import com.lawkeys.hcfcore.pvp.command.PvpCommand;
import com.lawkeys.hcfcore.pvp.listener.AttackSpeedListener;
import com.lawkeys.hcfcore.pvp.listener.CombatListener;
import com.lawkeys.hcfcore.pvp.listener.DeathbanListener;
import com.lawkeys.hcfcore.pvp.listener.LootProtectionListener;
import com.lawkeys.hcfcore.pvp.listener.ItemCooldownListener;
import com.lawkeys.hcfcore.pvp.listener.PearlListener;
import com.lawkeys.hcfcore.util.Cooldowns;
import com.lawkeys.hcfcore.pvp.legacy.CombatMode;
import com.lawkeys.hcfcore.pvp.legacy.LegacyCombatListener;
import com.lawkeys.hcfcore.pvp.legacy.LegacyCombatLoader;
import com.lawkeys.hcfcore.pvp.legacy.LegacyCombatSettings;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Wires the PvP module into the server.
 *
 * <p>Enabled after {@code claim/}, which it consults for safe zones. Note the
 * difference from the {@code claim} to {@code dtr} relationship: there, an
 * interface had to be declared because the answering module did not exist yet.
 * Here {@code claim/} already exists, so a direct call is simpler and honest -
 * seams invert an ordering problem, they are not a rule to apply blindly
 * (ARCHITECTURE.md section 14).
 */
public final class PvpModule {

    private final Plugin plugin;
    private final ClaimModule claims;
    private final LangManager lang;
    private final StartupGate startup;
    /** Identifies this module's attack-speed modifier, so it is replaced rather than stacked. */
    private final NamespacedKey attackSpeedKey;

    private volatile PvpSettings settings = PvpSettings.defaults();
    private volatile CombatProtection protection = CombatProtection.NONE;
    private volatile DeathbanPolicy deathbanPolicy = DeathbanPolicy.USUAL;
    private volatile DeathbanWaiver deathbanWaiver = DeathbanWaiver.NONE;
    private volatile AllyCombatZone allyCombatZone = AllyCombatZone.NOWHERE;
    /** Which combat the server plays: config.yml, {@code combat}. */
    private volatile CombatMode combatMode = CombatMode.MODERN;
    /** The 1.7.10 combat's settings: pvp.yml, {@code legacy-combat}. Used in {@link CombatMode#CLASSIC} only. */
    private volatile LegacyCombatSettings legacy = LegacyCombatSettings.defaults();
    private LegacyCombatListener legacyListener;
    private BukkitTask legacyTask;

    /** The HCF ender pearl cooldown, memory only: key {@code "pearl"}. */
    private final Cooldowns pearls = new Cooldowns();
    /** Partner items: neither the pearl cooldown nor the item cooldowns count them - a Fake Pearl, a Golden Head. */
    private volatile Predicate<ItemStack> partnerItems = item -> false;

    private DeathbanManager deathbans;
    private CombatTagManager combatTags;
    private BukkitTask saveTask;
    private BukkitTask tagExpiryTask;

    /** @param claims may be {@code null} if the claim module is not running */
    public PvpModule(Plugin plugin, ClaimModule claims, LangManager lang, StartupGate startup) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.claims = claims;
        this.lang = Objects.requireNonNull(lang, "lang");
        this.startup = Objects.requireNonNull(startup, "startup");
        this.attackSpeedKey = new NamespacedKey(plugin, "attack_speed");
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public StartupGate getStartup() {
        return startup;
    }

    public ClaimModule getClaims() {
        return claims;
    }

    public LangManager getLang() {
        return lang;
    }

    /** @return the id of this player's team, or {@code null} for none (or no claim module) */
    public UUID teamOf(UUID playerId) {
        return claims == null ? null
                : claims.getTeams().getManager().getTeamOf(playerId).map(team -> team.getId()).orElse(null);
    }

    public PvpSettings getSettings() {
        return settings;
    }

    public DeathbanManager getDeathbans() {
        return deathbans;
    }

    public CombatTagManager getCombatTags() {
        return combatTags;
    }

    public Cooldowns getPearlCooldowns() {
        return pearls;
    }

    /** @return whole seconds before this player may throw an ender pearl again, or {@code 0} */
    public long pearlSecondsLeft(UUID playerId) {
        return pearls.remaining(playerId, PearlListener.KEY, System.currentTimeMillis());
    }

    /** Ends a player's pearl cooldown at once. */
    public void resetPearl(UUID playerId) {
        pearls.forget(playerId);
    }

    public Predicate<ItemStack> getPartnerItems() {
        return partnerItems;
    }

    /**
     * Installs what a partner item is. Called by the {@code ability/} module at
     * startup: a Fake Pearl or a Golden Head has its own cooldown, and starts neither
     * the pearl cooldown nor an item cooldown.
     */
    public void setPartnerItems(Predicate<ItemStack> partnerItems) {
        this.partnerItems = Objects.requireNonNull(partnerItems, "partnerItems");
    }

    /** @return the item cooldown this item has - none for a partner item, or with them off */
    public Optional<PvpSettings.ItemCooldown> itemCooldownOf(ItemStack item) {
        PvpSettings.ItemCooldownRules rules = settings.itemCooldowns();
        if (!rules.enabled() || item == null || item.isEmpty() || partnerItems.test(item)) {
            return Optional.empty();
        }
        return rules.of(item.getType().name());
    }

    /** An item was used - eaten, by the game or by the classic combat: its cooldown starts, if it has one. */
    public void itemUsed(Player player, ItemStack item) {
        itemCooldownOf(item).ifPresent(cooldown -> startItemCooldown(player, cooldown));
    }

    /** Where a player's item cooldown ends, in their data: it survives logouts and restarts. */
    private NamespacedKey itemCooldownKey(String id) {
        return new NamespacedKey(plugin, "item_cooldown_" + id.replace('-', '_'));
    }

    /** @return whole seconds before this player may use this item again, or {@code 0} */
    public long itemCooldownLeft(Player player, PvpSettings.ItemCooldown item) {
        Long until = player.getPersistentDataContainer().get(itemCooldownKey(item.id()), PersistentDataType.LONG);
        return until == null ? 0L : Durations.secondsLeft(until - System.currentTimeMillis());
    }

    /** Starts this item's cooldown for this player, shown on the item too with {@code show-on-item}. */
    public void startItemCooldown(Player player, PvpSettings.ItemCooldown item) {
        if (item.seconds() <= 0) {
            return;
        }
        player.getPersistentDataContainer().set(itemCooldownKey(item.id()), PersistentDataType.LONG,
                System.currentTimeMillis() + item.seconds() * 1000L);
        showItemCooldown(player, item);
    }

    /** The game's own greyed-out item for what is left of this cooldown: after the item's use, and after a login. */
    public void showItemCooldown(Player player, PvpSettings.ItemCooldown item) {
        Material material = Material.getMaterial(item.material());
        if (!item.showOnItem() || material == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            long left = itemCooldownLeft(player, item);
            if (player.isOnline() && left > 0) {
                player.setCooldown(material, (int) Math.min(Integer.MAX_VALUE, left * 20L));
            }
        }, 1L);
    }

    /** Ends a player's item cooldown at once. */
    public void clearItemCooldown(Player player, PvpSettings.ItemCooldown item) {
        player.getPersistentDataContainer().remove(itemCooldownKey(item.id()));
        Material material = Material.getMaterial(item.material());
        if (material != null) {
            player.setCooldown(material, 0);
        }
    }

    public CombatProtection getProtection() {
        return protection;
    }

    /**
     * Installs the combat protection. Called by the {@code phase/} module at
     * startup (no PvP during SOTW); until then only safe zones refuse a hit.
     */
    public void setProtection(CombatProtection protection) {
        this.protection = Objects.requireNonNull(protection, "protection");
    }

    public DeathbanWaiver getDeathbanWaiver() {
        return deathbanWaiver;
    }

    /**
     * Installs the deathban waiver. Called by the {@code lives/} module at startup (a
     * life spent to come back); until then every banned player is turned away.
     */
    public void setDeathbanWaiver(DeathbanWaiver waiver) {
        this.deathbanWaiver = Objects.requireNonNull(waiver, "waiver");
    }

    /**
     * Installs the deathban policy. Called by the {@code phase/} module at startup
     * (none during SOTW, until the map ends during EOTW); until then every death
     * is banned by the tiers in {@code pvp.yml}.
     */
    public void setDeathbanPolicy(DeathbanPolicy policy) {
        this.deathbanPolicy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * Installs where allies may fight. Called by the {@code events/} module at
     * startup; until then allies hurt each other nowhere.
     */
    public void setAllyCombatZone(AllyCombatZone zone) {
        this.allyCombatZone = Objects.requireNonNull(zone, "zone");
    }

    /**
     * Judges a hit between two players against {@code friendly-fire}: teammates, and
     * allies outside an event area, are refused. Without the claim module there are
     * no teams to ask about, and every hit is allowed.
     */
    public FriendlyFire judgeFriendlyFire(Player attacker, Player victim) {
        if (claims == null) {
            return FriendlyFire.ALLOW;
        }
        var teams = claims.getTeams().getManager();
        UUID attackerTeam = teams.getTeamOf(attacker.getUniqueId()).map(Team::getId).orElse(null);
        UUID victimTeam = teams.getTeamOf(victim.getUniqueId()).map(Team::getId).orElse(null);
        boolean allied = attackerTeam != null && victimTeam != null
                && teams.getTeam(attackerTeam).map(team -> team.isAlliedWith(victimTeam)).orElse(false);
        return FriendlyFire.judge(attackerTeam, victimTeam, allied,
                () -> covers(attacker) || covers(victim), settings.friendlyFire());
    }

    /**
     * Whether this attacker may harm this victim at all: the rules a hit is refused
     * by, which a harmful potion, a rod's pull, a blast's push and a class's debuff
     * follow as well (the project owner's decision, 15/09/2026).
     *
     * <p>The moment's protection (SOTW) first, and before this module's own switch:
     * it is the map's rule, not one of this module's combat tweaks, and phases.yml
     * promises no PvP during SOTW whatever pvp.yml says. Then the ground (safe
     * zones), then the same side: teammates never, allies only in an event area
     * (pvp.yml, friendly-fire).
     *
     * @return why not - the message for the attacker - or empty when the harm may land
     */
    public Optional<Refusal> judgeHarm(Player attacker, Player victim) {
        Optional<String> protectedBy = protection.refusal(attacker.getUniqueId(), victim.getUniqueId());
        if (protectedBy.isPresent()) {
            return Optional.of(new Refusal(protectedBy.get()));
        }
        if (!settings.enabled()) {
            return Optional.empty();
        }
        if (settings.safeZones().enabled() && (isInSafeZone(victim) || isInSafeZone(attacker))) {
            return Optional.of(new Refusal(PvpMessages.SAFE_ZONE_ATTACKER));
        }
        FriendlyFire sameSide = judgeFriendlyFire(attacker, victim);
        if (sameSide != FriendlyFire.ALLOW) {
            return Optional.of(new Refusal(sameSide == FriendlyFire.TEAMMATE
                    ? PvpMessages.FRIENDLY_FIRE_TEAMMATE : PvpMessages.FRIENDLY_FIRE_ALLY,
                    "player", victim.getName()));
        }
        return Optional.empty();
    }

    /** Why a player may not harm another: the message to send them, with its placeholders. */
    public record Refusal(String key, String... placeholders) {
        public void tell(LangManager lang, Player attacker) {
            lang.send(attacker, key, placeholders);
        }
    }

    /** @return which combat the server plays */
    public CombatMode getCombatMode() {
        return combatMode;
    }

    /**
     * @return the 1.7.10 combat's settings while the server plays classic combat and
     *         this module is on; empty in modern combat
     */
    public Optional<LegacyCombatSettings> classicCombat() {
        return combatMode == CombatMode.CLASSIC && settings.enabled() ? Optional.of(legacy) : Optional.empty();
    }

    /**
     * Tags both sides of a hit that landed, telling each only when the tag is new -
     * a blow, or a classic fishing rod's hook.
     */
    public void tagForHit(Player attacker, Player victim) {
        if (!settings.enabled() || combatTags == null) {
            return;
        }
        if (combatTags.tag(victim.getUniqueId())) {
            lang.send(victim, PvpMessages.TAGGED, "seconds", String.valueOf(settings.combatTag().durationSeconds()));
        }
        if (settings.combatTag().tagAttacker() && combatTags.tag(attacker.getUniqueId())) {
            lang.send(attacker, PvpMessages.TAGGED, "seconds", String.valueOf(settings.combatTag().durationSeconds()));
        }
    }

    /**
     * @return whether this player stands on server land its team marks as safe -
     *         spawn, typically. Server land marked as a combat zone (warzone, roads,
     *         event grounds) is fought on like anywhere else. Reusing the claim
     *         module's system teams means an operator defines both the same way they
     *         define any territory.
     */
    public boolean isInSafeZone(Player player) {
        if (claims == null || claims.getManager() == null) {
            return false;
        }
        Optional<Team> owner = claims.ownerAt(player.getLocation());
        return owner.isPresent() && owner.get().isSafeZone();
    }

    private boolean covers(Player player) {
        var at = player.getLocation();
        return allyCombatZone.covers(player.getUniqueId(), at.getWorld().getName(), at.getX(), at.getY(), at.getZ());
    }

    public void enable(DataSource dataSource, long saveIntervalSeconds) {
        reloadSettings();

        DeathbanStore store = dataSource == null
                ? DeathbanStore.NO_OP
                : new JdbcDeathbanStore(dataSource, message -> plugin.getLogger().info(message));

        this.deathbans = new DeathbanManager(() -> settings, store);
        this.combatTags = new CombatTagManager(() -> settings);

        StartupBarrier.Load load = startup.expect("deathbans");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                deathbans.loadAll();
                plugin.getLogger().info("Loaded " + deathbans.getActiveBanCount() + " active deathbans.");
                load.succeeded();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load deathbans.", e);
                load.failed();
            }
        });

        if (saveIntervalSeconds > 0) {
            long ticks = saveIntervalSeconds * 20L;
            this.saveTask = Bukkit.getScheduler()
                    .runTaskTimerAsynchronously(plugin, this::flushQuietly, ticks, ticks);
        }

        // Combat tags expire on their own; this only tells players they are clear.
        this.tagExpiryTask = Bukkit.getScheduler()
                .runTaskTimer(plugin, this::announceExpiredTags, 20L, 20L);

        // This is what closes the TODO the claim module left: /team hq now refuses
        // to teleport a player who is in combat, without claim/ knowing what
        // combat is (ARCHITECTURE.md section 14).
        if (claims != null) {
            claims.setTeleportGuard(playerId -> {
                Player player = Bukkit.getPlayer(playerId);
                return player != null && (blockTeleportIfTagged(player) || blockTeleportForPearl(player));
            });
        }

        plugin.getServer().getPluginManager().registerEvents(new CombatListener(this), plugin);
        plugin.getServer().getPluginManager()
                .registerEvents(new com.lawkeys.hcfcore.pvp.listener.SafeZoneListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new DeathbanListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new AttackSpeedListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new LootProtectionListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new PearlListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new ItemCooldownListener(this), plugin);
        this.legacyListener = new LegacyCombatListener(this);
        plugin.getServer().getPluginManager().registerEvents(legacyListener, plugin);
        // Twice a second: 1.7 regeneration, and the swords in hand kept able to block
        // (or not) whatever put them there - a pickup, a kit, a chest.
        this.legacyTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            legacyListener.regenerate();
            Bukkit.getOnlinePlayers().forEach(legacyListener::syncHands);
        }, 10L, 10L);

        registerCommand();
    }

    private void registerCommand() {
        PluginCommand command = plugin.getServer().getPluginCommand("pvp");
        if (command == null) {
            plugin.getLogger().severe("The 'pvp' command is missing from plugin.yml; "
                    + "players will have no way to check their combat tag.");
            return;
        }
        PvpCommand executor = new PvpCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    public void reloadSettings() {
        var section = ConfigManager.loadFile(plugin, "pvp.yml");
        this.settings = PvpSettingsLoader.load(section,
                warning -> plugin.getLogger().warning("pvp.yml: " + warning));
        this.legacy = LegacyCombatLoader.load(section == null ? null : section.getConfigurationSection("legacy-combat"),
                warning -> plugin.getLogger().warning("pvp.yml: " + warning));
        CombatMode previous = combatMode;
        var config = ConfigManager.loadFile(plugin, "config.yml");
        String word = config == null ? "modern" : config.getString("combat", "modern");
        this.combatMode = CombatMode.parse(word).orElseGet(() -> {
            plugin.getLogger().warning("config.yml: combat '" + word + "' is neither modern nor classic; using modern.");
            return CombatMode.MODERN;
        });
        if (combatMode != previous || legacyListener != null) {
            plugin.getLogger().info("Combat: " + combatMode.name().toLowerCase(java.util.Locale.ROOT) + ".");
        }
        if (previous == CombatMode.CLASSIC && combatMode == CombatMode.MODERN) {
            // Every item a player carries loses what classic combat gave it: blocking, weapon damage.
            Bukkit.getOnlinePlayers().forEach(LegacyCombatListener::stripItems);
        }
        if (legacyListener != null) {
            Bukkit.getOnlinePlayers().forEach(legacyListener::syncHands);
        }
        // Everyone online, so /hcf reload turns attack speed on, off or to a new
        // value without anybody having to rejoin.
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyAttackSpeed(player);
        }
    }

    /**
     * Gives a player the configured attack speed, or takes it away.
     *
     * <p>A <em>transient</em> modifier - the Paper 26.2 javadoc: "Transient modifiers
     * are not persisted (saved with the NBT data)". So nothing is left in anybody's
     * player file: turning the setting off, or removing the plugin, restores vanilla
     * at the next join without a cleanup pass. The flip side is that it has to be
     * given again whenever the player entity is rebuilt, hence join and respawn.
     */
    public void applyAttackSpeed(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(attackSpeedKey);
        PvpSettings current = settings;
        Optional<LegacyCombatSettings> classic = classicCombat();
        double amount;
        if (classic.isPresent() && classic.get().attackCooldown().remove()) {
            // 1.7 had no attack cooldown: an attack speed high enough that every hit is a full one.
            amount = classic.get().attackCooldown().attackSpeed() - attribute.getBaseValue();
        } else {
            amount = current.enabled()
                    ? CombatMath.attackSpeedModifier(attribute.getBaseValue(), current.attackSpeed())
                    : 0.0;
        }
        if (amount != 0.0) {
            attribute.addTransientModifier(
                    new AttributeModifier(attackSpeedKey, amount, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    public void disable() {
        if (legacyTask != null) {
            legacyTask.cancel();
            legacyTask = null;
        }
        // Transient, so a restart clears it anyway; this is for a plugin disabled
        // while the server keeps running. The items lose what classic
        // combat gave them, so a plugin removed leaves no sword that blocks and no
        // weapon at its 1.7 damage - in the inventories of those online.
        for (Player player : Bukkit.getOnlinePlayers()) {
            LegacyCombatListener.stripItems(player);
            LegacyCombatListener.restoreRegeneration(player);
            AttributeInstance attribute = player.getAttribute(Attribute.ATTACK_SPEED);
            if (attribute != null) {
                attribute.removeModifier(attackSpeedKey);
            }
        }
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (tagExpiryTask != null) {
            tagExpiryTask.cancel();
            tagExpiryTask = null;
        }
        if (combatTags != null) {
            // Tags are memory-only: dropping them on shutdown is the point, so
            // nobody comes back tagged for a fight that ended with the server.
            combatTags.clearAll();
        }
        if (deathbans == null) {
            return;
        }
        try {
            int written = deathbans.flush();
            if (written > 0) {
                plugin.getLogger().info("Saved " + written + " deathban changes on shutdown.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save deathbans on shutdown", e);
        }
    }

    /**
     * Writes deathban changes now rather than at the next periodic save - for a ban
     * lifted by a life, which a crash must not bring back after the life was spent.
     */
    public void flushSoon() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::flushQuietly);
    }

    private void flushQuietly() {
        try {
            deathbans.flush();
            deathbans.purgeExpired();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Periodic deathban save failed; the affected bans stay queued.", e);
        }
    }

    /** Tells players whose combat tag just ran out that they are clear. */
    private void announceExpiredTags() {
        for (UUID playerId : combatTags.pollExpired()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                lang.send(player, PvpMessages.TAG_EXPIRED);
            }
        }
    }

    /** Lets staff die without being locked out, so they can moderate the raid they died in. */
    public static final String BYPASS_PERMISSION = "hcfcore.deathban.bypass";

    /**
     * Bans a player after their death, for the duration their permissions earn
     * them.
     *
     * <p><strong>The bypass is answered here, at the death, and not at the login.</strong>
     * A death has a real {@link Player}, so {@code hasPermission} works; a login does
     * not, since {@code PlayerConnectionValidateLoginEvent} carries a profile and no
     * {@code Player}, and Bukkit has no offline permission lookup. Deciding here means
     * staff are never banned in the first place, rather than banned and then let back
     * in - so the login check needs no permission at all, and
     * {@link com.lawkeys.hcfcore.pvp.listener.DeathbanListener} could leave the
     * deprecated {@code PlayerLoginEvent} behind.
     *
     * <p>The one case this loses: somebody granted the permission <em>while</em>
     * already banned stays out, because the ban was recorded before they had it.
     * {@code /pvp lift <player>} clears it, and works from the console.
     *
     * <p>What it gains: {@code /pvp ban} writes to the store directly rather than
     * through here, so a staff-issued ban still lands on a bypass holder - and since
     * the login no longer reads any permission, that ban is now actually enforced.
     * It was not before: the holder simply logged back in.
     */
    public void applyDeathban(Player player) {
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return;
        }
        switch (deathbanPolicy.rule()) {
            case NONE -> {
            }
            case UNTIL_MAP_END -> deathbans.applyUntilMapEnd(player.getUniqueId(), "eotw").ifPresent(ban ->
                    kickDeathbanned(player.getUniqueId(), true));
            case USUAL -> {
                long seconds = settings.deathbanSecondsFor(heldTierPermissions(player));
                deathbans.apply(player.getUniqueId(), seconds, "death").ifPresent(ban ->
                        kickDeathbanned(player.getUniqueId(), true));
            }
        }
    }

    /**
     * Sends a deathbanned player off the server, a tick from now, with the ban as the
     * reason on their screen.
     *
     * <p>Writing the ban was never enough: the login check only stops the
     * <em>next</em> connection, so a player banned at their death respawned and played
     * on until they chose to leave (found in game, 13/09/2026). A tick later rather
     * than inside the death event, where other listeners and the server itself are
     * still finishing that death. The ban is read again then, so one lifted or bought
     * back in between kicks nobody; a player already gone - the combat logger killed
     * as they left - is simply not there.
     *
     * @param died whether the ban comes from a death just now ("You died...") rather
     *             than from staff ("You are deathbanned for another...")
     */
    public void kickDeathbanned(UUID playerId, boolean died) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            Optional<Deathban> ban = deathbans.getActiveBan(playerId);
            if (player == null || ban.isEmpty()) {
                return;
            }
            String reason = ban.get().isUntilMapEnd()
                    ? lang.get(died ? PvpMessages.DEATHBAN_MAP_END_APPLIED : PvpMessages.DEATHBAN_MAP_END_LOGIN_DENIED)
                    : lang.get(died ? PvpMessages.DEATHBAN_APPLIED : PvpMessages.DEATHBAN_LOGIN_DENIED,
                            "time", formatDuration(ban.get().remainingSeconds(System.currentTimeMillis())));
            // LangManager already produces the section-sign form: a straight parse.
            player.kick(com.lawkeys.hcfcore.util.LegacyText.SERIALIZER.deserialize(reason));
        });
    }

    /**
     * @return the deathban tier nodes this player actually holds
     *
     * <p>Only the configured tier nodes are considered, rather than scanning every
     * permission the player has, so an unrelated wildcard grant cannot silently
     * hand somebody a shorter ban.
     */
    private List<String> heldTierPermissions(Player player) {
        List<String> held = new ArrayList<>();
        for (String node : settings.deathban().permissionTiers().keySet()) {
            if (player.hasPermission(node)) {
                held.add(node);
            }
        }
        return held;
    }

    /**
     * @return {@code true} if the player may not teleport right now, having been
     *         told why
     */
    public boolean blockTeleportIfTagged(Player player) {
        if (!settings.enabled() || !settings.combatTag().blockTeleport()) {
            return false;
        }
        if (!combatTags.isTagged(player.getUniqueId())) {
            return false;
        }
        lang.send(player, PvpMessages.TAG_BLOCKS_TELEPORT,
                "time", formatDuration(combatTags.getRemainingSeconds(player.getUniqueId())));
        return true;
    }

    /**
     * The pearl cooldown's part of the teleport guard: no {@code /spawn}, {@code /team hq}
     * or the like until it is over, with {@code ender-pearl-cooldown.block-teleport} -
     * a pearl out of a fight is not followed by a command out of it.
     *
     * @return whether it refuses, the player having been told
     */
    public boolean blockTeleportForPearl(Player player) {
        PvpSettings.EnderPearlRules rules = settings.enderPearl();
        if (!rules.enabled() || !rules.blockTeleport()) {
            return false;
        }
        long left = pearlSecondsLeft(player.getUniqueId());
        if (left <= 0) {
            return false;
        }
        lang.send(player, PvpMessages.PEARL_BLOCKS_TELEPORT, "time", formatDuration(left));
        return true;
    }

    /**
     * The answer to {@code general/}'s logout guard: a tagged player's safe logout
     * would be a combat log, which {@code DeathbanListener#onQuit} punishes by death -
     * so it is refused, on the same condition that punishment uses.
     *
     * @return {@code true} if the player may not log out through {@code /logout} now,
     *         having been told why
     */
    public boolean refuseLogout(Player player) {
        if (!settings.combatTag().killOnLogout() || !combatTags.isTagged(player.getUniqueId())) {
            return false;
        }
        lang.send(player, PvpMessages.TAG_BLOCKS_LOGOUT,
                "time", formatDuration(combatTags.getRemainingSeconds(player.getUniqueId())));
        return true;
    }

    /** @return a compact {@code 1h 5m 30s} form, skipping the leading units that are zero */
    public String formatDuration(long totalSeconds) {
        return Durations.formatWithSeconds(totalSeconds);
    }

}
