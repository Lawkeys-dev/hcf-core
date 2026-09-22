package com.lawkeys.hcfcore.claim.wand;

import com.lawkeys.hcfcore.claim.ClaimManager;
import com.lawkeys.hcfcore.claim.ClaimMessages;
import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamResult;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The wand drawing a team's claim: {@code /team claim} for its members, paid from
 * its bank, or {@code /team forceclaim <team>} for staff - server land, or any team's
 * land drawn for it - free and outside the size rules, but never over anybody's land.
 */
public final class TeamClaimTask implements WandTask {

    private final ClaimModule claims;
    private final UUID teamId;
    private final boolean staff;

    /** @param staff drawn by staff for the team: free, and outside the size rules */
    public TeamClaimTask(ClaimModule claims, UUID teamId, boolean staff) {
        this.claims = Objects.requireNonNull(claims, "claims");
        this.teamId = Objects.requireNonNull(teamId, "teamId");
        this.staff = staff;
    }

    @Override
    public String label() {
        return team().map(Team::getName).orElse("?");
    }

    private Optional<Team> team() {
        ClaimManager manager = claims.getManager();
        return manager == null ? Optional.empty() : claims.getTeams().getManager().getTeam(teamId);
    }

    private UUID actor(Player player) {
        return staff ? null : player.getUniqueId();
    }

    @Override
    public List<String> preview(Player player, Selection selection) {
        Optional<Team> team = team();
        if (team.isEmpty()) {
            return List.of(claims.getLang().get(ClaimMessages.NOT_IN_TEAM));
        }
        Optional<TeamResult> refused = claims.getManager().refusal(team.get(), actor(player), selection.world(),
                selection.minX(), selection.minZ(), selection.maxX(), selection.maxZ());
        if (refused.isPresent()) {
            return List.of(render(refused.get()));
        }
        double price = claims.getManager().priceOf(team.get(), actor(player), selection.area());
        if (price <= 0) {
            return List.of(claims.getLang().get(ClaimMessages.WAND_FREE));
        }
        var teams = claims.getTeams().getManager();
        return List.of(claims.getLang().get(
                team.get().getBalance() >= price ? ClaimMessages.WAND_COST : ClaimMessages.WAND_COST_SHORT,
                "cost", teams.formatAmount(price), "balance", teams.formatAmount(team.get().getBalance())));
    }

    @Override
    public boolean confirm(Player player, Selection selection) {
        Optional<Team> team = team();
        if (team.isEmpty()) {
            claims.getLang().send(player, ClaimMessages.NOT_IN_TEAM);
            return true;
        }
        TeamResult result = claims.getManager().claim(team.get(), actor(player), selection.world(),
                selection.first().x(), selection.first().z(), selection.second().x(), selection.second().z());
        player.sendMessage(com.lawkeys.hcfcore.util.LegacyText.SERIALIZER.deserialize(render(result)));
        return result.isSuccess();
    }

    private String render(TeamResult result) {
        return claims.getLang().get(result.getMessageKey(),
                claims.getTeams().readable(result.getPlaceholders()));
    }
}
