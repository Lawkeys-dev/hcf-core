package com.lawkeys.hcfcore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Text for an item's name or a lore line. The game draws a custom name and lore in
 * italics unless the text says otherwise, which reads as a renamed item rather than
 * a menu or a plugin's item: every name and lore line this plugin writes goes through
 * here, upright.
 */
public final class ItemText {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private ItemText() {
    }

    /** @param legacy text already coloured with section signs ({@code LangManager#colorize}) */
    public static Component line(String legacy) {
        return LEGACY.deserialize(legacy == null ? "" : legacy).decoration(TextDecoration.ITALIC, false);
    }
}
