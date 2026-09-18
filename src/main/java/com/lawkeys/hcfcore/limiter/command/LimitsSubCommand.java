package com.lawkeys.hcfcore.limiter.command;

import com.lawkeys.hcfcore.limiter.LimiterModule;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.team.command.TeamSubCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** {@code /team limits} - how many of each limited block your team's land holds, against the limit. */
public final class LimitsSubCommand extends TeamSubCommand {

    private final LimiterModule limiter;

    public LimitsSubCommand(LimiterModule limiter) {
        super("limits", Set.of("blocks"), null, "", "How many limited blocks your land holds", true, 0);
        this.limiter = Objects.requireNonNull(limiter, "limiter");
    }

    @Override
    public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
        Optional<Team> team = requireTeam(module, sender, (Player) sender);
        if (team.isEmpty()) {
            return;
        }
        if (!limiter.getClaimBlocks().isActive()) {
            limiter.getLang().send(sender, "limiter.claim-blocks.none");
            return;
        }
        limiter.getLang().send(sender, "limiter.claim-blocks.header", "team", team.get().getName());
        Map<String, Integer> limits = limiter.getClaimBlocks().limits();
        limiter.usage(team.get()).forEach((material, count) ->
                limiter.getLang().send(sender, "limiter.claim-blocks.entry",
                        "block", LimiterModule.display(material), "count", String.valueOf(count),
                        "limit", String.valueOf(limits.getOrDefault(material, 0))));
    }
}
