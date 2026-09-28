package com.lawkeys.hcfcore.resourcenode.command;

import com.lawkeys.hcfcore.events.AgendaEntry;
import com.lawkeys.hcfcore.resourcenode.ResourceNodeDefinition;
import com.lawkeys.hcfcore.resourcenode.ResourceNodeManager;
import com.lawkeys.hcfcore.resourcenode.ResourceNodeMessages;
import com.lawkeys.hcfcore.resourcenode.ResourceNodeModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code /resourcenode} - what refills when, and the staff override.
 *
 * <p>Separate from {@code /events} on purpose. The read-only agenda is shared,
 * because players want one place to look; the verbs are not, because "start a
 * KOTH" and "refill a mountain" are different acts on different engines
 * (ARCHITECTURE.md section 9).
 *
 * <p>Parses arguments and renders; every rule lives in {@link ResourceNodeManager}.
 */
public final class ResourceNodeCommand implements TabExecutor {

    private final ResourceNodeModule module;

    public ResourceNodeCommand(ResourceNodeModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (module.getManager() == null || !module.getSettings().enabled()) {
            module.getLang().send(sender, ResourceNodeMessages.DISABLED);
            return true;
        }
        if (args.length > 0 && args[0].toLowerCase(Locale.ROOT).equals("refill")) {
            return refill(sender, args);
        }
        if (args.length > 0 && args[0].toLowerCase(Locale.ROOT).equals("claim")) {
            return claim(sender, args);
        }
        list(sender);
        return true;
    }

    private void list(CommandSender sender) {
        List<ResourceNodeDefinition> nodes = module.getSettings().nodes();
        if (nodes.isEmpty()) {
            module.getLang().send(sender, ResourceNodeMessages.LIST_EMPTY);
            return;
        }
        module.getLang().send(sender, ResourceNodeMessages.LIST_HEADER);
        for (ResourceNodeDefinition node : nodes) {
            // The same line the /events agenda shows, from the same method: a node
            // described two different ways in two places is a bug waiting to happen.
            AgendaEntry entry = module.describe(node);
            module.getLang().send(sender, entry.messageKey(), entry.placeholders());
        }
    }

    private boolean refill(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ResourceNodeModule.ADMIN_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 2) {
            return false; // Paper prints the usage from plugin.yml.
        }

        String id = args[1];
        Optional<ResourceNodeDefinition> node = module.getSettings().find(id);
        if (node.isEmpty()) {
            module.getLang().send(sender, ResourceNodeMessages.UNKNOWN_NODE, "node", id);
            return true;
        }

        switch (module.refillNow(node.get())) {
            case STARTED -> module.getLang().send(sender, ResourceNodeMessages.ADMIN_REFILLING,
                    "node", node.get().displayName());
            case BUSY -> module.getLang().send(sender, ResourceNodeMessages.ADMIN_BUSY,
                    "node", node.get().displayName());
            case WORLD_MISSING -> module.getLang().send(sender, ResourceNodeMessages.WORLD_MISSING,
                    "node", node.get().displayName(), "world", node.get().region().world());
            case OUTSIDE_WORLD -> module.getLang().send(sender, ResourceNodeMessages.OUTSIDE_WORLD,
                    "node", node.get().displayName(), "world", node.get().region().world());
        }
        return true;
    }

    /**
     * {@code /resourcenode claim <id>} - a claimed Mountain's land, drawn with the
     * claiming wand for its server team, which is made (a combat zone) when missing.
     */
    private boolean claim(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ResourceNodeModule.ADMIN_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 2) {
            return false;
        }
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            module.getLang().send(sender, ResourceNodeMessages.CLAIM_IN_GAME);
            return true;
        }
        Optional<ResourceNodeDefinition> node = module.findConfigured(args[1]);
        if (node.isEmpty()) {
            module.getLang().send(sender, ResourceNodeMessages.UNKNOWN_NODE, "node", args[1]);
            return true;
        }
        if (!node.get().isClaimed()) {
            module.getLang().send(sender, ResourceNodeMessages.CLAIM_NOT_CLAIMED, "node", node.get().id());
            return true;
        }
        var claims = module.getClaims();
        if (claims.getManager() == null || claims.getWandSessions() == null || claims.getTeams() == null) {
            module.getLang().send(sender, ResourceNodeMessages.CLAIM_NO_WAND);
            return true;
        }
        var teams = claims.getTeams().getManager();
        String name = node.get().claim();
        var team = teams.getTeamByName(name);
        if (team.isPresent() && !team.get().getType().isSystem()) {
            module.getLang().send(sender, ResourceNodeMessages.CLAIM_TEAM_TAKEN, "node", node.get().displayName(),
                    "team", team.get().getName());
            return true;
        }
        if (team.isEmpty()) {
            var result = teams.createSystemTeam(name, com.lawkeys.hcfcore.team.SystemZone.COMBAT);
            if (!result.isSuccess()) {
                module.getLang().send(sender, ResourceNodeMessages.CLAIM_TEAM_FAILED, "team", name,
                        "reason", module.getLang().get(result.getMessageKey(), result.getPlaceholders()));
                return true;
            }
            module.getLang().send(sender, ResourceNodeMessages.CLAIM_TEAM_CREATED, "node", node.get().displayName(),
                    "team", name);
            team = teams.getTeamByName(name);
            if (team.isEmpty()) {
                return true;
            }
        }
        claims.getWandSessions().give(player,
                new com.lawkeys.hcfcore.claim.wand.TeamClaimTask(claims, team.get().getId(), true));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label,
                                      String[] args) {
        if (!sender.hasPermission(ResourceNodeModule.ADMIN_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return prefixed(List.of("refill", "claim"), args[0]);
        }
        if (args.length == 2) {
            List<String> ids = new ArrayList<>();
            boolean claiming = args[0].equalsIgnoreCase("claim");
            for (ResourceNodeDefinition node : module.getSettings().nodes()) {
                if (!claiming) {
                    ids.add(node.id());
                }
            }
            if (claiming) {
                module.configuredNodes().stream().filter(ResourceNodeDefinition::isClaimed)
                        .forEach(node -> ids.add(node.id()));
            }
            return prefixed(ids, args[1]);
        }
        return List.of();
    }

    private static List<String> prefixed(List<String> candidates, String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matches.add(candidate);
            }
        }
        return matches;
    }
}
