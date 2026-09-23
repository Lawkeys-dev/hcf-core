package com.lawkeys.hcfcore.pvp;

/** Every language key the PvP module can produce. Audited against {@code lang/en.yml}. */
public final class PvpMessages {

    public static final String LEGACY_NO_OFFHAND = "pvp.legacy.no-offhand";
    public static final String LEGACY_NO_SHIELD = "pvp.legacy.no-shield";

    private PvpMessages() {
    }

    // Ender pearl cooldown
    public static final String PEARL_COOLDOWN = "pvp.pearl.cooldown";
    public static final String PEARL_BLOCKS_TELEPORT = "pvp.pearl.blocks-teleport";

    // Item cooldowns
    public static final String ITEM_COOLDOWN = "pvp.item-cooldown.refused";

    // Combat tag
    public static final String TAGGED = "pvp.tag.tagged";
    public static final String TAG_EXPIRED = "pvp.tag.expired";
    public static final String TAG_BLOCKS_TELEPORT = "pvp.tag.blocks-teleport";
    public static final String TAG_BLOCKS_LOGOUT = "pvp.tag.blocks-logout";
    public static final String TAG_STATUS_ACTIVE = "pvp.tag.status-active";
    public static final String TAG_STATUS_CLEAR = "pvp.tag.status-clear";

    // Deathban
    public static final String DEATHBAN_APPLIED = "pvp.deathban.applied";
    public static final String DEATHBAN_LOGIN_DENIED = "pvp.deathban.login-denied";
    public static final String DEATHBAN_LIFTED = "pvp.deathban.lifted";
    public static final String DEATHBAN_NOT_BANNED = "pvp.deathban.not-banned";
    public static final String DEATHBAN_STATUS = "pvp.deathban.status";
    public static final String DEATHBAN_SET = "pvp.deathban.set";
    public static final String DEATHBAN_INVALID_LENGTH = "pvp.deathban.invalid-length";
    // A ban that lasts until the map ends (EOTW) has no time left to show
    public static final String DEATHBAN_MAP_END_APPLIED = "pvp.deathban.map-end.applied";
    public static final String DEATHBAN_MAP_END_LOGIN_DENIED = "pvp.deathban.map-end.login-denied";
    public static final String DEATHBAN_MAP_END_STATUS = "pvp.deathban.map-end.status";

    // Safe zones
    public static final String DEATH_SIGN_NAME = "pvp.death-sign.name";
    public static final String DEATH_SIGN_LINE_1 = "pvp.death-sign.line-1";
    public static final String DEATH_SIGN_LINE_2 = "pvp.death-sign.line-2";
    public static final String DEATH_SIGN_LINE_3 = "pvp.death-sign.line-3";
    public static final String DEATH_SIGN_LINE_4 = "pvp.death-sign.line-4";
    public static final String SAFE_ZONE_COMBAT = "pvp.safezone.combat";
    public static final String SAFE_ZONE_ATTACKER = "pvp.safezone.attacker";
    public static final String FRIENDLY_FIRE_TEAMMATE = "pvp.friendly-fire.teammate";
    public static final String FRIENDLY_FIRE_ALLY = "pvp.friendly-fire.ally";

    // Command layer
    public static final String STATUS_HEADER = "pvp.status.header";
    public static final String PLAYER_NOT_FOUND = "pvp.error.player-not-found";
    public static final String INVALID_NUMBER = "pvp.error.invalid-number";
    public static final String DISABLED = "pvp.error.disabled";
}
