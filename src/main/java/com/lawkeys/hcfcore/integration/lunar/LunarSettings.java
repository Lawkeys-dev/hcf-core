package com.lawkeys.hcfcore.integration.lunar;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Immutable snapshot of {@code apollo.yml}: what the Lunar Client integration shows.
 *
 * @param updateTicks how often everything is brought up to date; only what changed
 *                    is sent
 */
public record LunarSettings(boolean enabled, int updateTicks, Waypoints waypoints, TeamView teamView,
                            Cooldowns cooldowns, Nametags nametags) {

    public LunarSettings {
        Objects.requireNonNull(waypoints, "waypoints");
        Objects.requireNonNull(teamView, "teamView");
        Objects.requireNonNull(cooldowns, "cooldowns");
        Objects.requireNonNull(nametags, "nametags");
        updateTicks = Math.max(1, updateTicks);
    }

    /**
     * Colours are {@code 0xRRGGBB}.
     *
     * @param eventHeight how far above an event's own point its waypoint is put, in
     *                    blocks. {@code 0} - as shipped - puts it on the objective
     *                    itself, which is where the beam should start; raise it only
     *                    if your map hides the marker. The King's follows the player,
     *                    never raised
     */
    public record Waypoints(boolean enabled, boolean hq, boolean base, boolean rally, boolean focus,
                            boolean events, int eventHeight, int hqColor, int baseColor, int rallyColor,
                            int focusColor, int eventColor) {

        public Waypoints {
            eventHeight = Math.max(0, Math.min(320, eventHeight));
        }
    }

    /** @param trackingRange beyond this, in blocks, a teammate's position is sent as well as their marker */
    public record TeamView(boolean enabled, int markerColor, double trackingRange) {
    }

    /** @param combatTagIcon, warmupIcon, abilityGlobalIcon, enderPearlIcon item names, as {@code Material} spells them */
    public record Cooldowns(boolean enabled, boolean combatTag, String combatTagIcon, boolean warmups,
                            String warmupIcon, boolean abilities, String abilityGlobalIcon, boolean enderPearl,
                            String enderPearlIcon, boolean itemCooldowns, boolean classes, boolean crowbar) {
    }

    public record Nametags(boolean enabled, NametagStyle style) {
    }

    /** Built-in fallback, mirroring {@code resources/apollo.yml}. */
    public static LunarSettings defaults() {
        return new LunarSettings(true, 10,
                new Waypoints(true, true, true, true, true, true, 0,
                        0x55FF55, 0x00AA00, 0xFFFF55, 0xFF55FF, 0xFFAA00),
                new TeamView(true, 0x55FF55, 48.0),
                new Cooldowns(true, true, "DIAMOND_SWORD", true, "CLOCK", true, "NETHER_STAR", true, "ENDER_PEARL", true,
                        true, true),
                new Nametags(true, new NametagStyle("%color%%team% {dark}| {warning}%dtr%", "%color%%player%", Map.of(
                        NametagStyle.Relation.SELF, "&a",
                        NametagStyle.Relation.ALLY, "&9",
                        NametagStyle.Relation.ENEMY, "&c",
                        NametagStyle.Relation.FOCUS, "&d",
                        NametagStyle.Relation.NEUTRAL, "&f"))));
    }

    /** @return {@code 0xRRGGBB} from {@code "#RRGGBB"} or {@code "RRGGBB"}; empty when it is not one */
    public static OptionalInt parseRgb(String text) {
        if (text == null) {
            return OptionalInt.empty();
        }
        String hex = text.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(hex.toLowerCase(Locale.ROOT), 16));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }
}
