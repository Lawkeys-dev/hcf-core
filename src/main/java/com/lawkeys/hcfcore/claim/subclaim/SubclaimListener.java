package com.lawkeys.hcfcore.claim.subclaim;

import com.lawkeys.hcfcore.claim.ClaimMessages;
import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.claim.listener.ClaimProtectionListener;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamRole;
import com.lawkeys.hcfcore.util.LegacyText;
import com.lawkeys.hcfcore.util.RefusalThrottle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Makes {@code [Subclaim]} signs mean something: written on a container of the
 * team's own land, they keep its other members out of it - opening it, breaking it,
 * breaking the sign, or putting a hopper under it (see {@link Subclaim}).
 *
 * <p>The signs are the storage: nothing is saved, and a subclaim is exactly what the
 * signs on a container say.
 */
public final class SubclaimListener implements Listener {

    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private final ClaimModule module;
    private final Supplier<Subclaim.Rules> rules;
    private final RefusalThrottle refusals = new RefusalThrottle(2_000L);

    public SubclaimListener(ClaimModule module, Supplier<Subclaim.Rules> rules) {
        this.module = Objects.requireNonNull(module, "module");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    // ------------------------------------------------------------------
    // Writing a sign
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSign(SignChangeEvent event) {
        Subclaim.Rules current = rules.get();
        // Editing a subclaim sign is breaking it: rewritten without its header, it
        // would guard nothing. Judged on what the sign says before the edit.
        Block guarded = subclaimSignOn(event.getBlock());
        if (guarded != null && denied(event.getPlayer(), guarded)) {
            event.setCancelled(true);
            return;
        }
        List<String> lines = new ArrayList<>();
        for (Component line : event.lines()) {
            lines.add(line == null ? "" : PlainTextComponentSerializer.plainText().serialize(line));
        }
        Optional<Subclaim> declared = Subclaim.read(lines, current.header());
        if (!current.enabled() || declared.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        Block container = attachedTo(event.getBlock());
        if (container == null) {
            refuse(event, player, ClaimMessages.SUBCLAIM_NOT_CONTAINER);
            return;
        }
        Optional<Team> owner = module.ownerAt(container.getLocation());
        Optional<Team> own = module.getTeams().getManager().getTeamOf(player.getUniqueId());
        if (owner.isEmpty() || own.isEmpty() || !owner.get().getId().equals(own.get().getId())) {
            refuse(event, player, ClaimMessages.SUBCLAIM_NOT_OWN_LAND);
            return;
        }
        // Another subclaim already on it: only somebody it lets in may add a sign.
        if (!mayOpen(player, owner.get(), container)) {
            refuse(event, player, ClaimMessages.SUBCLAIM_DENIED);
            return;
        }
        if (declared.get().names().isEmpty()) {
            // A sign with no name is the writer's own chest.
            event.line(1, Component.text(player.getName()));
        }
        event.line(0, LegacyText.SERIALIZER.deserialize(
                module.getLang().get(ClaimMessages.SUBCLAIM_HEADER, "header", current.header())));
        module.getLang().send(player, ClaimMessages.SUBCLAIM_CREATED);
    }

    private void refuse(SignChangeEvent event, Player player, String key) {
        event.setCancelled(true);
        module.getLang().send(player, key);
    }

    // ------------------------------------------------------------------
    // Using it
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || !isContainer(block)) {
            return;
        }
        if (denied(event.getPlayer(), block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Block container = isContainer(block) ? block : subclaimSignOn(block);
        if (container != null && denied(event.getPlayer(), container)) {
            event.setCancelled(true);
        }
    }

    /** A hopper under a subclaim would empty it into anybody's hands. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopper(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.HOPPER) {
            return;
        }
        Block above = event.getBlockPlaced().getRelative(BlockFace.UP);
        if (isContainer(above) && denied(event.getPlayer(), above)) {
            event.setCancelled(true);
        }
    }

    /** @return whether this player may not touch that container, after telling them */
    private boolean denied(Player player, Block container) {
        Subclaim.Rules current = rules.get();
        if (!current.enabled() || module.getManager() == null
                || player.hasPermission(ClaimProtectionListener.BYPASS_PERMISSION)) {
            return false;
        }
        Optional<Team> owner = module.ownerAt(container.getLocation());
        // Only the owning team's own members are kept out: anybody else is the
        // claim's business, and a raid opens subclaims with the rest.
        if (owner.isEmpty() || owner.get().getRole(player.getUniqueId()).isEmpty()) {
            return false;
        }
        if (mayOpen(player, owner.get(), container)) {
            return false;
        }
        if (refusals.tryTell(player.getUniqueId(), System.currentTimeMillis())) {
            module.getLang().send(player, ClaimMessages.SUBCLAIM_DENIED);
        }
        return true;
    }

    private boolean mayOpen(Player player, Team owner, Block container) {
        Subclaim.Rules current = rules.get();
        TeamRole role = owner.getRole(player.getUniqueId()).orElse(null);
        return Subclaim.anyAllows(signsOn(container, current.header()), player.getName(), role,
                openAny(owner, current.openAny()));
    }

    /**
     * The lowest role of the owning team that opens all its subclaims: the team's own
     * choice ({@code /team settings}, {@code open-subclaims}) where it may make one,
     * else the server's - {@code null}, nobody, when the server says {@code none} and
     * the team said nothing.
     */
    private TeamRole openAny(Team owner, TeamRole server) {
        var manager = module.getTeams().getManager();
        boolean editable = module.getTeams().getSettings().custom().editable(OPEN_SUBCLAIMS);
        if (!editable || owner.getPermission(OPEN_SUBCLAIMS).isEmpty()) {
            return server;
        }
        return manager.requiredRole(owner, OPEN_SUBCLAIMS, server == null ? TeamRole.LEADER : server);
    }

    /** The permission's key in {@code /team settings}. */
    public static final String OPEN_SUBCLAIMS = "open-subclaims";

    // ------------------------------------------------------------------
    // Finding the signs
    // ------------------------------------------------------------------

    private static boolean isContainer(Block block) {
        return block.getState(false) instanceof Container;
    }

    /** @return the block a wall sign hangs on, when it is a container */
    private static Block attachedTo(Block sign) {
        if (!(sign.getBlockData() instanceof WallSign wall)) {
            return null;
        }
        Block behind = sign.getRelative(wall.getFacing().getOppositeFace());
        return isContainer(behind) ? behind : null;
    }

    /** @return the container a subclaim sign guards, when that block is one */
    private Block subclaimSignOn(Block block) {
        if (!(block.getState(false) instanceof Sign sign)) {
            return null;
        }
        return Subclaim.read(lines(sign), rules.get().header()).isPresent() ? attachedTo(block) : null;
    }

    /** @return every subclaim sign on the container - on both halves of a double chest */
    private static List<Subclaim> signsOn(Block container, String header) {
        List<Subclaim> signs = new ArrayList<>();
        for (Block half : halves(container)) {
            for (BlockFace side : SIDES) {
                Block candidate = half.getRelative(side);
                if (candidate.getBlockData() instanceof WallSign wall && wall.getFacing() == side
                        && candidate.getState(false) instanceof Sign sign) {
                    Subclaim.read(lines(sign), header).ifPresent(signs::add);
                }
            }
        }
        return signs;
    }

    private static Set<Block> halves(Block container) {
        Set<Block> halves = new LinkedHashSet<>();
        halves.add(container);
        BlockState state = container.getState(false);
        if (state instanceof Chest chest) {
            InventoryHolder holder = chest.getInventory().getHolder(false);
            if (holder instanceof DoubleChest pair) {
                if (pair.getLeftSide(false) instanceof Chest left) {
                    halves.add(left.getBlock());
                }
                if (pair.getRightSide(false) instanceof Chest right) {
                    halves.add(right.getBlock());
                }
            }
        }
        return halves;
    }

    private static List<String> lines(Sign sign) {
        List<String> lines = new ArrayList<>();
        for (Component line : sign.getSide(Side.FRONT).lines()) {
            lines.add(PlainTextComponentSerializer.plainText().serialize(line));
        }
        return lines;
    }
}
