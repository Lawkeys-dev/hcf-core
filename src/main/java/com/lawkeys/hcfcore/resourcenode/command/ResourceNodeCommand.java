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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label,
                                      String[] args) {
        if (!sender.hasPermission(ResourceNodeModule.ADMIN_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return prefixed(List.of("refill"), args[0]);
        }
        if (args.length == 2) {
            List<String> ids = new ArrayList<>();
            for (ResourceNodeDefinition node : module.getSettings().nodes()) {
                ids.add(node.id());
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
