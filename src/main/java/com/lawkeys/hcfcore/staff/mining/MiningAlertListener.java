package com.lawkeys.hcfcore.staff.mining;

import com.lawkeys.hcfcore.staff.StaffMessages;
import com.lawkeys.hcfcore.staff.StaffModule;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Says when a player breaks into a vein of a reported ore: once per vein, with its
 * size, and never for an ore a player placed - a placed diamond ore would otherwise
 * be an alert machine.
 *
 * <p>What is remembered - the ores placed by players, the veins already reported -
 * lives in memory, bounded, and goes with a restart: the worst it costs is one
 * extra alert for a vein half-mined across a restart.
 */
public final class MiningAlertListener implements Listener {

    /** How many positions each of the two memories keeps before forgetting the oldest. */
    private static final int REMEMBERED = 50_000;

    private final StaffModule module;
    private final Supplier<MiningAlertRules> rules;
    private final Set<String> placed = bounded();
    private final Set<String> reported = bounded();

    public MiningAlertListener(StaffModule module, Supplier<MiningAlertRules> rules) {
        this.module = Objects.requireNonNull(module, "module");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    private static Set<String> bounded() {
        return Collections.newSetFromMap(new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > REMEMBERED;
            }
        });
    }

    private static String key(World world, int x, int y, int z) {
        return world.getName() + ':' + x + ':' + y + ':' + z;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (rules.get().reports(block.getType())) {
            placed.add(key(block.getWorld(), block.getX(), block.getY(), block.getZ()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        MiningAlertRules current = rules.get();
        Block block = event.getBlock();
        Material ore = block.getType();
        if (!current.reports(ore)) {
            return;
        }
        World world = block.getWorld();
        String mined = key(world, block.getX(), block.getY(), block.getZ());
        if (placed.remove(mined) || reported.remove(mined)) {
            return; // A player's own block, or part of a vein already reported.
        }
        Player player = event.getPlayer();
        if (current.ignoreCreative() && player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        Set<VeinCounter.Position> vein = VeinCounter.vein(
                new VeinCounter.Position(block.getX(), block.getY(), block.getZ()),
                at -> world.getBlockAt(at.x(), at.y(), at.z()).getType() == ore
                        && !placed.contains(key(world, at.x(), at.y(), at.z())),
                current.maxVein());
        for (VeinCounter.Position at : vein) {
            if (!(at.x() == block.getX() && at.y() == block.getY() && at.z() == block.getZ())) {
                reported.add(key(world, at.x(), at.y(), at.z()));
            }
        }
        announce(player, ore, vein.size(), block, current.everyone());
    }

    private void announce(Player player, Material ore, int count, Block at, boolean everyone) {
        String name = MiningAlertRules.readable(ore);
        String staffLine = module.getLang().get(StaffMessages.MINING_FOUND_STAFF, "player", player.getName(),
                "count", String.valueOf(count), "ore", name, "world", at.getWorld().getName(),
                "x", String.valueOf(at.getX()), "y", String.valueOf(at.getY()), "z", String.valueOf(at.getZ()));
        String publicLine = module.getLang().get(StaffMessages.MINING_FOUND, "player", player.getName(),
                "count", String.valueOf(count), "ore", name);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(StaffModule.STAFF_PERMISSION)) {
                online.sendMessage(staffLine);
            } else if (everyone && !publicLine.isEmpty()) {
                online.sendMessage(publicLine);
            }
        }
        Bukkit.getConsoleSender().sendMessage(staffLine);
    }
}
