package com.lawkeys.hcfcore.events.king;

/**
 * {@code kill-the-king.<id>.reign:} - what the crown asks of the King, and what
 * dying with it costs them.
 *
 * <p>By default the King's death is the event's, not a real one: the kit is gone
 * with the reign, nobody picks it up, and the King's team loses no DTR and the
 * King is not deathbanned. Their own items were put aside when they were crowned
 * and come back at the respawn, as ever.
 *
 * @param lockArmour    the King cannot take off the kit's armour - no click, drag,
 *                      key swap or right-click swap with another piece
 * @param dropKit       the kit falls at the King's death, as loot for the killer
 *                      (loot protection, {@code pvp.yml}); otherwise it vanishes. What
 *                      the King picked up during the reign falls either way
 * @param deathCostsDtr the King's death takes DTR from their team, as any death
 * @param deathban      the King's death deathbans them, as any death
 * @param quitBanSeconds a King who logs out ends the event with no winner and is
 *                       banned for this long (2 hours as shipped); {@code 0} for no ban
 * @param afkSeconds    a player idle this long - no move, look, chat, command or
 *                      click - is not drawn as King; {@code 0} draws anybody
 * @param wall          the King sees the warzone's border as a wall when near it, as a
 *                      player in combat sees spawn's; leaving is refused either way
 * @param wallMaterial  the block it is drawn in
 * @param wallRadius    drawn within this many blocks of the King, all round
 */
public record ReignRules(boolean lockArmour, boolean dropKit, boolean deathCostsDtr, boolean deathban,
                         long quitBanSeconds, long afkSeconds, boolean wall, String wallMaterial, int wallRadius) {

    public ReignRules {
        quitBanSeconds = Math.max(0L, quitBanSeconds);
        afkSeconds = Math.max(0L, afkSeconds);
        wallMaterial = wallMaterial == null || wallMaterial.isBlank() ? "RED_STAINED_GLASS" : wallMaterial;
        wallRadius = Math.max(1, Math.min(32, wallRadius));
    }

    public ReignRules(boolean lockArmour, boolean dropKit, boolean deathCostsDtr, boolean deathban) {
        this(lockArmour, dropKit, deathCostsDtr, deathban, 7200L, 300L, true, "RED_STAINED_GLASS", 15);
    }

    public static ReignRules defaults() {
        return new ReignRules(true, false, false, false, 7200L, 300L, true, "RED_STAINED_GLASS", 15);
    }
}
