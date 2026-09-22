package com.lawkeys.hcfcore.elevator;

import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.pvp.PvpModule;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.util.Cooldowns;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Elevator signs: a sign reading {@code [Elevator]} over {@code Up} or {@code Down}
 * takes whoever right-clicks it to the next floor that way, straight up or down its
 * column - the classic HCF way between the floors of a base.
 *
 * <p>A sign is marked as an elevator when it is written, so text alone never makes one,
 * and the way it goes is kept with the mark. Using one is an interaction with a block:
 * the territory protection that refuses it on an enemy's land refuses the ride too,
 * unless that team is raidable.
 */
public final class ElevatorModule {

    private static final String COOLDOWN_KEY = "elevator";

    private final Plugin plugin;
    private final LangManager lang;
    /** May be {@code null}: that module is not running. */
    private final ClaimModule claims;
    private final PvpModule pvp;
    private final NamespacedKey key;
    private final Cooldowns cooldowns = new Cooldowns();

    private volatile ElevatorSettings settings = ElevatorSettings.defaults();

    public ElevatorModule(Plugin plugin, LangManager lang, ClaimModule claims, PvpModule pvp) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lang = Objects.requireNonNull(lang, "lang");
        this.claims = claims;
        this.pvp = pvp;
        this.key = new NamespacedKey(plugin, "elevator");
    }

    public void enable() {
        reloadSettings();
        plugin.getServer().getPluginManager().registerEvents(new ElevatorListener(this), plugin);
    }

    public void reloadSettings() {
        ConfigurationSection section = ConfigManager.loadFile(plugin, "elevators.yml");
        ElevatorSettings d = ElevatorSettings.defaults();
        this.settings = section == null ? d : new ElevatorSettings(
                section.getBoolean("enabled", d.enabled()),
                section.getString("header", d.header()).trim(),
                section.getString("up-word", d.upWord()).trim(),
                section.getString("down-word", d.downWord()).trim(),
                section.getInt("max-distance", d.maxDistance()),
                section.getLong("cooldown-seconds", d.cooldownSeconds()),
                section.getBoolean("own-territory-only", d.ownTerritoryOnly()),
                section.getBoolean("blocked-in-combat", d.blockedInCombat()));
    }

    public ElevatorSettings getSettings() {
        return settings;
    }

    public LangManager getLang() {
        return lang;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    /** Where an elevator sign keeps its mark, and the way it goes. */
    public NamespacedKey getKey() {
        return key;
    }

    public Cooldowns getCooldowns() {
        return cooldowns;
    }

    /** @return the way a written second line goes, if it is one of the two words */
    public Optional<ElevatorRules.Direction> direction(String word) {
        String typed = word == null ? "" : word.trim();
        if (typed.equalsIgnoreCase(settings.upWord())) {
            return Optional.of(ElevatorRules.Direction.UP);
        }
        return typed.equalsIgnoreCase(settings.downWord()) ? Optional.of(ElevatorRules.Direction.DOWN) : Optional.empty();
    }

    /**
     * Takes a player from an elevator sign to the next floor that way, or tells them
     * why not.
     */
    public void ride(Player player, Block sign, ElevatorRules.Direction direction) {
        long now = System.currentTimeMillis();
        if (cooldowns.isWaiting(player.getUniqueId(), COOLDOWN_KEY, now)) {
            return;
        }
        if (settings.blockedInCombat() && pvp != null && pvp.getCombatTags() != null
                && pvp.getCombatTags().isTagged(player.getUniqueId())) {
            lang.send(player, ElevatorMessages.IN_COMBAT);
            return;
        }
        if (settings.ownTerritoryOnly() && !onOwnLand(player, sign.getLocation())) {
            lang.send(player, ElevatorMessages.NOT_YOUR_LAND);
            return;
        }
        World world = sign.getWorld();
        int x = sign.getX();
        int z = sign.getZ();
        OptionalInt floor;
        OptionalInt linked = ElevatorRules.linkedSign(sign.getY(), world.getMinHeight(), world.getMaxHeight(),
                direction, settings.maxDistance(), y -> isElevator(world.getBlockAt(x, y, z)));
        if (linked.isPresent()) {
            // Another elevator in the column: straight there, whatever lies between.
            floor = ElevatorRules.arrivalAtLinked(linked.getAsInt(),
                    y -> firm(world.getBlockAt(x, y, z)), y -> free(world.getBlockAt(x, y, z)));
            if (floor.isEmpty()) {
                lang.send(player, ElevatorMessages.NO_ROOM);
                return;
            }
        } else {
            floor = ElevatorRules.destination(ElevatorRules.searchFrom(sign.getY(), direction), world.getMinHeight(), world.getMaxHeight(), direction,
                    settings.maxDistance(), y -> firm(world.getBlockAt(x, y, z)), y -> free(world.getBlockAt(x, y, z)));
        }
        if (floor.isEmpty()) {
            lang.send(player, ElevatorMessages.NO_FLOOR);
            return;
        }
        cooldowns.start(player.getUniqueId(), COOLDOWN_KEY, settings.cooldownSeconds(), now);
        Location from = player.getLocation();
        player.teleport(new Location(world, x + 0.5, floor.getAsInt(), z + 0.5, from.getYaw(), from.getPitch()),
                PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    private boolean onOwnLand(Player player, Location at) {
        if (claims == null || claims.getManager() == null) {
            return true;
        }
        Optional<Team> owner = claims.ownerAt(at);
        Optional<Team> theirs = claims.getTeams().getManager().getTeamOf(player.getUniqueId());
        return owner.isPresent() && theirs.isPresent() && owner.get().getId().equals(theirs.get().getId());
    }

    /** Whether a block is an elevator sign: marked when it was written. */
    public boolean isElevator(Block block) {
        return org.bukkit.Tag.ALL_SIGNS.isTagged(block.getType())
                && block.getState(false) instanceof org.bukkit.block.Sign sign
                && sign.getPersistentDataContainer().has(key);
    }

    /**
     * Whether a player can stand on a block. Not {@code Material#isSolid}: the game
     * counts a sign as solid, and a sign then made a floor - the rider was put on top
     * of the very sign they clicked.
     */
    private static boolean firm(Block block) {
        return !block.isPassable() && !block.isLiquid();
    }

    private static boolean free(Block block) {
        Material type = block.getType();
        return block.isPassable() && !block.isLiquid() && type != Material.FIRE && type != Material.SOUL_FIRE
                && type != Material.POWDER_SNOW;
    }

    public void forget(Player player) {
        cooldowns.forget(player.getUniqueId());
    }
}
