package com.lawkeys.hcfcore.events.totem;

import com.lawkeys.hcfcore.util.Cuboid;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * One Totem or Mini Totem, as read from {@code events.yml} ({@code totem:}).
 *
 * <p>A column of {@link #height()} blocks standing on {@code base}. Between runs it is
 * made of {@link #idleMaterial()} (bedrock); a run turns it to
 * {@link #activeMaterial()} (quartz), and every block a team breaks becomes
 * {@link #brokenMaterial()} (bedrock again). The first team to break the whole
 * column wins. A Mini Totem is only a shorter column.
 *
 * @param zone         the event's zone, which the column stands in: allies may fight
 *                     there, partner items may be refused there, as for every event
 * @param baseX/Y/Z    the column's lowest block
 * @param allowedTools the items a block may be broken with - every sword by default
 * @param instantBreak whether an allowed hit breaks a block at once, rather than after
 *                     the time the game gives that block to that tool
 */
public record TotemDefinition(String id,
                              String displayName,
                              Cuboid zone,
                              int baseX,
                              int baseY,
                              int baseZ,
                              int height,
                              String activeMaterial,
                              String brokenMaterial,
                              String idleMaterial,
                              List<String> allowedTools,
                              boolean instantBreak,
                              RivalBreak rivalBreak,
                              boolean announceBreaks,
                              List<LocalTime> schedule,
                              long maxDurationSeconds,
                              List<String> rewardCommands) {

    public TotemDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(activeMaterial, "activeMaterial");
        Objects.requireNonNull(brokenMaterial, "brokenMaterial");
        Objects.requireNonNull(idleMaterial, "idleMaterial");
        Objects.requireNonNull(rivalBreak, "rivalBreak");
        allowedTools = List.copyOf(Objects.requireNonNull(allowedTools, "allowedTools"));
        schedule = List.copyOf(Objects.requireNonNull(schedule, "schedule"));
        rewardCommands = List.copyOf(Objects.requireNonNull(rewardCommands, "rewardCommands"));
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive for totem " + id);
        }
        if (!zone.containsBlock(zone.world(), baseX, baseY, baseZ)
                || !zone.containsBlock(zone.world(), baseX, baseY + height - 1, baseZ)) {
            throw new IllegalArgumentException("the column of '" + id + "' must stand inside its own zone");
        }
    }

    /**
     * @return which block of the column that is, {@code 0} for the lowest, or
     *         {@code -1} when it is not part of it
     */
    public int levelOf(String world, int x, int y, int z) {
        if (!zone.world().equalsIgnoreCase(world) || x != baseX || z != baseZ) {
            return -1;
        }
        int level = y - baseY;
        return level >= 0 && level < height ? level : -1;
    }

    /** @return whether that item may break a block of the column; an empty list allows anything */
    public boolean allowsTool(String material) {
        return allowedTools.isEmpty() || allowedTools.stream().anyMatch(tool -> tool.equalsIgnoreCase(material));
    }
}
