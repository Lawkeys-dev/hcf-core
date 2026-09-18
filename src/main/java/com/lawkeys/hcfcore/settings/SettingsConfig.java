package com.lawkeys.hcfcore.settings;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * {@code settings.yml}: which player settings are offered, and what counts as
 * cobblestone for {@code /cobble}.
 *
 * @param cobblestone item names as written in the file ({@code cobbled_deepslate},
 *                    {@code minecraft:cobblestone}); the module turns them into materials
 */
public record SettingsConfig(boolean enabled, Set<PlayerSetting> offered, Set<String> cobblestone) {

    public SettingsConfig {
        offered = Set.copyOf(Objects.requireNonNull(offered, "offered"));
        cobblestone = Set.copyOf(Objects.requireNonNull(cobblestone, "cobblestone"));
    }

    /** Built-in fallback, as shipped: every setting, both cobblestones. */
    public static SettingsConfig defaults() {
        return new SettingsConfig(true, EnumSet.allOf(PlayerSetting.class),
                Set.of("COBBLESTONE", "COBBLED_DEEPSLATE"));
    }
}
