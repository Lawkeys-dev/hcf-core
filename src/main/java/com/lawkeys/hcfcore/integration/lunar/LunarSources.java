package com.lawkeys.hcfcore.integration.lunar;

import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.dtr.DtrModule;
import com.lawkeys.hcfcore.events.EventModule;
import com.lawkeys.hcfcore.kit.KitModule;
import com.lawkeys.hcfcore.pvp.PvpModule;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.warmup.WarmupModule;

import java.util.Objects;

/** The modules the Lunar Client integration reads what it shows from. */
public record LunarSources(TeamModule teams, ClaimModule claims, DtrModule dtr, PvpModule pvp,
                           EventModule events, KitModule kits, WarmupModule warmups) {

    public LunarSources {
        Objects.requireNonNull(teams, "teams");
        Objects.requireNonNull(claims, "claims");
        Objects.requireNonNull(dtr, "dtr");
        Objects.requireNonNull(pvp, "pvp");
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(kits, "kits");
        Objects.requireNonNull(warmups, "warmups");
    }
}
