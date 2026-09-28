package com.lawkeys.hcfcore.economy.bounty;

import com.lawkeys.hcfcore.economy.EconomyManager;
import com.lawkeys.hcfcore.economy.EconomyMessages;
import com.lawkeys.hcfcore.economy.reward.Sides;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.team.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Pays a bounty to whoever kills its target. A teammate or an ally cannot collect
 * it - the bounty stays for somebody who will actually fight for it - and a killer
 * at the balance ceiling leaves it in place rather than lose it.
 */
public final class BountyListener implements Listener {

    private final Supplier<Bounties> bounties;
    private final Supplier<BountyRules> rules;
    private final Supplier<EconomyManager> economy;
    private final Supplier<TeamManager> teams;
    private final LangManager lang;

    public BountyListener(Supplier<Bounties> bounties, Supplier<BountyRules> rules, Supplier<EconomyManager> economy,
                          Supplier<TeamManager> teams, LangManager lang) {
        this.bounties = Objects.requireNonNull(bounties, "bounties");
        this.rules = Objects.requireNonNull(rules, "rules");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.lang = Objects.requireNonNull(lang, "lang");
    }

    /** Not on a death another plugin cancelled: the target lives, and the bounty stays. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        claim(victim.getUniqueId(), victim.getName(), victim.getKiller());
    }

    /** A combat logger's stand-in killed: the player died, though away. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoggerDeath(com.lawkeys.hcfcore.api.event.CombatLoggerDeathEvent event) {
        claim(event.getVictimId(), event.getVictimName(), event.getKiller().orElse(null));
    }

    private void claim(java.util.UUID victimId, String victimName, Player killer) {
        Bounties all = bounties.get();
        EconomyManager money = economy.get();
        if (killer == null || killer.getUniqueId().equals(victimId) || all == null || money == null || !rules.get().enabled()
                || all.get(victimId) <= 0
                || Sides.same(teams.get(), killer.getUniqueId(), victimId)) {
            return;
        }
        double amount = all.claim(victimId);
        if (!money.deposit(killer.getUniqueId(), amount).isOk()) {
            all.restore(victimId, amount);
            return;
        }
        String line = lang.get(EconomyMessages.BOUNTY_CLAIMED, "killer", killer.getName(),
                "player", victimName, "amount", money.format(amount));
        Bukkit.getOnlinePlayers().forEach(online -> online.sendMessage(line));
        Bukkit.getConsoleSender().sendMessage(line);
        com.lawkeys.hcfcore.util.Announcements.publish(EconomyMessages.BOUNTY_CLAIMED, line);
    }
}
