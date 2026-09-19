package com.lawkeys.hcfcore.integration.lunar;

import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.crowbar.CrowbarModule;
import com.lawkeys.hcfcore.dtr.DtrModule;
import com.lawkeys.hcfcore.events.EventModule;
import com.lawkeys.hcfcore.ability.AbilityModule;
import com.lawkeys.hcfcore.pvp.PvpModule;
import com.lawkeys.hcfcore.pvpclass.ClassModule;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.warmup.WarmupModule;

import java.util.Objects;

/**
 * The modules the Lunar Client integration reads what it shows from. {@code classes}
 * and {@code crowbar} may be {@code null}: their cooldowns are then not shown.
 */
public record LunarSources(TeamModule teams, ClaimModule claims, DtrModule dtr, PvpModule pvp,
                           EventModule events, AbilityModule abilities, WarmupModule warmups,
                           ClassModule classes, CrowbarModule crowbar) {

    public LunarSources {
        Objects.requireNonNull(teams, "teams");
        Objects.requireNonNull(claims, "claims");
        Objects.requireNonNull(dtr, "dtr");
        Objects.requireNonNull(pvp, "pvp");
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(abilities, "abilities");
        Objects.requireNonNull(warmups, "warmups");
    }
}
