package com.lawkeys.hcfcore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Text already coloured with section signs ({@code LangManager#colorize}) as a
 * component - hex colours included, which the game writes {@code §x§r§r§g§g§b§b} and
 * Adventure's plain {@code legacySection()} would print as they are.
 */
public final class LegacyText {

    public static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character(ColorCodes.COLOR_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private LegacyText() {
    }

    public static Component of(String legacy) {
        return SERIALIZER.deserialize(legacy == null ? "" : legacy);
    }
}
