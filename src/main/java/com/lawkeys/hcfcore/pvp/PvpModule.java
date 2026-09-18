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
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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
                return player != null && blockTeleportIfTagged(player);
            });
        }

        plugin.getServer().getPluginManager().registerEvents(new CombatListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new DeathbanListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new AttackSpeedListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new LootProtectionListener(this), plugin);

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
        double amount = current.enabled()
                ? CombatMath.attackSpeedModifier(attribute.getBaseValue(), current.attackSpeed())
                : 0.0;
        if (amount != 0.0) {
            attribute.addTransientModifier(
                    new AttributeModifier(attackSpeedKey, amount, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    public void disable() {
        // Transient, so a restart clears it anyway; this is for a plugin disabled
        // while the server keeps running.
        for (Player player : Bukkit.getOnlinePlayers()) {
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
            player.kick(LegacyComponentSerializer.legacySection().deserialize(reason));
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
