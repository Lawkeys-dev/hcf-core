package com.lawkeys.hcfcore.claim.view;

import com.lawkeys.hcfcore.claim.ClaimArea;
import com.lawkeys.hcfcore.claim.ClaimManager;
import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.claim.ClaimSettings;
import com.lawkeys.hcfcore.team.Team;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /team map} drawn in the world: a column on each corner of every claim
 * around the player, one block kind per team, drawn for that player only - the
 * project owner's choice, 22/09/2026, in place of a grid of characters in the chat.
 *
 * <p>The kinds are handed out at random each time the map is asked for, so a team
 * has no fixed colour to learn; which team got which is said in the chat under the
 * map. They are full blocks only ({@code claims.yml}, {@code map.pillars.materials}):
 * a slab or a pane would read as decoration and cost the client more to draw.
 */
public final class MapPillars {

    /** What one call drew: the teams found, in the order they were drawn, and their blocks. */
    public record Drawn(Map<UUID, String> materials, int claims) {

        public Drawn {
            materials = Map.copyOf(materials);
        }
    }

    private final ClaimModule module;
    private final ClientBlocks view;
    /** Each player's countdown to the pillars going away. */
    private final Map<UUID, Integer> fading = new ConcurrentHashMap<>();

    public MapPillars(ClaimModule module, ClientBlocks view) {
        this.module = Objects.requireNonNull(module, "module");
        this.view = Objects.requireNonNull(view, "view");
    }

    /**
     * Draws the map for this player, replacing whatever they were shown before.
     *
     * @return the teams drawn and the block each got, for the legend in the chat
     */
    public Drawn show(Player player) {
        ClaimSettings.MapRules rules = module.getSettings().map();
        ClaimManager manager = module.getManager();
        World world = player.getWorld();
        Location at = player.getLocation();
        int radius = rules.radiusChunks() * 16;
        List<ClaimArea> claims = LockWalls.nearbyClaims(manager, world.getName(), at.getBlockX(), at.getBlockZ(), radius);

        List<String> palette = new ArrayList<>(rules.materials());
        java.util.Collections.shuffle(palette);
        Map<UUID, String> materials = new LinkedHashMap<>();
        Map<Location, BlockData> pillars = new HashMap<>();
        int drawn = 0;
        for (ClaimArea claim : claims) {
            if (!rules.includeSystemClaims() && module.getTeams().getManager().getTeam(claim.teamId())
                    .map(team -> team.getType().isSystem()).orElse(false)) {
                continue; // Server land - spawn, roads, event grounds - is not what the map is for.
            }
            List<int[]> corners = BorderColumns.corners(claim, at.getBlockX(), at.getBlockZ(), radius);
            if (corners.isEmpty()) {
                continue;
            }
            String material = materials.computeIfAbsent(claim.teamId(),
                    team -> palette.get(materials.size() % palette.size()));
            Material block = Material.matchMaterial(material);
            if (block == null) {
                continue;
            }
            BlockData data = block.createBlockData();
            drawn++;
            for (int[] corner : corners) {
                int[] range = ColumnHeights.range(world.getHighestBlockYAt(corner[0], corner[1]),
                        rules.topY(), rules.minimumHeight(), world.getMaxHeight());
                if (range == null) {
                    continue;
                }
                for (int y = range[0]; y <= range[1]; y++) {
                    Location column = new Location(world, corner[0], y, corner[1]);
                    if (column.getBlock().getType().isAir()) {
                        pillars.put(column, data);
                    }
                }
            }
        }
        view.show(player, pillars);
        fade(player, rules.seconds());
        return new Drawn(materials, drawn);
    }

    /**
     * Takes the pillars away if this player has any: {@code /team map} again is what
     * turns them off, and once more brings them back.
     *
     * @return whether there were any to take away
     */
    public boolean hideIfShown(Player player) {
        if (!view.isShowing(player.getUniqueId())) {
            return false;
        }
        Integer running = fading.remove(player.getUniqueId());
        if (running != null) {
            Bukkit.getScheduler().cancelTask(running);
        }
        view.clear(player);
        return true;
    }

    /** @return the name of the team this claim belongs to, for the legend */
    public Optional<String> teamName(UUID teamId) {
        return module.getTeams().getManager().getTeam(teamId).map(Team::getName);
    }

    /** Takes the pillars away after the time the settings give, unless the map is asked for again. */
    private void fade(Player player, long seconds) {
        UUID id = player.getUniqueId();
        Integer running = fading.remove(id);
        if (running != null) {
            Bukkit.getScheduler().cancelTask(running);
        }
        int task = Bukkit.getScheduler().runTaskLater(module.getPlugin(), () -> {
            fading.remove(id);
            Player still = Bukkit.getPlayer(id);
            if (still != null) {
                view.clear(still);
            }
        }, Math.max(1L, seconds * 20L)).getTaskId();
        fading.put(id, task);
    }

    /** Server stopping or settings reloaded: the pillars go, and their countdowns with them. */
    public void stop() {
        fading.values().forEach(Bukkit.getScheduler()::cancelTask);
        fading.clear();
        view.clearAll(Bukkit.getOnlinePlayers());
    }
}
