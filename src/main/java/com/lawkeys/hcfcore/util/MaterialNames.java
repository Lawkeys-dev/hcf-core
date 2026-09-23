package com.lawkeys.hcfcore.util;

import java.util.Locale;

/** A block or item named as players read it. Pure Java. */
public final class MaterialNames {

    private MaterialNames() {
    }

    /** @return {@code DEEPSLATE_DIAMOND_ORE} as {@code Deepslate Diamond Ore} */
    public static String readable(String material) {
        StringBuilder name = new StringBuilder();
        for (String word : material.toLowerCase(Locale.ROOT).split("_")) {
            if (!word.isEmpty()) {
                name.append(name.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1));
            }
        }
        return name.toString();
    }
}
