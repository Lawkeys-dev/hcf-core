package com.lawkeys.hcfcore.economy.reward;

import com.lawkeys.hcfcore.economy.EconomyManager;
import com.lawkeys.hcfcore.economy.EconomyMessages;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamManager;
import com.lawkeys.hcfcore.team.TeamRelation;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Pays a kill ({@link KillReward}): the flat amount from nowhere, the stolen share
 * from the dead player. Nothing for killing an ally - allies may only fight in an
 * event area, and a reward there would pay allies to farm each other.
 */
public final class KillRewardListener implements Listener {

    private final Supplier<EconomyManager> economy;
    private final Supplier<TeamManager> teams;
    private final LangManager lang;
    private final Supplier<KillReward.Rules> rules;
    private final KillReward reward = new KillReward();

    public KillRewardListener(Supplier<EconomyManager> economy, Supplier<TeamManager> teams, LangManager lang,
                              Supplier<KillReward.Rules> rules) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.lang = Objects.requireNonNull(lang, "lang");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        EconomyManager money = economy.get();
        KillReward.Rules current = rules.get();
        if (killer == null || money == null || !current.enabled() || sameSide(killer, victim)) {
            return;
        }
        long now = System.currentTimeMillis();
        KillReward.Payout payout = reward.payout(current, killer.getUniqueId(), victim.getUniqueId(),
                money.getBalance(victim.getUniqueId()), now);
        reward.forgetOlderThan(now - current.sameVictimCooldownSeconds() * 1000L);
        if (payout.isEmpty()) {
            return;
        }
        double stolen = 0.0;
        if (payout.stolen() > 0 && money.withdraw(victim.getUniqueId(), payout.stolen()).isOk()) {
            stolen = payout.stolen();
        }
        double total = payout.flat() + stolen;
        if (total <= 0 || !money.deposit(killer.getUniqueId(), total).isOk()) {
            // The killer's balance is at its ceiling: nothing is lost on the way.
            if (stolen > 0) {
                money.restore(victim.getUniqueId(), stolen);
            }
            return;
        }
        lang.send(killer, EconomyMessages.KILL_REWARD, "amount", money.format(total), "victim", victim.getName());
        if (stolen > 0) {
            lang.send(victim, EconomyMessages.KILL_REWARD_STOLEN, "amount", money.format(stolen),
                    "killer", killer.getName());
        }
    }

    private boolean sameSide(Player killer, Player victim) {
        TeamManager manager = teams.get();
        if (manager == null) {
            return false;
        }
        Team killerTeam = manager.getTeamOf(killer.getUniqueId()).orElse(null);
        Team victimTeam = manager.getTeamOf(victim.getUniqueId()).orElse(null);
        TeamRelation relation = manager.getRelation(killerTeam, victimTeam);
        return relation == TeamRelation.SELF || relation == TeamRelation.ALLY;
    }
}
