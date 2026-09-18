package com.lawkeys.hcfcore.ability;

import java.util.List;
import java.util.Objects;

/**
 * A set a Pocket Bard gives ({@code pocket-bard} in {@code abilities.yml}): the
 * items, and the effect each gives when right-clicked.
 *
 * @param id          identifier, stored on the items
 * @param material    the item, upper case
 * @param name        its name
 * @param lore        under the name
 * @param amount      how many a pick gives
 * @param effect      what a right-click gives
 * @param radius      blocks around the user its effect reaches
 * @param includeSelf whether the user gets it too, besides the teammates in range
 * @param slot        its place in the selection menu, from 0
 * @param menuName    its name in the menu
 */
public record PocketBardItem(String id, String material, String name, List<String> lore, int amount,
                             AbilityEffect effect, double radius, boolean includeSelf, int slot, String menuName) {

    public PocketBardItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(effect, "effect");
        lore = List.copyOf(lore);
        amount = Math.max(1, Math.min(64, amount));
        radius = Math.max(0.0, radius);
    }
}
