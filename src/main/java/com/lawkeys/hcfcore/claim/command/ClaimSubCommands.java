package com.lawkeys.hcfcore.claim.command;

import com.lawkeys.hcfcore.claim.ClaimManager;
import com.lawkeys.hcfcore.claim.ClaimMessages;
import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.claim.ClaimSettings;
import com.lawkeys.hcfcore.claim.HomeType;
import com.lawkeys.hcfcore.claim.TeamHome;
import com.lawkeys.hcfcore.claim.wand.TeamClaimTask;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamMessages;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.team.TeamRelation;
import com.lawkeys.hcfcore.team.TeamResult;
import com.lawkeys.hcfcore.team.command.TeamSubCommand;
import com.lawkeys.hcfcore.util.ChunkPosition;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * The territory subcommands grafted onto {@code /team}.
 *
 * <p>They live in the claim module and are registered through
 * {@link TeamModule#registerSubCommand}, so the team module has no knowledge of
 * territory. As everywhere else in the command layer, these only parse input and
 * render results - the rules are in {@code ClaimManager}.
 */
public final class ClaimSubCommands {

    /** Staff may claim and release land for any team - server land, typically. Declared in plugin.yml. */
    public static final String ADMIN_PERMISSION = "hcfcore.claim.admin";

    private ClaimSubCommands() {
    }

    /** @return the subcommands to graft onto {@code /team}, in help order */
    public static List<TeamSubCommand> all(ClaimModule claims) {
        Objects.requireNonNull(claims, "claims");
        return List.of(
                new Claim(claims),
                new Unclaim(claims),
                new UnclaimAll(claims),
                new Here(claims),
                new Map(claims),
                new SetHome(claims, HomeType.HQ, "sethq", "Set your team's HQ here"),
                new GoHome(claims, HomeType.HQ, "hq", "Teleport to your team's HQ"),
                new SetHome(claims, HomeType.BASE, "setbase", "Set your team's secondary base here"),
                new GoHome(claims, HomeType.BASE, "base", "Teleport to your team's secondary base"),
                new ForceClaim(claims),
                new ForceUnclaim(claims),
                new Stuck(claims),
                new LockClaim(claims));
    }

    /**
     * {@code /team stuck} - gets a player out of territory they cannot leave.
     *
     * <p><strong>Not a {@link ClaimSubCommand}</strong>, deliberately: that base
     * refuses anybody without a team, and a player with no team walled into
     * somebody else's claim is exactly who needs this most.
     *
     * <p>The search itself is in {@link com.lawkeys.hcfcore.claim.StuckSearch} and
     * is pure, so "which chunk should they land in" is decided and tested without a
     * server. Here only the warmup and the teleport remain.
     *
     * <p><strong>The warmup is the point.</strong> This is the one way out of a base
     * short of breaking it, so without a countdown it would be a free exit from any
     * trap and from any raid. Damage or leaving the block cancels it, like
     * {@code /spawn}; the search is run once to answer "not stuck" or "nowhere" at
     * once, and again at the end, since land can be claimed in the meantime.
     *
     * <p>The landing height is read after the destination chunk has loaded
     * asynchronously - {@code getHighestBlockYAt} on an unloaded chunk would load it
     * on the main thread, which CONTRIBUTING.md section 5 rules out.
     */
    private static final class Stuck extends TeamSubCommand {

        private final ClaimModule claims;

        Stuck(ClaimModule claims) {
            super("stuck", Set.of(), null, "",
                    "Teleport out of territory you cannot leave", true, 0);
            this.claims = claims;
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Player player = (Player) sender;
            ClaimManager manager = claims.getManager();
            if (manager == null || !manager.isEnforced()) {
                module.getLang().send(sender, ClaimMessages.CLAIM_DISABLED);
                return;
            }
            // A combat tag refuses this like any other plugin teleport: being stuck is
            // not a way out of a fight.
            if (claims.getTeleportGuard().blockTeleport(player.getUniqueId())) {
                return;
            }
            if (destinationFor(module, player).isEmpty()) {
                return;
            }
            long seconds = claims.getSettings().stuck().warmupSeconds();
            if (!claims.getWarmups().begin(player, "stuck", seconds, ClaimMessages.STUCK_CANCELLED,
                    moving -> finish(module, moving))) {
                module.getLang().send(sender, ClaimMessages.WARMUP_BUSY);
                return;
            }
            if (seconds > 0) {
                module.getLang().send(sender, ClaimMessages.STUCK_WARMUP, "time", String.valueOf(seconds));
            }
        }

        /** Runs when the countdown ends: everything is asked again, then the move. */
        private void finish(TeamModule module, Player player) {
            ClaimManager manager = claims.getManager();
            if (manager == null || !manager.isEnforced()) {
                module.getLang().send(player, ClaimMessages.CLAIM_DISABLED);
                return;
            }
            if (claims.getTeleportGuard().blockTeleport(player.getUniqueId())) {
                return;
            }
            Optional<ChunkPosition> destination = destinationFor(module, player);
            if (destination.isEmpty()) {
                return;
            }
            claims.moveTo(player, destination.get(),
                    () -> module.getLang().send(player, ClaimMessages.STUCK_MOVED));
        }

        /** @return the nearest land they may stand on, or empty after saying why there is none */
        private Optional<ChunkPosition> destinationFor(TeamModule module, Player player) {
            ChunkPosition from = ClaimModule.toChunk(player.getLocation());
            UUID teamId = module.getManager().getTeamOf(player.getUniqueId()).map(Team::getId).orElse(null);
            // Where they stand first: claims are block-precise, so the middle of their
            // chunk is not the block under their feet.
            org.bukkit.Location at = player.getLocation();
            Optional<Team> ownerHere = claims.ownerAt(at);
            boolean freeHere = ownerHere.isEmpty() ? !claims.isWarzoneAt(at) : ownerHere.get().getId().equals(teamId);
            if (freeHere) {
                module.getLang().send(player, ClaimMessages.STUCK_NOT_STUCK);
                return Optional.empty();
            }
            // Free land, or their own team's: both are places they may stand.
            Optional<ChunkPosition> destination = claims.nearestFreeLand(from, teamId);
            if (destination.isEmpty()) {
                module.getLang().send(player, ClaimMessages.STUCK_NOWHERE);
                return Optional.empty();
            }
            return destination;
        }
    }

    /**
     * Locks or unlocks the team's claim, during SOTW only - the project owner's rule
     * (12/09/2026), so nobody can camp in it until SOTW ends. Locking moves out, to
     * the nearest free land, every non-member already standing inside.
     */
    private static final class LockClaim extends ClaimSubCommand {

        LockClaim(ClaimModule claims) {
            super(claims, "lockclaim", Set.of("lock"), "", "Lock your claim to non-members until SOTW ends", 0);
        }

        @Override
        void run(TeamModule module, Player player, Team team, String[] args, String label) {
            TeamResult result = claims.getManager().toggleLock(team, player.getUniqueId());
            report(module, player, result);
            if (!result.isSuccess()) {
                return;
            }
            boolean locked = claims.getManager().isLocked(team.getId());
            module.broadcast(team, player.getUniqueId(),
                    locked ? ClaimMessages.LOCK_BROADCAST_LOCKED : ClaimMessages.LOCK_BROADCAST_UNLOCKED,
                    "player", player.getName());
            if (locked) {
                expelIntruders(module, team);
            }
        }

        private void expelIntruders(TeamModule module, Team team) {
            for (Player other : org.bukkit.Bukkit.getOnlinePlayers()) {
                org.bukkit.Location where = other.getLocation();
                ChunkPosition at = ClaimModule.toChunk(where);
                if (claims.getManager().lockedAgainst(where.getWorld().getName(), where.getBlockX(), where.getBlockZ(),
                        other.getUniqueId()).isEmpty()
                        || other.hasPermission(com.lawkeys.hcfcore.claim.listener.ClaimProtectionListener.BYPASS_PERMISSION)) {
                    continue;
                }
                claims.nearestFreeLand(at, module.getManager().getTeamOf(other.getUniqueId())
                                .map(Team::getId).orElse(null))
                        .ifPresent(destination -> claims.moveTo(other, destination, () -> module.getLang()
                                .send(other, ClaimMessages.LOCK_EXPELLED, "team", team.getName())));
            }
        }
    }

    /** Shared plumbing: every territory subcommand is player-only and needs a team. */
    private abstract static class ClaimSubCommand extends TeamSubCommand {

        protected final ClaimModule claims;

        ClaimSubCommand(ClaimModule claims, String name, Set<String> aliases, String usage,
                        String description, int minimumArgs) {
            super(name, aliases, null, usage, description, true, minimumArgs);
            this.claims = claims;
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Player player = (Player) sender;
            if (claims.getManager() == null) {
                // The claim module never started. This is not "still loading": the
                // manager exists before its load begins, and /team refuses every
                // subcommand until the loads have landed (StartupGate).
                module.getLang().send(sender, ClaimMessages.CLAIM_DISABLED);
                return;
            }
            Optional<Team> team = module.getManager().getTeamOf(player.getUniqueId());
            if (team.isEmpty() && needsTeam()) {
                module.getLang().send(sender, ClaimMessages.NOT_IN_TEAM);
                return;
            }
            run(module, player, team.orElse(null), args, label);
        }

        /**
         * @return whether only a team member may use it - {@code false} for what only
         *         reads the land, which a player looking for somewhere to found a team
         *         needs most
         */
        boolean needsTeam() {
            return true;
        }

        /** @param team the player's team; {@code null} only when {@link #needsTeam()} is false */
        abstract void run(TeamModule module, Player player, Team team, String[] args, String label);

        protected static void report(TeamModule module, CommandSender sender, TeamResult result) {
            module.getLang().send(sender, result.getMessageKey(), module.readable(result.getPlaceholders()));
        }
    }

    // ------------------------------------------------------------------

    /**
     * {@code /team claim} - hands over the claiming wand: the traditional HCF claim,
     * drawn block by block from two corners and paid from the team bank.
     */
    private static final class Claim extends ClaimSubCommand {

        Claim(ClaimModule claims) {
            super(claims, "claim", Set.of(), "", "Get the claiming wand", 0);
        }

        @Override
        void run(TeamModule module, Player player, Team team, String[] args, String label) {
            if (!claims.getManager().isEnforced()) {
                module.getLang().send(player, ClaimMessages.CLAIM_DISABLED);
                return;
            }
            claims.getWandSessions().give(player, new TeamClaimTask(claims, team.getId(), false));
        }
    }

    private static final class Unclaim extends ClaimSubCommand {

        Unclaim(ClaimModule claims) {
            super(claims, "unclaim", Set.of(), "", "Release the claim you are standing in", 0);
        }

        @Override
        void run(TeamModule module, Player player, Team team, String[] args, String label) {
            org.bukkit.Location at = player.getLocation();
            report(module, player, claims.getManager().unclaim(team, player.getUniqueId(),
                    at.getWorld().getName(), at.getBlockX(), at.getBlockZ()));
        }
    }

    private static final class UnclaimAll extends ClaimSubCommand {

        UnclaimAll(ClaimModule claims) {
            super(claims, "unclaimall", Set.of(), "", "Release all of your team's territory", 0);
        }

        @Override
        void run(TeamModule module, Player player, Team team, String[] args, String label) {
            report(module, player, claims.getManager().unclaimAll(team, player.getUniqueId()));
        }
    }

    /** {@code /team here} - who owns the land under your feet, and is it currently raidable. */
    private static final class Here extends ClaimSubCommand {

        Here(ClaimModule claims) {
            super(claims, "here", Set.of("claiminfo"), "",
                    "Show who owns the land you are standing on", 0);
        }

        @Override
        boolean needsTeam() {
            return false;
        }

        @Override
        void run(TeamModule module, Player player, Team team, String[] args, String label) {
            org.bukkit.Location at = player.getLocation();
            String world = at.getWorld().getName();
            ClaimManager manager = claims.getManager();

            module.getLang().send(player, ClaimMessages.INFO_HEADER,
                    "x", String.valueOf(at.getBlockX()), "z", String.valueOf(at.getBlockZ()));
            Optional<com.lawkeys.hcfcore.claim.ClaimArea> claim = manager.getClaimAt(world, at.getBlockX(), at.getBlockZ());
            Optional<Team> owner = claim.flatMap(area -> module.getManager().getTeam(area.teamId()));
            if (owner.isEmpty()) {
                if (manager.isWarzone(world, at.getBlockX(), at.getBlockZ())) {
                    module.getLang().send(player, ClaimMessages.INFO_WARZONE,
                            "warzone", claims.getSettings().warzone().displayName());
                } else {
                    module.getLang().send(player, ClaimMessages.INFO_WILDERNESS);
                }
                return;
            }

            module.getLang().send(player, ClaimMessages.INFO_OWNER, "team", owner.get().getName());
            com.lawkeys.hcfcore.claim.ClaimArea area = claim.get();
            module.getLang().send(player, ClaimMessages.INFO_CLAIM,
                    "size", area.width() + "x" + area.length(), "area", String.valueOf(area.area()),
                    "x1", String.valueOf(area.minX()), "z1", String.valueOf(area.minZ()),
                    "x2", String.valueOf(area.maxX()), "z2", String.valueOf(area.maxZ()));
            if (owner.get().getType().isSystem()) {
                // Server land is never raidable; what a player needs to know is
                // whether they can be attacked standing here.
                module.getLang().send(player, owner.get().isSafeZone()
                        ? TeamMessages.INFO_ZONE_SAFE : TeamMessages.INFO_ZONE_COMBAT);
            } else if (!manager.isEnforced()) {
                module.getLang().send(player, ClaimMessages.INFO_UNENFORCED);
            } else {
                boolean raidable = manager.getRaidabilityPolicy().isRaidable(owner.get().getId());
                module.getLang().send(player,
                        raidable ? ClaimMessages.INFO_RAIDABLE : ClaimMessages.INFO_PROTECTED);
            }
            module.getLang().send(player, ClaimMessages.INFO_COUNT,
                    "count", String.valueOf(manager.getClaimCount(owner.get().getId())),
                    "area", String.valueOf(manager.getClaimedArea(owner.get().getId())));
        }
    }

    /**
     * {@code /team map} - an ASCII view of the land around the player, one cell per
     * {@code map.cell-blocks} blocks, each judged by the block in its middle.
     *
     * <p>Rendered as text rather than through a map item so it works for every
     * client, Lunar or vanilla.
     */
    private static final class Map extends ClaimSubCommand {

        Map(ClaimModule claims) {
            super(claims, "map", Set.of(), "[pillars|chat]", "Show the territory around you", 0);
        }

        @Override
        boolean needsTeam() {
            return false;
        }

        @Override
        void run(TeamModule module, Player player, Team team, String[] args, String label) {
            ClaimSettings.MapStyle style = args.length > 0 ? ClaimSettings.MapStyle.of(args[0]) : null;
            if (style == null && args.length > 0) {
                module.getLang().send(player, ClaimMessages.MAP_STYLE_UNKNOWN, "style", args[0]);
                return;
            }
            if (style == null) {
                style = claims.getSettings().map().style();
            }
            if (style == ClaimSettings.MapStyle.PILLARS) {
                pillars(module, player);
                return;
            }
            chat(module, player, team);
        }

        /** The pillars in the world: one block kind per team, on the corners of every claim around. */
        private void pillars(TeamModule module, Player player) {
            com.lawkeys.hcfcore.claim.view.MapPillars.Drawn drawn = claims.getMapPillars().show(player);
            if (drawn.claims() == 0) {
                module.getLang().send(player, ClaimMessages.MAP_PILLARS_EMPTY,
                        "radius", String.valueOf(claims.getSettings().map().radiusChunks()));
                return;
            }
            module.getLang().send(player, ClaimMessages.MAP_PILLARS_HEADER,
                    "claims", String.valueOf(drawn.claims()),
                    "seconds", String.valueOf(claims.getSettings().map().seconds()));
            drawn.materials().forEach((teamId, material) -> module.getLang().send(player,
                    ClaimMessages.MAP_PILLARS_TEAM,
                    "team", claims.getMapPillars().teamName(teamId).orElse("?"),
                    "block", readable(material)));
        }

        /** @return {@code RED_CONCRETE} as {@code Red Concrete}: a block named as players read it */
        private static String readable(String material) {
            StringBuilder name = new StringBuilder();
            for (String word : material.toLowerCase(Locale.ROOT).split("_")) {
                if (!word.isEmpty()) {
                    name.append(name.isEmpty() ? "" : " ")
                            .append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
                }
            }
            return name.toString();
        }

        private void chat(TeamModule module, Player player, Team team) {
            org.bukkit.Location at = player.getLocation();
            String world = at.getWorld().getName();
            ClaimManager manager = claims.getManager();
            ClaimSettings.MapRules rules = claims.getSettings().map();
            int cell = rules.cellBlocks();

            module.getLang().send(player, ClaimMessages.MAP_HEADER,
                    "x", String.valueOf(at.getBlockX()), "z", String.valueOf(at.getBlockZ()),
                    "cell", String.valueOf(cell));
            for (int dz = -rules.chatRadiusZ(); dz <= rules.chatRadiusZ(); dz++) {
                StringBuilder row = new StringBuilder();
                for (int dx = -rules.chatRadiusX(); dx <= rules.chatRadiusX(); dx++) {
                    // Each cell is judged by the block in its middle; the player's own
                    // cell is centred on them.
                    int x = at.getBlockX() + dx * cell;
                    int z = at.getBlockZ() + dz * cell;
                    row.append(cell(module, manager, team, world, x, z, dx == 0 && dz == 0));
                }
                module.getLang().send(player, ClaimMessages.MAP_ROW, "row", row.toString());
            }
            module.getLang().send(player, ClaimMessages.MAP_LEGEND);
        }

        /**
         * @return one colourised cell: the player's position, or a glyph coloured by
         *         how the owning team relates to the viewer
         */
        private String cell(TeamModule module, ClaimManager manager, Team viewer,
                            String world, int x, int z, boolean here) {
            if (here) {
                return "&e+";
            }
            Optional<Team> owner = manager.getOwner(world, x, z);
            if (owner.isEmpty()) {
                return manager.isWarzone(world, x, z) ? "&4#" : "&7-";
            }
            if (owner.get().getType().isSystem()) {
                // Before the relation: to a player with no team every relation is
                // neutral, and the spawn would be drawn as enemy land.
                return "&b#";
            }
            TeamRelation relation = module.getManager().getRelation(viewer, owner.get());
            return switch (relation) {
                case SELF -> "&a#";
                case ALLY -> "&d#";
                case SYSTEM -> "&b#";
                default -> "&c#";
            };
        }
    }

    private static final class SetHome extends ClaimSubCommand {

        private final HomeType type;

        SetHome(ClaimModule claims, HomeType type, String name, String description) {
            super(claims, name, Set.of(), "", description, 0);
            this.type = type;
        }

        @Override
        void run(TeamModule module, Player player, Team team, String[] args, String label) {
            report(module, player, claims.getManager().setHome(team, player.getUniqueId(), type,
                    ClaimModule.toPosition(player.getLocation())));
        }
    }

    /**
     * Teleports to a team home.
     *
     * <p>Refused while the player is in combat, through the {@code TeleportGuard}
     * seam the {@code pvp/} module fills in at startup - so this command gates on
     * combat without the claim module knowing what combat is. With no PvP module
     * running, the default guard allows everything.
     *
     * <p>After a countdown that damage or movement cancels. Everything is asked
     * again when it ends - the guard included, since hitting somebody tags the
     * attacker without hurting them - and the home is looked up again, since it can
     * be moved or the player can leave the team in the meantime. The teleport is
     * {@code teleportAsync}: a plain {@code teleport} to a home in an unloaded chunk
     * would load it on the main thread.
     */
    private static final class GoHome extends ClaimSubCommand {

        private final HomeType type;

        GoHome(ClaimModule claims, HomeType type, String name, String description) {
            super(claims, name, Set.of(), "", description, 0);
            this.type = type;
        }

        @Override
        void run(TeamModule module, Player player, Team team, String[] args, String label) {
            if (destination(module, player, team).isEmpty()) {
                return;
            }
            long seconds = claims.getSettings().homes().warmupSeconds();
            if (!claims.getWarmups().begin(player, "home", seconds, ClaimMessages.HOME_CANCELLED,
                    arriving -> finish(module, arriving, team.getId()))) {
                module.getLang().send(player, ClaimMessages.WARMUP_BUSY);
                return;
            }
            if (seconds > 0) {
                module.getLang().send(player, ClaimMessages.HOME_WARMUP,
                        "type", type.name(), "time", String.valueOf(seconds));
            }
        }

        private void finish(TeamModule module, Player player, java.util.UUID teamId) {
            Optional<Team> team = module.getManager().getTeamOf(player.getUniqueId());
            if (team.isEmpty() || !team.get().getId().equals(teamId)) {
                module.getLang().send(player, ClaimMessages.HOME_CANCELLED);
                return;
            }
            destination(module, player, team.get()).ifPresent(location -> {
                player.teleportAsync(location);
                module.getLang().send(player, ClaimMessages.HOME_TELEPORTED, "type", type.name());
            });
        }

        /** @return where the home is, or empty after saying why it cannot be reached */
        private Optional<Location> destination(TeamModule module, Player player, Team team) {
            if (claims.getTeleportGuard().blockTeleport(player.getUniqueId())) {
                // The guard has already told the player why.
                return Optional.empty();
            }
            Optional<TeamHome> home = claims.getManager().getHome(team.getId(), type);
            if (home.isEmpty()) {
                module.getLang().send(player, ClaimMessages.HOME_NOT_SET, "type", type.name());
                return Optional.empty();
            }
            Optional<Location> location = ClaimModule.toLocation(home.get().position());
            if (location.isEmpty()) {
                module.getLang().send(player, ClaimMessages.HOME_WORLD_UNLOADED,
                        "world", home.get().position().world());
            }
            return location;
        }
    }

    // ------------------------------------------------------------------
    // Staff: land for any team
    // ------------------------------------------------------------------

    /** What the staff subcommands share: a permission, a named team, and a loaded claim cache. */
    private abstract static class StaffClaimSubCommand extends TeamSubCommand {

        protected final ClaimModule claims;

        StaffClaimSubCommand(ClaimModule claims, String name, String usage, String description, boolean playerOnly) {
            super(name, Set.of(), ADMIN_PERMISSION, usage, description, playerOnly, 1);
            this.claims = claims;
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            if (claims.getManager() == null) {
                module.getLang().send(sender, ClaimMessages.CLAIM_DISABLED);
                return;
            }
            Optional<Team> team = requireTeamByName(module, sender, args[0]);
            if (team.isPresent()) {
                run(module, sender, team.get(), args);
            }
        }

        abstract void run(TeamModule module, CommandSender sender, Team team, String[] args);

        @Override
        public List<String> tabComplete(TeamModule module, CommandSender sender, String[] args) {
            if (args.length != 1) {
                return List.of();
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Team team : module.getManager().getTeams()) {
                if (team.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(team.getName());
                }
            }
            return names;
        }
    }

    /**
     * {@code /team forceclaim <team>} - hands staff the claiming wand for any team:
     * how spawn, the warzone's roads and event grounds get their land, since a server
     * team has nobody to type {@code /team claim}.
     *
     * <p>A staff claim is free and follows none of the size or placement rules, which
     * exist for players; it is still refused over another team's land or a reserved
     * region (see {@code ClaimManager#claim}).
     */
    private static final class ForceClaim extends StaffClaimSubCommand {

        ForceClaim(ClaimModule claims) {
            super(claims, "forceclaim", "<team> [x1 z1 x2 z2 [world]]",
                    "Draw a claim for any team, or give its corners", false);
        }

        /**
         * With coordinates - {@code <x1> <z1> <x2> <z2> [world]} - the rectangle is claimed
         * at once, with no wand: from the console too, which has no hand to hold one, so
         * server land can be laid out by a script. The world defaults to the sender's.
         */
        @Override
        void run(TeamModule module, CommandSender sender, Team team, String[] args) {
            if (args.length == 1) {
                if (!(sender instanceof Player player)) {
                    module.getLang().send(sender, ClaimMessages.FORCECLAIM_USAGE);
                    return;
                }
                claims.getWandSessions().give(player, new TeamClaimTask(claims, team.getId(), true));
                return;
            }
            if (args.length < 5) {
                module.getLang().send(sender, ClaimMessages.FORCECLAIM_USAGE);
                return;
            }
            int[] corners = new int[4];
            for (int i = 0; i < 4; i++) {
                try {
                    corners[i] = Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    module.getLang().send(sender, ClaimMessages.FORCECLAIM_USAGE);
                    return;
                }
            }
            String world = args.length > 5 ? args[5]
                    : sender instanceof Player player ? player.getWorld().getName() : null;
            if (world == null) {
                module.getLang().send(sender, ClaimMessages.FORCECLAIM_USAGE);
                return;
            }
            report(module, sender, claims.getManager().claim(team, null, world,
                    corners[0], corners[1], corners[2], corners[3]));
        }
    }

    /** {@code /team forceunclaim <team> [all]} - the chunk you stand in, or all of that team's land. */
    private static final class ForceUnclaim extends StaffClaimSubCommand {

        ForceUnclaim(ClaimModule claims) {
            super(claims, "forceunclaim", "<team> [all]", "Release a team's claim here, or all its land", false);
        }

        @Override
        void run(TeamModule module, CommandSender player, Team team, String[] args) {
            ClaimManager manager = claims.getManager();
            if (args.length > 1 && args[1].equalsIgnoreCase("all")) {
                TeamResult result = manager.unclaimAll(team, null);
                if (!result.isSuccess()) {
                    report(module, player, result);
                    return;
                }
                module.getLang().send(player, ClaimMessages.ADMIN_UNCLAIMED_ALL,
                        "team", team.getName(), "count", result.getPlaceholders().getOrDefault("count", "0"));
                return;
            }
            if (!(player instanceof Player standing)) {
                module.getLang().send(player, ClaimMessages.FORCEUNCLAIM_CONSOLE);
                return;
            }
            org.bukkit.Location at = standing.getLocation();
            TeamResult result = manager.unclaim(team, null, at.getWorld().getName(), at.getBlockX(), at.getBlockZ());
            if (!result.isSuccess()) {
                report(module, player, result);
                return;
            }
            module.getLang().send(player, ClaimMessages.ADMIN_UNCLAIMED,
                    "team", team.getName(), "claim", result.getPlaceholders().getOrDefault("claim", ""));
        }
    }
}
