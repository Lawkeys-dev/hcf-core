package com.lawkeys.hcfcore.claim.command;

import com.lawkeys.hcfcore.claim.ClaimManager;
import com.lawkeys.hcfcore.claim.ClaimMessages;
import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.claim.HomeType;
import com.lawkeys.hcfcore.claim.TeamHome;
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

    /** @return the square of chunks {@code radius} chunks around {@code centre}, in each direction */
    private static Set<ChunkPosition> square(ChunkPosition centre, int radius) {
        Set<ChunkPosition> selection = new LinkedHashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                selection.add(new ChunkPosition(centre.world(), centre.x() + dx, centre.z() + dz));
            }
        }
        return selection;
    }

    /** @return the radius typed, or empty after saying it is not one between 0 and {@code max} */
    private static OptionalInt parseRadius(TeamModule module, CommandSender sender, String input, int max) {
        try {
            int radius = Integer.parseInt(input);
            if (radius >= 0 && radius <= max) {
                return OptionalInt.of(radius);
            }
        } catch (NumberFormatException ignored) {
            // reported below, like an out-of-range number
        }
        module.getLang().send(sender, ClaimMessages.INVALID_RADIUS, "input", input);
        return OptionalInt.empty();
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
            // Free land, or their own team's: both are places they may stand.
            Optional<ChunkPosition> destination = claims.nearestFreeLand(from,
                    module.getManager().getTeamOf(player.getUniqueId()).map(Team::getId).orElse(null));
            if (destination.isEmpty()) {
                module.getLang().send(player, ClaimMessages.STUCK_NOWHERE);
                return Optional.empty();
            }
            if (destination.get().equals(from)) {
                module.getLang().send(player, ClaimMessages.STUCK_NOT_STUCK);
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
                ChunkPosition at = ClaimModule.toChunk(other.getLocation());
                if (claims.getManager().lockedAgainst(at, other.getUniqueId()).isEmpty()
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

    private static final class Claim extends ClaimSubCommand {

        /** Keeps a typo like {@code /team claim 200} from trying to build a huge square. */
        private static final int MAX_RADIUS = 8;

        Claim(ClaimModule claims) {
            super(claims, "claim", Set.of(), "[radius]",
                    "Claim the chunk you are standing in", 0);
        }

        @Override
        void run(TeamModule module, Player player, Team team, String[] args, String label) {
            int radius = 0;
            if (args.length > 0) {
                OptionalInt parsed = parseRadius(module, player, args[0], MAX_RADIUS);
                if (parsed.isEmpty()) {
                    return;
                }
                radius = parsed.getAsInt();
            }
            Set<ChunkPosition> selection = square(ClaimModule.toChunk(player.getLocation()), radius);
            report(module, player, claims.getManager().claim(team, player.getUniqueId(), selection));
        }
    }

    private static final class Unclaim extends ClaimSubCommand {

        Unclaim(ClaimModule claims) {
            super(claims, "unclaim", Set.of(), "", "Release the chunk you are standing in", 0);
        }

        @Override
        void run(TeamModule module, Player player, Team team, String[] args, String label) {
            ChunkPosition chunk = ClaimModule.toChunk(player.getLocation());
            report(module, player, claims.getManager().unclaim(team, player.getUniqueId(), chunk));
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

    /** {@code /team here} - who owns the chunk under your feet, and is it currently raidable. */
    private static final class Here extends ClaimSubCommand {

        Here(ClaimModule claims) {
            super(claims, "here", Set.of("claiminfo"), "",
                    "Show who owns the chunk you are standing in", 0);
        }

        @Override
        boolean needsTeam() {
            return false;
        }

        @Override
        void run(TeamModule module, Player player, Team team, String[] args, String label) {
            ChunkPosition chunk = ClaimModule.toChunk(player.getLocation());
            ClaimManager manager = claims.getManager();

            module.getLang().send(player, ClaimMessages.INFO_HEADER, "chunk", chunk.toString());
            Optional<Team> owner = manager.getOwner(chunk);
            if (owner.isEmpty()) {
                if (manager.isWarzone(chunk)) {
                    module.getLang().send(player, ClaimMessages.INFO_WARZONE,
                            "warzone", claims.getSettings().warzone().displayName());
                } else {
                    module.getLang().send(player, ClaimMessages.INFO_WILDERNESS);
                }
                return;
            }

            module.getLang().send(player, ClaimMessages.INFO_OWNER, "team", owner.get().getName());
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
            int max = manager.getMaxClaims(owner.get());
            module.getLang().send(player, ClaimMessages.INFO_COUNT,
                    "count", String.valueOf(manager.getClaimCount(owner.get().getId())),
                    "max", max > 0 ? String.valueOf(max) : "∞");
        }
    }

    /**
     * {@code /team map} - an ASCII view of the chunks around the player.
     *
     * <p>Rendered as text rather than through a map item so it works for every
     * client, Lunar or vanilla.
     */
    private static final class Map extends ClaimSubCommand {

        private static final int RADIUS_X = 12;
        private static final int RADIUS_Z = 6;

        Map(ClaimModule claims) {
            super(claims, "map", Set.of(), "", "Show a map of nearby territory", 0);
        }

        @Override
        boolean needsTeam() {
            return false;
        }

        @Override
        void run(TeamModule module, Player player, Team team, String[] args, String label) {
            ChunkPosition centre = ClaimModule.toChunk(player.getLocation());
            ClaimManager manager = claims.getManager();

            module.getLang().send(player, ClaimMessages.MAP_HEADER, "chunk", centre.toString());
            for (int dz = -RADIUS_Z; dz <= RADIUS_Z; dz++) {
                StringBuilder row = new StringBuilder();
                for (int dx = -RADIUS_X; dx <= RADIUS_X; dx++) {
                    ChunkPosition chunk = new ChunkPosition(
                            centre.world(), centre.x() + dx, centre.z() + dz);
                    row.append(cell(module, manager, team, chunk, dx == 0 && dz == 0));
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
                            ChunkPosition chunk, boolean here) {
            if (here) {
                return "&e+";
            }
            Optional<Team> owner = manager.getOwner(chunk);
            if (owner.isEmpty()) {
                return manager.isWarzone(chunk) ? "&4#" : "&7-";
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

        StaffClaimSubCommand(ClaimModule claims, String name, String usage, String description) {
            super(name, Set.of(), ADMIN_PERMISSION, usage, description, true, 1);
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
                run(module, (Player) sender, team.get(), args);
            }
        }

        abstract void run(TeamModule module, Player player, Team team, String[] args);

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
     * {@code /team forceclaim <team> [radius]} - how spawn, the warzone, roads and
     * event grounds get their land, since a server team has nobody to type
     * {@code /team claim}.
     *
     * <p>A staff claim is still refused over another team's land or a reserved
     * region; for a server team it skips the allowance and placement rules, which
     * exist for players (see {@code ClaimManager#claim}).
     */
    private static final class ForceClaim extends StaffClaimSubCommand {

        /**
         * Larger than a player's: server land is drawn a big square at a time. Still
         * bounded, so a typo cannot claim a region the size of the map in one go.
         */
        private static final int MAX_RADIUS = 32;

        ForceClaim(ClaimModule claims) {
            super(claims, "forceclaim", "<team> [radius]", "Claim land for any team, server teams included");
        }

        @Override
        void run(TeamModule module, Player player, Team team, String[] args) {
            int radius = 0;
            if (args.length > 1) {
                OptionalInt parsed = parseRadius(module, player, args[1], MAX_RADIUS);
                if (parsed.isEmpty()) {
                    return;
                }
                radius = parsed.getAsInt();
            }
            Set<ChunkPosition> selection = square(ClaimModule.toChunk(player.getLocation()), radius);
            TeamResult result = claims.getManager().claim(team, null, selection);
            if (!result.isSuccess()) {
                report(module, player, result);
                return;
            }
            module.getLang().send(player, ClaimMessages.ADMIN_CLAIMED,
                    "team", team.getName(),
                    "count", result.getPlaceholders().getOrDefault("count", "0"),
                    "total", result.getPlaceholders().getOrDefault("total", "0"));
        }
    }

    /** {@code /team forceunclaim <team> [all]} - the chunk you stand in, or all of that team's land. */
    private static final class ForceUnclaim extends StaffClaimSubCommand {

        ForceUnclaim(ClaimModule claims) {
            super(claims, "forceunclaim", "<team> [all]", "Release a team's chunk here, or all its land");
        }

        @Override
        void run(TeamModule module, Player player, Team team, String[] args) {
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
            ChunkPosition chunk = ClaimModule.toChunk(player.getLocation());
            TeamResult result = manager.unclaim(team, null, chunk);
            if (!result.isSuccess()) {
                report(module, player, result);
                return;
            }
            module.getLang().send(player, ClaimMessages.ADMIN_UNCLAIMED,
                    "team", team.getName(), "chunk", chunk.toString());
        }
    }
}
