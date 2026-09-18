package com.lawkeys.hcfcore.settings;

/** Language keys used by the player settings module (see {@code lang/en.yml}). */
public final class SettingsMessages {

    public static final String MENU_TITLE = "settings.menu-title";
    public static final String STATE_ON = "settings.state-on";
    public static final String STATE_OFF = "settings.state-off";
    public static final String LORE_STATE = "settings.lore-state";
    public static final String LORE_CLICK = "settings.lore-click";
    public static final String CHANGED = "settings.changed";
    public static final String UNKNOWN = "settings.unknown";
    public static final String LIST_HEADER = "settings.list-header";
    public static final String LIST_ENTRY = "settings.list-entry";
    public static final String DISABLED = "settings.disabled";
    public static final String USAGE = "settings.usage";

    /** @return the key of a setting's display name */
    public static String name(PlayerSetting setting) {
        return "settings.names." + setting.key();
    }

    /** @return the key of a setting's one-line description */
    public static String description(PlayerSetting setting) {
        return "settings.descriptions." + setting.key();
    }

    private SettingsMessages() {
    }
}
