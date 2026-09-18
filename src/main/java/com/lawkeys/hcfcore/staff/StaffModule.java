package com.lawkeys.hcfcore.staff;

import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.command.VisiblePlayers;
import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.database.dao.JdbcLastInventoryStore;
import com.lawkeys.hcfcore.database.dao.JdbcStrikeStore;
import com.lawkeys.hcfcore.database.dao.JdbcTicketStore;
import com.lawkeys.hcfcore.database.dao.JdbcStaffBanStore;
import com.lawkeys.hcfcore.database.dao.JdbcStaffStashStore;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.staff.StaffManager.VanishSource;
import com.lawkeys.hcfcore.staff.command.BroadcastCommand;
import com.lawkeys.hcfcore.staff.command.FreezeCommand;
import com.lawkeys.hcfcore.staff.command.InvseeCommand;
import com.lawkeys.hcfcore.staff.command.LastInvCommand;
import com.lawkeys.hcfcore.staff.command.StrikeCommand;
import com.lawkeys.hcfcore.staff.command.TicketCommand;
import com.lawkeys.hcfcore.staff.command.ClearChatCommand;
import com.lawkeys.hcfcore.staff.command.StaffBuildCommand;
import com.lawkeys.hcfcore.staff.command.StaffChatCommand;
import com.lawkeys.hcfcore.staff.command.StaffCommand;
import com.lawkeys.hcfcore.staff.command.VanishCommand;
import com.lawkeys.hcfcore.staff.listener.FreezeListener;
import com.lawkeys.hcfcore.staff.listener.InspectListener;
import com.lawkeys.hcfcore.staff.listener.StaffListener;
import com.lawkeys.hcfcore.staff.listener.TicketMenuListener;
import com.lawkeys.hcfcore.staff.strike.Strike;
import com.lawkeys.hcfcore.staff.strike.StrikeLadder;
import com.lawkeys.hcfcore.staff.strike.StrikeManager;
import com.lawkeys.hcfcore.staff.strike.StrikeStore;
import com.lawkeys.hcfcore.staff.ticket.TicketManager;
import com.lawkeys.hcfcore.staff.ticket.TicketStore;
import com.lawkeys.hcfcore.startup.StartupBarrier;
import com.lawkeys.hcfcore.startup.StartupGate;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamManager;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.team.TeamResult;
import com.lawkeys.hcfcore.team.TeamMessages;
import com.lawkeys.hcfcore.util.ItemText;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Wires the staff module into the server.
 *
 * <p>Enabled after {@code claim/}, whose {@link com.lawkeys.hcfcore.claim.BuildOverride}
 * seam it answers so that the staff build toggle lifts territory protection. It
 * works without the claim module: the toggle then simply has nothing to lift.
 *
 * <p><strong>Where the care goes: the inventory.</strong> Entering staff mode takes
 * a player's whole inventory away. Everything below is arranged so that it can only
 * ever be in one of two places - their hands, or {@link StaffStashes} - and never
 * in neither.
 */
public final class StaffModule {

    private final Plugin plugin;
    private final ClaimModule claims;
    private final LangManager lang;
    private final StartupGate startup;

    private volatile StaffSettings settings = StaffSettings.defaults();

    private final StaffManager manager = new StaffManager();
    private final FreezeManager freezes = new FreezeManager();
    private final InvseeSessions invsee = new InvseeSessions();
    private StaffStashes stashes;
    private StaffBans bans;
    private LastInventoryStore lastInventories = LastInventoryStore.NO_OP;
    private TicketManager tickets;
    private StrikeManager strikes;
    private volatile StrikeLadder strikeLadder = StrikeLadder.empty();
    private NamespacedKey toolbarMarker;
    private BukkitTask saveTask;
    private BukkitTask reminderTask;

    /** @param claims may be {@code null} if the claim module is not running */
    public StaffModule(Plugin plugin, ClaimModule claims, LangManager lang, StartupGate startup) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.claims = claims;
        this.lang = Objects.requireNonNull(lang, "lang");
        this.startup = Objects.requireNonNull(startup, "startup");
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public LangManager getLang() {
        return lang;
    }

    public StartupGate getStartup() {
        return startup;
    }

    public StaffSettings getSettings() {
        return settings;
    }

    public StaffManager getManager() {
        return manager;
    }

    public StaffStashes getStashes() {
        return stashes;
    }

    public FreezeManager getFreezes() {
        return freezes;
    }

    public InvseeSessions getInvsee() {
        return invsee;
    }

    public StaffBans getBans() {
        return bans;
    }

    public LastInventoryStore getLastInventories() {
        return lastInventories;
    }

    public TicketManager getTickets() {
        return tickets;
    }

    public StrikeManager getStrikes() {
        return strikes;
    }

    public StrikeLadder getStrikeLadder() {
        return strikeLadder;
    }

    public NamespacedKey getToolbarMarker() {
        return toolbarMarker;
    }

    public void enable(DataSource dataSource, long saveIntervalSeconds) {
        reloadSettings();

        this.toolbarMarker = new NamespacedKey(plugin, "staff_toolbar");
        StaffStashStore store = dataSource == null
                ? StaffStashStore.NO_OP
                : new JdbcStaffStashStore(dataSource, message -> plugin.getLogger().info(message));
        this.stashes = new StaffStashes(store);
        StaffBanStore banStore = dataSource == null
                ? StaffBanStore.NO_OP
                : new JdbcStaffBanStore(dataSource, message -> plugin.getLogger().info(message));
        this.bans = new StaffBans(banStore);
        // The death archive is queried on demand rather than loaded: see
        // LastInventoryStore for why this one is not cached like everything else.
        this.lastInventories = dataSource == null
                ? LastInventoryStore.NO_OP
                : new JdbcLastInventoryStore(dataSource, message -> plugin.getLogger().info(message));

        TicketStore ticketStore = dataSource == null
                ? TicketStore.NO_OP
                : new JdbcTicketStore(dataSource, message -> plugin.getLogger().info(message));
        // The cooldown is read at each use rather than fixed here, so /hcf reload
        // changes it like every other setting.
        this.tickets = new TicketManager(ticketStore, () -> settings.tickets().cooldownSeconds());
        StrikeStore strikeStore = dataSource == null
                ? StrikeStore.NO_OP
                : new JdbcStrikeStore(dataSource, message -> plugin.getLogger().info(message));
        this.strikes = new StrikeManager(strikeStore);

        StartupBarrier.Load load = startup.expect("staff data");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                stashes.loadAll();
                if (stashes.size() > 0) {
                    plugin.getLogger().info("Holding " + stashes.size()
                            + " staff inventories from a previous session; each is given back on login.");
                }
                bans.loadAll();
                // Applies migrations 2 and 3 even when nothing is stored yet, so the
                // archive's table exists before the first death rather than at the
                // first death, on whatever thread that happens to be.
                lastInventories.initSchema();
                tickets.loadAll();
                strikes.loadAll();
                load.succeeded();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Could not load staff data (held inventories, bans, tickets, strikes).", e);
                load.failed();
            }
        });

        if (saveIntervalSeconds > 0) {
            long ticks = saveIntervalSeconds * 20L;
            this.saveTask = Bukkit.getScheduler()
                    .runTaskTimerAsynchronously(plugin, this::flushQuietly, ticks, ticks);
        }

        // Reminds frozen players that they are held. Ticks once a second and does the
        // arithmetic itself rather than being rescheduled when the interval changes,
        // so /hcf reload applies to it like everything else.
        this.reminderTask = Bukkit.getScheduler().runTaskTimer(plugin, this::remindFrozen, 20L, 20L);

        // The staff build toggle, answered for claim/ (ARCHITECTURE.md section 14).
        if (claims != null) {
            claims.setBuildOverride(manager::hasStaffBuild);
        }

        plugin.getServer().getPluginManager().registerEvents(new StaffListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new FreezeListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new InspectListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new TicketMenuListener(this), plugin);

        register("staff", new StaffCommand(this));
        register("vanish", new VanishCommand(this));
        register("staffchat", new StaffChatCommand(this));
        register("staffbuild", new StaffBuildCommand(this));
        register("broadcast", new BroadcastCommand(this));
        register("clearchat", new ClearChatCommand(this));
        register("freeze", new FreezeCommand(this));
        register("invsee", new InvseeCommand(this));
        register("lastinv", new LastInvCommand(this));
        TicketCommand ticketCommand = new TicketCommand(this);
        register("report", ticketCommand);
        register("request", ticketCommand);
        register("tickets", ticketCommand);
        register("strike", new StrikeCommand(this));
    }

    private void register(String name, TabExecutor executor) {
        PluginCommand command = plugin.getServer().getPluginCommand(name);
        if (command == null) {
            plugin.getLogger().severe("The '" + name + "' command is missing from plugin.yml; "
                    + "that staff tool is unavailable.");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    public void reloadSettings() {
        var section = ConfigManager.loadFile(plugin, "staff.yml");
        this.settings = StaffSettingsLoader.load(section,
                warning -> plugin.getLogger().warning("staff.yml: " + warning));
        this.strikeLadder = StaffSettingsLoader.loadStrikeLadder(section,
                warning -> plugin.getLogger().warning("staff.yml: strikes." + warning));
        // Said out loud, because a strike that runs a ban is not something an
        // operator should discover by issuing one.
        if (!strikeLadder.isEmpty()) {
            plugin.getLogger().info("Strike sanctions are configured at " + strikeLadder.steps()
                    + " active strikes.");
        }
    }

    /**
     * Takes every staff member out of the mode, giving their inventories back, and
     * writes what is left.
     *
     * <p>Order matters and is the reason this is not a loop over {@code clearAll}:
     * leaving the mode is what puts somebody's items back in their hands, and it has
     * to happen while they are still online and before the final flush, or the stash
     * row would outlive the shutdown and be handed back at their next login instead.
     * That would be correct but confusing; this is correct and invisible.
     */
    public void disable() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (reminderTask != null) {
            reminderTask.cancel();
            reminderTask = null;
        }
        if (stashes == null) {
            return;
        }
        for (UUID playerId : manager.staffModePlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                // Quietly: the server is going down and nobody is reading chat.
                leaveStaffMode(player, false);
            }
        }
        manager.clearAll();
        // A freeze is a conversation in progress, not a punishment: the server going
        // down ends it, and nobody is banned for a disconnect they did not choose.
        freezes.clearAll();
        invsee.clearAll();
        try {
            int written = stashes.flush() + bans.flush() + tickets.flush() + strikes.flush();
            if (written > 0) {
                plugin.getLogger().info("Saved " + written + " staff changes on shutdown.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save staff data on shutdown", e);
        }
    }

    private void flushQuietly() {
        try {
            stashes.flush();
            bans.flush();
            tickets.flush();
            strikes.flush();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Periodic staff save failed; the affected rows stay queued.", e);
        }
    }

    /**
     * Tells frozen players, at the configured interval, that they are still held.
     *
     * <p>Worth the repetition: the message they got when the freeze started has
     * usually scrolled away by the time they wonder why they cannot move, and a
     * player who thinks they have crashed is a player about to restart their client -
     * which is the disconnect the hold is trying to avoid.
     */
    private void remindFrozen() {
        long every = settings.freeze().reminderSeconds();
        if (every <= 0 || !settings.enabled() || !settings.freeze().enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (UUID playerId : freezes.frozenPlayers()) {
            FreezeManager.Freeze freeze = freezes.get(playerId).orElse(null);
            Player player = Bukkit.getPlayer(playerId);
            if (freeze == null || player == null) {
                continue;
            }
            long heldSeconds = (now - freeze.since()) / 1000L;
            // Nothing at the moment of freezing: they have just been told.
            if (heldSeconds > 0 && heldSeconds % every == 0) {
                lang.send(player, StaffMessages.FREEZE_REMINDER, "player", freeze.frozenBy());
            }
        }
    }

    /** Writes a ban or a lift now rather than at the next periodic save. */
    public void flushBansSoon() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::flushQuietly);
    }

    /**
     * Writes every pending staff change - held inventories, bans, tickets, strikes -
     * now rather than at the next periodic save.
     *
     * <p>Called the moment an inventory is taken or given back. The window between
     * the cache holding it and the row existing is a few milliseconds of async
     * scheduling rather than up to a save interval - the same trade {@code KingStashes}
     * makes, and the smallest one available while CONTRIBUTING.md section 5 forbids a
     * blocking write on the main thread.
     */
    public void flushSoon() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::flushQuietly);
    }

    // ------------------------------------------------------------------
    // Strikes against teams
    // ------------------------------------------------------------------

    /** @return the team module, or {@code null} when claims - and so teams - are not running */
    public TeamModule getTeams() {
        return claims == null ? null : claims.getTeams();
    }

    /**
     * What issuing a strike did.
     *
     * @param pointsLost      points taken from the team, {@code 0} for none
     * @param disbanded       the team was disbanded
     * @param disbandRefused  the ladder said disband and something refused it
     */
    public record StrikeOutcome(Strike strike, int active, long pointsLost, boolean disbanded,
                                boolean disbandRefused) {
    }

    /**
     * Strikes a team, and applies what the ladder says for its new count: points
     * taken, console commands run, the team disbanded - in that order, so a command
     * can still name the team.
     *
     * @param subject the member it was for, or blank
     */
    public StrikeOutcome strikeTeam(Team team, String subject, String reason, String issuedBy) {
        TeamModule teams = Objects.requireNonNull(getTeams(), "teams");
        TeamManager manager = teams.getManager();
        Strike strike = strikes.issue(team.getId(), team.getName(), subject, reason, issuedBy,
                settings.strikes().validSeconds());
        flushSoon();
        int active = strikes.activeCount(team.getId());
        StrikeLadder.Sanction sanction = strikeLadder.at(active).orElse(null);
        if (sanction == null) {
            return new StrikeOutcome(strike, active, 0L, false, false);
        }
        long lost = StrikeLadder.pointsLost(team.getPoints(), sanction.pointsLossPercent());
        if (lost > 0) {
            manager.addPoints(team, -lost);
        }
        // The name filled in is the team's own, never what was typed: it ends up in a
        // console command.
        for (String command : StrikeLadder.fill(sanction.commands(), team.getName(), active)) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
        boolean disbanded = false;
        boolean refused = false;
        if (sanction.disband()) {
            List<Player> members = teams.getOnlineMembers(team);
            TeamResult result = manager.disband(team, null);
            disbanded = result.isSuccess();
            refused = !disbanded;
            if (disbanded) {
                teams.broadcastTo(members, null, TeamMessages.DISBAND_BROADCAST, "player", issuedBy);
            }
        }
        return new StrikeOutcome(strike, active, lost, disbanded, refused);
    }

    // ------------------------------------------------------------------
    // Staff mode
    // ------------------------------------------------------------------

    /**
     * Puts a player into staff mode: their inventory is taken and held, the toolbar
     * takes its place, and the configured extras are turned on.
     *
     * @return whether they are now in staff mode
     */
    public boolean enterStaffMode(Player player) {
        UUID playerId = player.getUniqueId();
        if (!manager.enterStaffMode(playerId)) {
            return false;
        }
        byte[] contents = ItemStack.serializeItemsAsBytes(player.getInventory().getContents());
        if (!stashes.put(playerId, contents)) {
            // Already holding one: entering would stash the toolbar over their real
            // items. Refuse rather than destroy them, and say so.
            manager.leaveStaffMode(playerId);
            lang.send(player, StaffMessages.MODE_ALREADY_HELD);
            return false;
        }
        // Written before the inventory is cleared, so the row is on its way to the
        // database before the items leave the player.
        flushSoon();
        player.getInventory().clear();
        giveToolbar(player);

        StaffSettings.StaffModeRules rules = settings.staffMode();
        if (rules.vanish()) {
            applyVanish(player, VanishSource.STAFF_MODE);
        }
        if (rules.flight()) {
            player.setAllowFlight(true);
            player.setFlying(true);
        }
        lang.send(player, StaffMessages.MODE_ENTERED);
        if (rules.announce()) {
            announceToStaff(StaffMessages.MODE_ANNOUNCE_ENTERED, player);
        }
        return true;
    }

    /**
     * Takes a player out of staff mode and gives their own inventory back.
     *
     * @param tell whether to send them the confirmation, which shutdown skips
     * @return whether they were in staff mode
     */
    public boolean leaveStaffMode(Player player, boolean tell) {
        UUID playerId = player.getUniqueId();
        if (!manager.leaveStaffMode(playerId)) {
            return false;
        }
        // The toolbar goes first: restoring over it would leave copies in any slot
        // the stash does not fill.
        player.getInventory().clear();
        boolean restored = restoreStash(player);

        StaffSettings.StaffModeRules rules = settings.staffMode();
        if (rules.vanish() && manager.revealIfStaffMode(playerId)) {
            showToEveryone(player);
        }
        if (rules.flight()) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        if (!restored) {
            // Told even on shutdown: they are left holding nothing.
            lang.send(player, StaffMessages.MODE_RESTORE_FAILED);
        }
        if (tell) {
            if (restored) {
                lang.send(player, StaffMessages.MODE_LEFT);
            }
            if (rules.announce()) {
                announceToStaff(StaffMessages.MODE_ANNOUNCE_LEFT, player);
            }
        }
        return true;
    }

    /**
     * Gives back an inventory held from an earlier session.
     *
     * <p>Called on join for somebody who is owed one but is not in staff mode - which
     * is what a crash or a {@code /stop} mid-staff-mode leaves behind. The toolbar
     * they were holding was saved as their real inventory by the server, so it is
     * cleared first.
     */
    public void restoreAfterRestart(Player player) {
        if (!stashes.has(player.getUniqueId()) || manager.isInStaffMode(player.getUniqueId())) {
            return;
        }
        player.getInventory().clear();
        lang.send(player, restoreStash(player)
                ? StaffMessages.MODE_STASH_RESTORED
                : StaffMessages.MODE_RESTORE_FAILED);
    }

    /** @return {@code false} if a held inventory could not be given back, and is still held */
    private boolean restoreStash(Player player) {
        UUID playerId = player.getUniqueId();
        byte[] contents = stashes.get(playerId).orElse(null);
        if (contents == null) {
            return true;
        }
        try {
            player.getInventory().setContents(ItemStack.deserializeItemsFromBytes(contents));
        } catch (RuntimeException e) {
            // Their items are still in the database: the row is only dropped below on
            // the success path, so a bad read can be retried after a fix rather than
            // silently throwing somebody's inventory away.
            plugin.getLogger().log(Level.SEVERE,
                    "Could not give " + player.getName() + " their held inventory back; "
                            + "it is kept in the database and will be tried again on their next login.", e);
            return false;
        }
        stashes.remove(playerId);
        flushSoon();
        return true;
    }

    private void giveToolbar(Player player) {
        for (Map.Entry<Integer, ToolbarItem> entry : settings.staffMode().toolbar().items().entrySet()) {
            ItemStack item = build(entry.getValue());
            if (item != null) {
                player.getInventory().setItem(entry.getKey(), item);
            }
        }
    }

    private ItemStack build(ToolbarItem spec) {
        Material material = Material.matchMaterial(spec.material());
        if (material == null || !material.isItem()) {
            plugin.getLogger().warning("staff.yml: '" + spec.material()
                    + "' in slot " + spec.slot() + " is not an item; that slot stays empty.");
            return null;
        }
        ItemStack item = ItemStack.of(material);
        if (spec.name() != null) {
            item.editMeta(meta -> meta.customName(ItemText.line(LangManager.colorize(spec.name()))));
        }
        if (!spec.lore().isEmpty()) {
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            for (String line : spec.lore()) {
                lore.add(ItemText.line(LangManager.colorize(line)));
            }
            item.editMeta(meta -> meta.lore(lore));
        }
        // The slot is stored on the item, so a use can find its binding again even if
        // the player has moved it - and so the item is recognisable as a toolbar item
        // to the listener that refuses to let it be dropped or stored.
        item.editPersistentDataContainer(
                data -> data.set(toolbarMarker, PersistentDataType.INTEGER, spec.slot()));
        return item;
    }

    /** @return the binding this item carries, or {@code null} if it is not a toolbar item */
    public ToolbarItem bindingOf(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        Integer slot = item.getPersistentDataContainer().get(toolbarMarker, PersistentDataType.INTEGER);
        return slot == null ? null : settings.staffMode().toolbar().at(slot).orElse(null);
    }

    /** @return whether this item is part of the staff toolbar, bound or not */
    public boolean isToolbarItem(ItemStack item) {
        return item != null && !item.isEmpty()
                && item.getPersistentDataContainer().has(toolbarMarker);
    }

    // ------------------------------------------------------------------
    // Vanish
    // ------------------------------------------------------------------

    /** Hides a player from everybody who may not see vanished staff. */
    public void applyVanish(Player player, VanishSource source) {
        if (!manager.vanish(player.getUniqueId(), source)) {
            return;
        }
        hideFromEveryone(player);
    }

    public void hideFromEveryone(Player player) {
        String seePermission = settings.vanish().seePermission();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player) && !other.hasPermission(seePermission)) {
                other.hidePlayer(plugin, player);
            }
        }
    }

    public void showToEveryone(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.showPlayer(plugin, player);
            }
        }
    }

    /**
     * Applies everybody's vanish state to a player who has just joined.
     *
     * <p>Both directions: they must not see staff who are hidden, and staff who are
     * hidden must not become visible to them just because they arrived after the
     * hiding was done.
     */
    public void applyVanishTo(Player joiner) {
        String seePermission = settings.vanish().seePermission();
        if (joiner.hasPermission(seePermission)) {
            return;
        }
        for (UUID hiddenId : manager.vanishedPlayers()) {
            Player hidden = Bukkit.getPlayer(hiddenId);
            if (hidden != null && !hidden.equals(joiner)) {
                joiner.hidePlayer(plugin, hidden);
            }
        }
    }

    // ------------------------------------------------------------------
    // Messaging
    // ------------------------------------------------------------------

    /** Node for every staff tool in this module. */
    public static final String STAFF_PERMISSION = "hcfcore.staff";

    /** Sends a message to everybody holding the staff node, console included. */
    public void announceToStaff(String key, Player about) {
        String message = lang.get(key, "player", about.getName());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(STAFF_PERMISSION) && !player.equals(about)) {
                player.sendMessage(message);
            }
        }
        plugin.getServer().getConsoleSender().sendMessage(message);
    }

    /** Sends a raw, already-formatted line to everybody on the staff channel. */
    public void sendToStaffChannel(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(STAFF_PERMISSION)) {
                player.sendMessage(message);
            }
        }
        plugin.getServer().getConsoleSender().sendMessage(message);
    }

    /**
     * @return the online player of that name, or {@code null} after telling the sender.
     *         A vanished colleague the sender may not see is not found: staff without
     *         the vanish see-permission must not open, freeze or reach them by name.
     */
    public Player requireOnline(CommandSender sender, String name) {
        Player target = VisiblePlayers.find(sender, name).orElse(null);
        if (target == null) {
            lang.send(sender, StaffMessages.PLAYER_NOT_FOUND, "player", name);
        }
        return target;
    }
}
