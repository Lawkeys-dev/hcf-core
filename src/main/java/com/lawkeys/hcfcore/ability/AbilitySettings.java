package com.lawkeys.hcfcore.ability;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code abilities.yml}: the shared rules, every ability, and the Pocket Bard's sets.
 *
 * @param globalCooldownSeconds a wait after any ability before the next one, whichever
 * @param hitsWithinSeconds     the most time between two hits counted towards {@code hits-required}
 * @param disabledIn            where no ability works
 */
public record AbilitySettings(boolean enabled, long globalCooldownSeconds, long hitsWithinSeconds, String menuTitle,
                              DisabledIn disabledIn, List<Ability> abilities,
                              String pocketBardTitle, int pocketBardSize, List<PocketBardItem> pocketBard) {

    /**
     * Where abilities are refused.
     *
     * @param eventTerritory when abilities are refused on the land of an event - its
     *                       server team's claims ({@code /events claim}): while it
     *                       runs, always, or never; an event may say otherwise with
     *                       its own {@code disable-abilities} key in {@code events.yml}
     */
    public record DisabledIn(boolean safezone, boolean citadel, boolean events, boolean nether, boolean end,
                             boolean warzone,
                             com.lawkeys.hcfcore.events.setup.TerritoryAbilityMode eventTerritory) {
    }

    public AbilitySettings {
        Objects.requireNonNull(disabledIn, "disabledIn");
        abilities = List.copyOf(abilities);
        pocketBard = List.copyOf(pocketBard);
        globalCooldownSeconds = Math.max(0L, globalCooldownSeconds);
        hitsWithinSeconds = Math.max(1L, hitsWithinSeconds);
    }

    public static AbilitySettings defaults() {
        return new AbilitySettings(true, 10, 10, "{primary}&lAbilities", new DisabledIn(true, true, true, true, true, false,
                com.lawkeys.hcfcore.events.setup.TerritoryAbilityMode.DURING_EVENT),
                List.of(), "{primary}&lPocket Bard", 27, List.of());
    }

    public Optional<Ability> ability(String id) {
        String key = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        return abilities.stream().filter(ability -> ability.id().equals(key)).findFirst();
    }

    public Optional<PocketBardItem> pocketBardItem(String id) {
        String key = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        return pocketBard.stream().filter(item -> item.id().equals(key)).findFirst();
    }
}
