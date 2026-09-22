package com.lawkeys.hcfcore.events.setup;

import com.lawkeys.hcfcore.claim.ClaimArea;
import com.lawkeys.hcfcore.claim.ClaimManager;
import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.claim.wand.Selection;
import com.lawkeys.hcfcore.claim.wand.TeamClaimTask;
import com.lawkeys.hcfcore.claim.wand.WandTask;
import com.lawkeys.hcfcore.events.EventIds;
import com.lawkeys.hcfcore.events.EventModule;
import com.lawkeys.hcfcore.events.core.CoreEventDefinition;
import com.lawkeys.hcfcore.events.totem.TotemDefinition;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.team.SystemZone;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamResult;
import com.lawkeys.hcfcore.util.Cuboid;
import com.lawkeys.hcfcore.util.LegacyText;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The staff side of every event: {@code /events create|delete|claim|unclaim|setzone|
 * delzone|setblock|info}, the same verbs whatever the kind of event (the project
 * owner's choice, 22/09/2026 - one way of doing things, so staff are not lost).
 *
 * <p>An event is set up in up to three parts, and {@code /events info <id>} shows
 * which are done:
 * <ul>
 *   <li>its <b>territory</b> - server land, a system team's claims, drawn with the
 *       claiming wand like any other claim ({@code /events claim}). Nobody builds
 *       there, and {@code /team map} shows it. The team is named in the event's
 *       {@code claim} key, and made by {@code /events create};</li>
 *   <li>its <b>zone</b> - what is held, stood in or broken inside, with heights
 *       ({@code /events setzone}); a Conquest has several;</li>
 *   <li>its <b>block</b> - a DTC's or Last Break's core, a Totem's column
 *       ({@code /events setblock}).</li>
 * </ul>
 * {@code /events create} does all three at once where the staff member stands: the
 * rest only moves them. Kill the King has none of them: its ground is the warzone.
 *
 * <p>These are the only commands in the plugin that write a configuration file
 * ({@link EventYamlStore}).
 */
public final class EventSetup {

    /** The verbs this class answers, in the order tab completion offers them. */
    public static final List<String> VERBS =
            List.of("create", "info", "claim", "unclaim", "setzone", "delzone", "setblock", "delete");

    /** A zone check walks every column; past this many it samples the corners only. */
    private static final long MAX_COLUMNS_CHECKED = 250_000;

    private final EventModule module;

    public EventSetup(EventModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    /** An event as {@code events.yml} holds it. */
    private record Found(EventKind kind, String section, String key, ConfigurationSection entry) {
    }

    private LangManager lang() {
        return module.getLang();
    }

    /** @return {@code false} to have Paper print the usage */
    public boolean handle(CommandSender sender, String verb, String[] args) {
        if (!sender.hasPermission(EventModule.ADMIN_PERMISSION)) {
            lang().send(sender, "general.no-permission");
            return true;
        }
        return switch (verb) {
            case "create" -> create(sender, args);
            case "info" -> info(sender, args);
            case "claim" -> claim(sender, args);
            case "unclaim" -> unclaim(sender, args);
            case "setzone" -> setZone(sender, args);
            case "delzone" -> deleteZone(sender, args);
            case "setblock" -> setBlock(sender, args);
            case "delete" -> delete(sender, args);
            default -> false;
        };
    }

    // ------------------------------------------------------------------
    // create
    // ------------------------------------------------------------------

    private boolean create(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender, "/events create <" + String.join("|", EventKind.typeNames()) + "> <id>");
            return true;
        }
        Player player = inGame(sender);
        if (player == null) {
            return true;
        }
        Optional<EventKind> kind = EventKind.fromTypeName(args[1]);
        if (kind.isEmpty()) {
            lang().send(player, EventSetupMessages.UNKNOWN_TYPE, "type", args[1],
                    "types", String.join(", ", EventKind.typeNames()));
            return true;
        }
        if (!validId(player, args[2])) {
            return true;
        }
        String id = EventIds.normalize(args[2]);
        Optional<YamlConfiguration> yaml = EventYamlStore.read(module.getPlugin());
        if (yaml.isEmpty()) {
            lang().send(player, EventSetupMessages.WRITE_FAILED);
            return true;
        }
        if (find(yaml.get(), id).isPresent() || isLoaded(id)) {
            lang().send(player, EventSetupMessages.ALREADY_EXISTS, "event", id);
            return true;
        }
        Optional<Map<String, Object>> template = EventYamlStore.template(module.getPlugin(), kind.get());
        if (template.isEmpty()) {
            lang().send(player, EventSetupMessages.NO_TEMPLATE, "type", kind.get().typeName());
            return true;
        }
        Location at = player.getLocation();
        String world = at.getWorld().getName();
        Map<String, Object> entry = EventTemplate.place(template.get(), id, world,
                at.getBlockX(), at.getBlockY(), at.getBlockZ());

        Optional<Team> territory = Optional.empty();
        if (kind.get().hasTerritory()) {
            territory = territoryTeam(player, id, null, true);
            territory.ifPresent(team -> entry.put("claim", team.getName()));
            if (territory.isEmpty()) {
                entry.remove("claim");
            }
        }

        boolean written = EventYamlStore.edit(module.getPlugin(), root -> {
            ConfigurationSection parent = root.getConfigurationSection(kind.get().section());
            if (parent == null) {
                parent = root.createSection(kind.get().section());
            }
            if (parent.contains(id)) {
                return false;
            }
            parent.createSection(id, entry);
            return true;
        });
        if (!written) {
            lang().send(player, EventSetupMessages.WRITE_FAILED);
            return true;
        }
        module.reloadSettings();
        lang().send(player, EventSetupMessages.CREATED, "event", id, "type", kind.get().typeName());

        if (territory.isPresent() && module.isSetupAutoClaim() && claimsRunning()) {
            Team land = territory.get();
            EventTemplate.bounds(entry).ifPresent(bounds -> autoClaim(player, id, land, world, bounds));
        }
        showInfo(player, id);
        return true;
    }

    /** Claims the land under the new event's zones, and a margin around them. */
    private void autoClaim(Player player, String id, Team team, String world, int[] bounds) {
        int margin = module.getSetupClaimMargin();
        TeamResult result = module.getClaims().getManager().claim(team, null, world,
                bounds[0] - margin, bounds[1] - margin, bounds[2] + margin, bounds[3] + margin);
        if (result.isSuccess()) {
            lang().send(player, EventSetupMessages.AUTO_CLAIMED, "event", id, "team", team.getName(),
                    "size", (bounds[2] - bounds[0] + 1 + 2 * margin) + "x" + (bounds[3] - bounds[1] + 1 + 2 * margin));
        } else {
            lang().send(player, EventSetupMessages.AUTO_CLAIM_REFUSED, "event", id, "reason", render(result));
        }
    }

    // ------------------------------------------------------------------
    // Territory: claim / unclaim
    // ------------------------------------------------------------------

    /** {@code /events claim <id>} - the claiming wand, drawing the event's territory. */
    private boolean claim(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/events claim <id>");
            return true;
        }
        Player player = inGame(sender);
        Optional<Found> found = player == null ? Optional.empty() : existing(player, args[1]);
        if (found.isEmpty()) {
            return true;
        }
        String id = found.get().key();
        if (!found.get().kind().hasTerritory()) {
            lang().send(player, EventSetupMessages.TERRITORY_NOT_APPLICABLE, "event", id);
            return true;
        }
        if (!claimsRunning()) {
            lang().send(player, EventSetupMessages.NO_WAND);
            return true;
        }
        String named = found.get().entry().getString("claim", "");
        Optional<Team> team = territoryTeam(player, id, named, true);
        if (team.isEmpty()) {
            return true;
        }
        if (!team.get().getName().equals(named)) {
            // The team was just made, or the key named none: the event must name it.
            Found event = found.get();
            if (!EventYamlStore.edit(module.getPlugin(), root -> {
                ConfigurationSection entry = entryOf(root, event.section(), event.key());
                if (entry == null) {
                    return false;
                }
                entry.set("claim", team.get().getName());
                return true;
            })) {
                lang().send(player, EventSetupMessages.WRITE_FAILED);
                return true;
            }
            module.reloadSettings();
        }
        module.getClaims().getWandSessions().give(player, new TeamClaimTask(module.getClaims(), team.get().getId(), true));
        return true;
    }

    /** {@code /events unclaim <id> [all]} - the event's claim you stand in, or all its land. */
    private boolean unclaim(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/events unclaim <id> [all]");
            return true;
        }
        Optional<Found> found = existing(sender, args[1]);
        if (found.isEmpty()) {
            return true;
        }
        String id = found.get().key();
        if (!claimsRunning()) {
            lang().send(sender, EventSetupMessages.NO_WAND);
            return true;
        }
        Optional<Team> team = territoryTeam(sender, id, found.get().entry().getString("claim", ""), false);
        if (team.isEmpty()) {
            lang().send(sender, EventSetupMessages.TERRITORY_NONE, "event", id);
            return true;
        }
        ClaimManager claims = module.getClaims().getManager();
        if (args.length > 2 && args[2].equalsIgnoreCase("all")) {
            TeamResult result = claims.unclaimAll(team.get(), null);
            if (!result.isSuccess()) {
                sendResult(sender, result);
                return true;
            }
            lang().send(sender, EventSetupMessages.UNCLAIMED_ALL, "event", id, "team", team.get().getName(),
                    "count", result.getPlaceholders().getOrDefault("count", "0"));
            return true;
        }
        Player player = inGame(sender);
        if (player == null) {
            return true;
        }
        Location at = player.getLocation();
        TeamResult result = claims.unclaim(team.get(), null, at.getWorld().getName(), at.getBlockX(), at.getBlockZ());
        if (!result.isSuccess()) {
            sendResult(player, result);
            return true;
        }
        lang().send(player, EventSetupMessages.UNCLAIMED, "event", id,
                "claim", result.getPlaceholders().getOrDefault("claim", ""));
        return true;
    }

    /**
     * @param named  the event's {@code claim} key - blank for none, in which case
     *               the team takes the event's id as its name
     * @param create whether to make the team when there is none yet
     * @return the system team whose land is the event's territory; empty, after
     *         saying why when that is news, when there is none
     */
    private Optional<Team> territoryTeam(CommandSender sender, String id, String named, boolean create) {
        String name = named == null || named.isBlank()
                ? TerritoryNames.forEvent(id, module.getTeams().getSettings().names().maxLength())
                : named.trim();
        var teams = module.getTeams().getManager();
        Optional<Team> existing = teams.getTeamByName(name);
        if (existing.isPresent()) {
            if (existing.get().getType().isSystem()) {
                return existing;
            }
            lang().send(sender, EventSetupMessages.TERRITORY_NAME_TAKEN, "event", id, "team", existing.get().getName());
            return Optional.empty();
        }
        if (!create) {
            return Optional.empty();
        }
        TeamResult result = teams.createSystemTeam(name, SystemZone.COMBAT);
        if (!result.isSuccess()) {
            lang().send(sender, EventSetupMessages.TERRITORY_TEAM_FAILED, "event", id, "reason", render(result));
            return Optional.empty();
        }
        lang().send(sender, EventSetupMessages.TERRITORY_TEAM_CREATED, "event", id, "team", name);
        return teams.getTeamByName(name);
    }

    // ------------------------------------------------------------------
    // Zones: setzone / delzone
    // ------------------------------------------------------------------

    /** {@code /events setzone <id> [zone]} - the claiming wand, drawing the zone (a Conquest's named one). */
    private boolean setZone(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/events setzone <id> [zone]");
            return true;
        }
        Player player = inGame(sender);
        Optional<Found> found = player == null ? Optional.empty() : existing(player, args[1]);
        if (found.isEmpty()) {
            return true;
        }
        String id = found.get().key();
        EventKind kind = found.get().kind();
        String zone = null;
        switch (kind.zone()) {
            case NONE -> {
                lang().send(player, EventSetupMessages.ZONE_NOT_APPLICABLE, "event", id);
                return true;
            }
            case MANY -> {
                if (args.length < 3) {
                    lang().send(player, EventSetupMessages.ZONE_NAME_NEEDED, "event", id,
                            "zones", String.join(", ", zoneNames(found.get())));
                    return true;
                }
                if (!EventIds.isValid(args[2])) {
                    lang().send(player, EventSetupMessages.ZONE_INVALID_NAME, "zone", args[2]);
                    return true;
                }
                zone = EventIds.normalize(args[2]);
            }
            case ONE -> {
                if (args.length > 2) {
                    lang().send(player, EventSetupMessages.ZONE_ONLY_ONE, "event", id);
                    return true;
                }
            }
        }
        if (isRunning(id)) {
            lang().send(player, EventSetupMessages.RUNNING, "event", id);
            return true;
        }
        if (!claimsRunning()) {
            lang().send(player, EventSetupMessages.NO_WAND);
            return true;
        }
        module.getClaims().getWandSessions().give(player, new ZoneTask(id, zone));
        return true;
    }

    /** {@code /events delzone <id> <zone>} - one of a Conquest's zones goes. */
    private boolean deleteZone(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender, "/events delzone <id> <zone>");
            return true;
        }
        Optional<Found> found = existing(sender, args[1]);
        if (found.isEmpty()) {
            return true;
        }
        String id = found.get().key();
        if (found.get().kind().zone() != EventKind.Zone.MANY) {
            lang().send(sender, EventSetupMessages.ZONE_ONLY_ONE, "event", id);
            return true;
        }
        List<String> zones = zoneNames(found.get());
        String zone = zones.stream().filter(name -> name.equalsIgnoreCase(args[2])).findFirst().orElse(null);
        if (zone == null) {
            lang().send(sender, EventSetupMessages.ZONE_UNKNOWN, "event", id, "zone", args[2],
                    "zones", String.join(", ", zones));
            return true;
        }
        if (zones.size() <= 1) {
            lang().send(sender, EventSetupMessages.ZONE_LAST, "event", id);
            return true;
        }
        if (isRunning(id)) {
            lang().send(sender, EventSetupMessages.RUNNING, "event", id);
            return true;
        }
        Found event = found.get();
        if (!EventYamlStore.edit(module.getPlugin(), root -> {
            ConfigurationSection entry = entryOf(root, event.section(), event.key());
            ConfigurationSection zoneSection = entry == null ? null : entry.getConfigurationSection("zones");
            if (zoneSection == null) {
                return false;
            }
            zoneSection.set(zone, null);
            return true;
        })) {
            lang().send(sender, EventSetupMessages.WRITE_FAILED);
            return true;
        }
        module.reloadSettings();
        lang().send(sender, EventSetupMessages.ZONE_DELETED, "event", id, "zone", zone);
        return true;
    }

    /** The wand drawing an event's zone - or, with a name, one of a Conquest's. */
    private final class ZoneTask implements WandTask {

        private final String id;
        /** A Conquest's zone; {@code null} for the event's own. */
        private final String zone;

        ZoneTask(String id, String zone) {
            this.id = id;
            this.zone = zone;
        }

        private String name() {
            return zone == null ? id : id + " " + zone;
        }

        @Override
        public String label() {
            return name();
        }

        @Override
        public List<String> preview(Player player, Selection selection) {
            List<String> lines = new ArrayList<>();
            lines.add(lang().get(EventSetupMessages.ZONE_PREVIEW, "event", name(),
                    "size", selection.width() + "x" + selection.length(),
                    "from", String.valueOf(selection.minY()),
                    "to", String.valueOf(selection.maxY() + module.getSetupZoneHeight())));
            if (!insideTerritory(id, selection.world(), selection.minX(), selection.minZ(),
                    selection.maxX(), selection.maxZ())) {
                lines.add(lang().get(EventSetupMessages.ZONE_PREVIEW_OUTSIDE, "event", id));
            }
            return lines;
        }

        @Override
        public boolean confirm(Player player, Selection selection) {
            Optional<Found> found = EventYamlStore.read(module.getPlugin()).flatMap(yaml -> find(yaml, id));
            if (found.isEmpty()) {
                lang().send(player, EventSetupMessages.UNKNOWN_ID, "event", id);
                return true;
            }
            if (isRunning(id)) {
                lang().send(player, EventSetupMessages.RUNNING, "event", id);
                return false;
            }
            int top = selection.maxY() + module.getSetupZoneHeight();
            Found event = found.get();
            boolean written = EventYamlStore.edit(module.getPlugin(), root -> {
                ConfigurationSection entry = entryOf(root, event.section(), event.key());
                if (entry == null) {
                    return false;
                }
                entry.set("world", selection.world());
                ConfigurationSection target = entry;
                if (zone != null) {
                    ConfigurationSection zones = entry.getConfigurationSection("zones");
                    if (zones == null) {
                        zones = entry.createSection("zones");
                    }
                    target = zones.getConfigurationSection(zone);
                    if (target == null) {
                        target = zones.createSection(zone);
                        target.set("display-name", "{primary}" + zone);
                    }
                }
                setPosition(target, "corner-1", selection.minX(), selection.minY(), selection.minZ());
                setPosition(target, "corner-2", selection.maxX(), top, selection.maxZ());
                return true;
            });
            if (!written) {
                lang().send(player, EventSetupMessages.WRITE_FAILED);
                return false;
            }
            module.reloadSettings();
            lang().send(player, EventSetupMessages.ZONE_DRAWN, "event", name(),
                    "size", selection.width() + "x" + selection.length());
            if (!insideTerritory(id, selection.world(), selection.minX(), selection.minZ(),
                    selection.maxX(), selection.maxZ())) {
                lang().send(player, EventSetupMessages.ZONE_OUTSIDE_TERRITORY, "event", id);
            }
            return true;
        }
    }

    // ------------------------------------------------------------------
    // setblock
    // ------------------------------------------------------------------

    /** {@code /events setblock <id>} - a DTC's core, or a Totem's column, on the block looked at. */
    private boolean setBlock(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/events setblock <id>");
            return true;
        }
        Player player = inGame(sender);
        Optional<Found> found = player == null ? Optional.empty() : existing(player, args[1]);
        if (found.isEmpty()) {
            return true;
        }
        String id = found.get().key();
        if (!found.get().kind().hasBlock()) {
            lang().send(player, EventSetupMessages.BLOCK_NOT_APPLICABLE, "event", id);
            return true;
        }
        if (isRunning(id)) {
            lang().send(player, EventSetupMessages.RUNNING, "event", id);
            return true;
        }
        Block target = player.getTargetBlockExact(module.getSetupTargetDistance());
        if (target == null) {
            lang().send(player, EventSetupMessages.NO_TARGET);
            return true;
        }
        return found.get().section().equals("totem")
                ? setColumn(player, found.get(), target)
                : setCore(player, found.get(), target);
    }

    private boolean setCore(Player player, Found event, Block target) {
        String id = event.key();
        Optional<CoreEventDefinition> definition = module.getCore() == null
                ? Optional.empty() : module.getCore().getSettings().find(id);
        if (definition.isEmpty()) {
            lang().send(player, EventSetupMessages.NOT_LOADED, "event", id);
            return true;
        }
        Cuboid zone = definition.get().zone();
        if (!zone.containsBlock(target.getWorld().getName(), target.getX(), target.getY(), target.getZ())) {
            lang().send(player, EventSetupMessages.BLOCK_OUTSIDE_ZONE, "event", id);
            return true;
        }
        boolean written = EventYamlStore.edit(module.getPlugin(), root -> {
            ConfigurationSection entry = entryOf(root, event.section(), id);
            if (entry == null) {
                return false;
            }
            ConfigurationSection core = entry.getConfigurationSection("core");
            if (core == null) {
                core = entry.createSection("core");
            }
            core.set("x", target.getX());
            core.set("y", target.getY());
            core.set("z", target.getZ());
            if (!core.contains("material")) {
                core.set("material", "OBSIDIAN");
            }
            return true;
        });
        if (!written) {
            lang().send(player, EventSetupMessages.WRITE_FAILED);
            return true;
        }
        module.reloadSettings();
        lang().send(player, EventSetupMessages.CORE_SET, "event", id);
        return true;
    }

    /** The old column goes; the new one is built at once, of the block it stands as between runs. */
    private boolean setColumn(Player player, Found event, Block target) {
        String id = event.key();
        Optional<TotemDefinition> definition = module.getTotem() == null
                ? Optional.empty() : module.getTotem().getSettings().find(id);
        if (definition.isEmpty()) {
            lang().send(player, EventSetupMessages.NOT_LOADED, "event", id);
            return true;
        }
        TotemDefinition old = definition.get();
        Cuboid zone = old.zone();
        String world = target.getWorld().getName();
        if (!zone.containsBlock(world, target.getX(), target.getY(), target.getZ())
                || !zone.containsBlock(world, target.getX(), target.getY() + old.height() - 1, target.getZ())) {
            lang().send(player, EventSetupMessages.BLOCK_OUTSIDE_ZONE, "event", id);
            return true;
        }
        boolean written = EventYamlStore.edit(module.getPlugin(), root -> {
            ConfigurationSection entry = entryOf(root, event.section(), id);
            if (entry == null) {
                return false;
            }
            setPosition(entry, "base", target.getX(), target.getY(), target.getZ());
            return true;
        });
        if (!written) {
            lang().send(player, EventSetupMessages.WRITE_FAILED);
            return true;
        }
        clearColumn(old);
        module.reloadSettings();
        module.getTotem().getSettings().find(id).ifPresent(now -> {
            Material idle = Material.matchMaterial(now.idleMaterial());
            World in = target.getWorld();
            for (int level = 0; level < now.height(); level++) {
                in.getBlockAt(now.baseX(), now.baseY() + level, now.baseZ()).setType(idle, false);
            }
            lang().send(player, EventSetupMessages.COLUMN_SET, "event", id, "height", String.valueOf(now.height()));
        });
        return true;
    }

    /** Removes a Totem's column where it stood, idle blocks only: whatever else is there stays. */
    private static void clearColumn(TotemDefinition totem) {
        World world = Bukkit.getWorld(totem.zone().world());
        if (world == null) {
            return;
        }
        for (int level = 0; level < totem.height(); level++) {
            Block block = world.getBlockAt(totem.baseX(), totem.baseY() + level, totem.baseZ());
            if (block.getType().name().equals(totem.idleMaterial())) {
                block.setType(Material.AIR, false);
            }
        }
    }

    // ------------------------------------------------------------------
    // delete
    // ------------------------------------------------------------------

    /**
     * {@code /events delete <id>} - the event goes from {@code events.yml}, a Totem's
     * column from the world, and its territory is released: the system team is
     * disbanded, unless another event names it too.
     */
    private boolean delete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/events delete <id>");
            return true;
        }
        Optional<Found> found = existing(sender, args[1]);
        if (found.isEmpty()) {
            return true;
        }
        Found event = found.get();
        String id = event.key();
        if (isRunning(id)) {
            lang().send(sender, EventSetupMessages.RUNNING, "event", id);
            return true;
        }
        String territory = event.entry().getString("claim", "");
        Optional<TotemDefinition> column = module.getTotem() == null
                ? Optional.empty() : module.getTotem().getSettings().find(id);
        boolean written = EventYamlStore.edit(module.getPlugin(), root -> {
            ConfigurationSection parent = root.getConfigurationSection(event.section());
            if (parent == null || !parent.contains(id)) {
                return false;
            }
            parent.set(id, null);
            return true;
        });
        if (!written) {
            lang().send(sender, EventSetupMessages.WRITE_FAILED);
            return true;
        }
        column.ifPresent(EventSetup::clearColumn);
        module.reloadSettings();
        lang().send(sender, EventSetupMessages.DELETED, "event", id);

        if (!territory.isBlank() && !namedByAnother(territory)) {
            Optional<Team> team = module.getTeams().getManager().getTeamByName(territory);
            if (team.isPresent() && team.get().getType().isSystem()
                    && module.getTeams().getManager().disband(team.get(), null).isSuccess()) {
                lang().send(sender, EventSetupMessages.DELETED_TERRITORY, "event", id, "team", team.get().getName());
            }
        }
        return true;
    }

    /** @return whether any event still in {@code events.yml} names this team as its territory */
    private boolean namedByAnother(String team) {
        Optional<YamlConfiguration> yaml = EventYamlStore.read(module.getPlugin());
        if (yaml.isEmpty()) {
            return true; // Unreadable: keep the land rather than guess.
        }
        for (String sectionName : EventKind.sections()) {
            ConfigurationSection section = yaml.get().getConfigurationSection(sectionName);
            if (section == null) {
                continue;
            }
            for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry != null && team.equalsIgnoreCase(entry.getString("claim", "").trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // info - the checklist
    // ------------------------------------------------------------------

    private boolean info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/events info <id>");
            return true;
        }
        if (existing(sender, args[1]).isPresent()) {
            showInfo(sender, EventIds.normalize(args[1]));
        }
        return true;
    }

    /** What an event has, what it lacks, and the command for each. */
    private void showInfo(CommandSender sender, String rawId) {
        Optional<Found> found = EventYamlStore.read(module.getPlugin()).flatMap(yaml -> find(yaml, rawId));
        if (found.isEmpty()) {
            return;
        }
        Found event = found.get();
        ConfigurationSection entry = event.entry();
        String id = event.key();
        String world = entry.getString("world", "world");
        lang().send(sender, EventSetupMessages.INFO_HEADER, "event", id,
                "name", entry.getString("display-name", id), "type", event.kind().typeName());
        if (!isLoaded(id)) {
            lang().send(sender, EventSetupMessages.INFO_NOT_LOADED, "event", id);
        }

        Optional<Team> territory = event.kind().hasTerritory()
                ? territoryTeam(sender, id, entry.getString("claim", ""), false) : Optional.empty();

        // Territory
        if (event.kind().hasTerritory()) {
            if (territory.isEmpty()) {
                lang().send(sender, EventSetupMessages.INFO_TERRITORY_NONE, "event", id);
            } else {
                ClaimManager claims = claimsRunning() ? module.getClaims().getManager() : null;
                int count = claims == null ? 0 : claims.getClaimCount(territory.get().getId());
                if (count == 0) {
                    lang().send(sender, EventSetupMessages.INFO_TERRITORY_EMPTY, "event", id,
                            "team", territory.get().getName());
                } else {
                    lang().send(sender, EventSetupMessages.INFO_TERRITORY, "event", id,
                            "team", territory.get().getName(), "claims", String.valueOf(count),
                            "area", String.valueOf(claims.getClaimedArea(territory.get().getId())));
                }
            }
        }

        // Zone(s)
        switch (event.kind().zone()) {
            case NONE -> {
                boolean hasWarzone = claimsRunning()
                        && module.getClaims().getSettings().warzone().areas().containsKey(world);
                lang().send(sender, hasWarzone ? EventSetupMessages.INFO_WARZONE : EventSetupMessages.INFO_NO_WARZONE,
                        "world", world);
            }
            case ONE -> zoneLine(sender, id, null, world, entry);
            case MANY -> {
                ConfigurationSection zones = entry.getConfigurationSection("zones");
                if (zones == null || zones.getKeys(false).isEmpty()) {
                    lang().send(sender, EventSetupMessages.INFO_NO_ZONE, "event", id);
                } else {
                    for (String zone : zones.getKeys(false)) {
                        zoneLine(sender, id, zone, world, zones.getConfigurationSection(zone));
                    }
                }
            }
        }

        // Block
        if (event.kind().hasBlock()) {
            if (event.section().equals("totem")) {
                columnLine(sender, id, world, entry);
            } else {
                ConfigurationSection core = entry.getConfigurationSection("core");
                if (core != null) {
                    lang().send(sender, EventSetupMessages.INFO_CORE, "event", id,
                            "x", String.valueOf(core.getInt("x")), "y", String.valueOf(core.getInt("y")),
                            "z", String.valueOf(core.getInt("z")), "material", core.getString("material", "OBSIDIAN"));
                }
            }
        }

        // Schedule and state
        List<String> schedule = entry.getStringList("schedule");
        if (schedule.isEmpty()) {
            lang().send(sender, EventSetupMessages.INFO_SCHEDULE_NONE, "event", id);
        } else {
            lang().send(sender, EventSetupMessages.INFO_SCHEDULE, "event", id, "times", String.join(", ", schedule));
        }
        lang().send(sender, isRunning(id) ? EventSetupMessages.INFO_RUNNING : EventSetupMessages.INFO_IDLE, "event", id);
    }

    private void zoneLine(CommandSender sender, String id, String zone, String world, ConfigurationSection section) {
        ConfigurationSection first = section == null ? null : section.getConfigurationSection("corner-1");
        ConfigurationSection second = section == null ? null : section.getConfigurationSection("corner-2");
        if (first == null || second == null) {
            lang().send(sender, EventSetupMessages.INFO_NO_ZONE, "event", id);
            return;
        }
        int minX = Math.min(first.getInt("x"), second.getInt("x"));
        int maxX = Math.max(first.getInt("x"), second.getInt("x"));
        int minZ = Math.min(first.getInt("z"), second.getInt("z"));
        int maxZ = Math.max(first.getInt("z"), second.getInt("z"));
        String size = (maxX - minX + 1) + "x" + (maxZ - minZ + 1);
        String centre = Math.floorDiv(minX + maxX, 2) + ", " + Math.floorDiv(minZ + maxZ, 2);
        String from = String.valueOf(Math.min(first.getInt("y"), second.getInt("y")));
        String to = String.valueOf(Math.max(first.getInt("y"), second.getInt("y")));
        if (zone == null) {
            lang().send(sender, EventSetupMessages.INFO_ZONE, "event", id,
                    "size", size, "at", centre, "from", from, "to", to);
        } else {
            lang().send(sender, EventSetupMessages.INFO_ZONE_NAMED, "event", id, "zone", zone,
                    "size", size, "at", centre, "from", from, "to", to);
        }
        if (!insideTerritory(id, world, minX, minZ, maxX, maxZ)) {
            lang().send(sender, EventSetupMessages.INFO_ZONE_OUTSIDE, "event", id,
                    "zone", zone == null ? id : zone);
        }
    }

    private void columnLine(CommandSender sender, String id, String world, ConfigurationSection entry) {
        ConfigurationSection base = entry.getConfigurationSection("base");
        if (base == null) {
            return;
        }
        int x = base.getInt("x");
        int y = base.getInt("y");
        int z = base.getInt("z");
        World in = Bukkit.getWorld(world);
        Material idle = Material.matchMaterial(entry.getString("idle-material", "BEDROCK"));
        Material active = Material.matchMaterial(entry.getString("material", "QUARTZ_BLOCK"));
        Material broken = Material.matchMaterial(entry.getString("broken-material", "BEDROCK"));
        Material there = in == null ? null : in.getBlockAt(x, y, z).getType();
        boolean built = there != null && (there == idle || there == active || there == broken);
        lang().send(sender, built ? EventSetupMessages.INFO_COLUMN : EventSetupMessages.INFO_COLUMN_MISSING,
                "event", id, "height", String.valueOf(entry.getInt("height", 5)),
                "x", String.valueOf(x), "y", String.valueOf(y), "z", String.valueOf(z));
    }

    /**
     * @return whether every column of the rectangle is the event's territory - also
     *         {@code true} when there is no territory to compare with, which
     *         {@code /events info} reports on its own line
     */
    private boolean insideTerritory(String id, String world, int minX, int minZ, int maxX, int maxZ) {
        if (!claimsRunning()) {
            return true;
        }
        Optional<Found> found = EventYamlStore.read(module.getPlugin()).flatMap(yaml -> find(yaml, id));
        if (found.isEmpty() || !found.get().kind().hasTerritory()) {
            return true;
        }
        String named = found.get().entry().getString("claim", "");
        Optional<Team> team = named.isBlank()
                ? module.getTeams().getManager().getTeamByName(
                        TerritoryNames.forEvent(id, module.getTeams().getSettings().names().maxLength()))
                : module.getTeams().getManager().getTeamByName(named.trim());
        if (team.isEmpty()) {
            return true;
        }
        ClaimManager claims = module.getClaims().getManager();
        long columns = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
        if (columns > MAX_COLUMNS_CHECKED) {
            int[][] corners = {{minX, minZ}, {minX, maxZ}, {maxX, minZ}, {maxX, maxZ}};
            for (int[] corner : corners) {
                if (!ownedBy(claims, team.get(), world, corner[0], corner[1])) {
                    return false;
                }
            }
            return true;
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!ownedBy(claims, team.get(), world, x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean ownedBy(ClaimManager claims, Team team, String world, int x, int z) {
        return claims.getClaimAt(world, x, z).map(ClaimArea::teamId).filter(team.getId()::equals).isPresent();
    }

    // ------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------

    /** @return the event named {@code id} in any section of {@code events.yml}, case-insensitively */
    private static Optional<Found> find(YamlConfiguration yaml, String id) {
        for (String sectionName : EventKind.sections()) {
            ConfigurationSection section = yaml.getConfigurationSection(sectionName);
            if (section == null) {
                continue;
            }
            for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry != null && key.equalsIgnoreCase(id)) {
                    return EventKind.fromSection(sectionName).map(kind -> new Found(kind, sectionName, key, entry));
                }
            }
        }
        return Optional.empty();
    }

    /** @return the event, after saying what is wrong when there is none */
    private Optional<Found> existing(CommandSender sender, String rawId) {
        if (!validId(sender, rawId)) {
            return Optional.empty();
        }
        Optional<YamlConfiguration> yaml = EventYamlStore.read(module.getPlugin());
        if (yaml.isEmpty()) {
            lang().send(sender, EventSetupMessages.WRITE_FAILED);
            return Optional.empty();
        }
        Optional<Found> found = find(yaml.get(), rawId);
        if (found.isEmpty()) {
            lang().send(sender, EventSetupMessages.UNKNOWN_ID, "event", rawId);
        }
        return found;
    }

    /** @return every event id in {@code events.yml}, for tab completion */
    public List<String> ids() {
        List<String> ids = new ArrayList<>();
        EventYamlStore.read(module.getPlugin()).ifPresent(yaml -> {
            for (String sectionName : EventKind.sections()) {
                ConfigurationSection section = yaml.getConfigurationSection(sectionName);
                if (section != null) {
                    for (String key : section.getKeys(false)) {
                        if (section.isConfigurationSection(key)) {
                            ids.add(key);
                        }
                    }
                }
            }
        });
        return ids;
    }

    /** @return the names of a Conquest's zones, for tab completion and messages */
    public List<String> zoneNames(String id) {
        return EventYamlStore.read(module.getPlugin()).flatMap(yaml -> find(yaml, id))
                .map(this::zoneNames).orElse(List.of());
    }

    private List<String> zoneNames(Found event) {
        ConfigurationSection zones = event.entry().getConfigurationSection("zones");
        return zones == null ? List.of() : new ArrayList<>(zones.getKeys(false));
    }

    /** @return whether an event by this id was loaded, by whichever engine runs its kind */
    private boolean isLoaded(String id) {
        if (module.getSettings().find(id).isPresent()) {
            return true;
        }
        if (module.getKing() != null && module.getKing().getSettings().find(id).isPresent()) {
            return true;
        }
        if (module.getConquest() != null && module.getConquest().getSettings().find(id).isPresent()) {
            return true;
        }
        if (module.getCore() != null && module.getCore().getSettings().find(id).isPresent()) {
            return true;
        }
        if (module.getTotem() != null && module.getTotem().getSettings().find(id).isPresent()) {
            return true;
        }
        return module.getSlide() != null && module.getSlide().getSettings().find(id).isPresent();
    }

    private boolean isRunning(String id) {
        return module.isRunning(id);
    }

    private boolean claimsRunning() {
        ClaimModule claims = module.getClaims();
        return claims != null && claims.getManager() != null;
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private boolean validId(CommandSender sender, String raw) {
        if (EventIds.isValid(raw)) {
            return true;
        }
        lang().send(sender, EventSetupMessages.INVALID_ID, "id", raw,
                "min", String.valueOf(EventIds.MIN_LENGTH), "max", String.valueOf(EventIds.MAX_LENGTH));
        return false;
    }

    /** @return the sender as a player, or {@code null} after saying the verb needs one */
    private Player inGame(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        lang().send(sender, EventSetupMessages.IN_GAME_ONLY);
        return null;
    }

    private void usage(CommandSender sender, String usage) {
        lang().send(sender, EventSetupMessages.USAGE, "usage", usage);
    }

    private String render(TeamResult result) {
        return lang().get(result.getMessageKey(), module.getTeams().readable(result.getPlaceholders()));
    }

    private void sendResult(CommandSender sender, TeamResult result) {
        sender.sendMessage(LegacyText.SERIALIZER.deserialize(render(result)));
    }

    private static void setPosition(ConfigurationSection parent, String key, int x, int y, int z) {
        ConfigurationSection position = parent.createSection(key);
        position.set("x", x);
        position.set("y", y);
        position.set("z", z);
    }

    /** @return the {@code id}'s own section under {@code sectionName}, or {@code null} if it vanished */
    private static ConfigurationSection entryOf(ConfigurationSection root, String sectionName, String id) {
        ConfigurationSection parent = root.getConfigurationSection(sectionName);
        if (parent == null) {
            return null;
        }
        for (String key : parent.getKeys(false)) {
            if (key.equalsIgnoreCase(id)) {
                return parent.getConfigurationSection(key);
            }
        }
        return null;
    }

    /** @return the completions for a setup verb's arguments */
    public List<String> complete(String verb, String[] args) {
        if (args.length == 2) {
            return verb.equals("create") ? EventKind.typeNames() : ids();
        }
        if (args.length == 3) {
            return switch (verb.toLowerCase(Locale.ROOT)) {
                case "setzone", "delzone" -> zoneNames(args[1]);
                case "unclaim" -> List.of("all");
                default -> List.of();
            };
        }
        return List.of();
    }
}
