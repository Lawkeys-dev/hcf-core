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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        Bounties all = bounties.get();
        EconomyManager money = economy.get();
        if (killer == null || killer.equals(victim) || all == null || money == null || !rules.get().enabled()
                || all.get(victim.getUniqueId()) <= 0
                || Sides.same(teams.get(), killer.getUniqueId(), victim.getUniqueId())) {
            return;
        }
        double amount = all.claim(victim.getUniqueId());
        if (!money.deposit(killer.getUniqueId(), amount).isOk()) {
            all.restore(victim.getUniqueId(), amount);
            return;
        }
        String line = lang.get(EconomyMessages.BOUNTY_CLAIMED, "killer", killer.getName(),
                "player", victim.getName(), "amount", money.format(amount));
        Bukkit.getOnlinePlayers().forEach(online -> online.sendMessage(line));
        Bukkit.getConsoleSender().sendMessage(line);
    }
}
