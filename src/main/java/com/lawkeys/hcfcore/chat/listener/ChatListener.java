package com.lawkeys.hcfcore.chat.listener;

import com.lawkeys.hcfcore.chat.ChatModule;
import com.lawkeys.hcfcore.chat.ChatSettings;
import com.lawkeys.hcfcore.chat.Positions;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.stats.PlayerStats;
import com.lawkeys.hcfcore.team.ChatChannel;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamManager;
import com.lawkeys.hcfcore.team.TeamMessages;
import com.lawkeys.hcfcore.util.ColorCodes;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Routes team and ally chat, and formats everything else.
 *
 * <p><strong>Why one listener and not two.</strong> {@code team/listener} was
 * written to hold the channel router and this module the public format, but both
 * would have had to act on the same event - one cancelling it, the other rendering
 * it - and the result would depend on their relative priorities, which is exactly
 * the kind of ordering nobody notices until chat goes missing. One listener decides
 * once: a non-public channel is routed and the event cancelled, otherwise the line
 * is formatted. {@code team/listener/package-info.java} points here.
 *
 * <p>The staff channel is the one other listener, and it runs first
 * ({@code StaffListener#onChat}, {@code LOW}): a line it takes arrives here
 * cancelled and is skipped, so a staff member's words never reach team chat.
 *
 * <p><strong>This event is asynchronous.</strong> Everything read here is either
 * immutable, a concurrent cache, or LuckPerms' own already-loaded data; the
 * recipients are resolved from {@code Bukkit.getPlayer} per id, which is a lookup
 * rather than a mutation. Nothing in the world is read or written from this thread -
 * the Paper documentation rules that out - so local chat reads positions from a copy
 * the main thread keeps ({@link Positions}), and the kill count is looked up without
 * the side effect of creating a stats entry.
 *
 * <p><strong>What a player types is shown as typed</strong> unless they hold
 * {@link ChatModule#COLOR_PERMISSION}: templates are coloured after their
 * placeholders are filled in, so without escaping, anybody could colour, obfuscate
 * or dress up a line as a staff message.
 */
public final class ChatListener implements Listener {

    private final ChatModule module;

    public ChatListener(ChatModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    /**
     * Team and ally chat is routed whatever {@code enabled} says: that switch hands
     * the <em>public</em> format back to the server, and chat.yml promises that
     * {@code /team chat} keeps working. Checking it first sent a team's private
     * lines to everybody on a server that had only wanted its own chat format.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        ChatSettings settings = module.getSettings();
        Player player = event.getPlayer();
        String typed = PlainTextComponentSerializer.plainText().serialize(event.message());
        String message = ChatModule.typedText(player, typed);

        if (routeToTeam(event, player, message, settings)) {
            return;
        }
        if (settings.enabled()) {
            formatPublic(event, player, message, settings);
        }
    }

    /**
     * Sends the message to the player's team or allies if that is where they are
     * talking.
     *
     * @return whether the message was routed, in which case the event is cancelled
     */
    private boolean routeToTeam(AsyncChatEvent event, Player player, String message,
                                ChatSettings settings) {
        TeamManager teams = module.getTeams() == null ? null : module.getTeams().getManager();
        if (teams == null) {
            return false;
        }
        ChatChannel channel = teams.getChatChannel(player.getUniqueId());
        if (channel == null || channel == ChatChannel.PUBLIC) {
            return false;
        }
        Optional<Team> team = teams.getTeamOf(player.getUniqueId());
        if (team.isEmpty()) {
            // No team to talk to: their line goes to public chat rather than nowhere.
            return false;
        }
        event.setCancelled(true);

        List<UUID> recipients = new ArrayList<>(team.get().getMembers().keySet());
        String key = channel == ChatChannel.ALLY ? TeamMessages.CHAT_FORMAT_ALLY : TeamMessages.CHAT_FORMAT_TEAM;
        if (channel == ChatChannel.ALLY) {
            for (UUID allyId : team.get().getAllies()) {
                teams.getTeam(allyId).ifPresent(ally -> recipients.addAll(ally.getMembers().keySet()));
            }
        }
        // get() colours the line already. Colouring it a second time would undo the
        // escaping: "&&c" becomes "&c" on the first pass and red on the second.
        String line = module.getLang().get(key,
                "player", player.getName(), "team", team.get().getName(), "message", message);
        for (UUID recipientId : recipients) {
            Player recipient = Bukkit.getPlayer(recipientId);
            if (recipient != null) {
                recipient.sendMessage(line);
            }
        }
        if (settings.logTeamChat()) {
            // The plugin's java.util.logging Logger: "All methods on Logger are
            // multi-thread safe" (Java SE javadoc), and this is not the main thread.
            module.getPlugin().getLogger().info(ColorCodes.strip(module.getLang().get(TeamMessages.CHAT_LOG,
                    "channel", channel.name(), "team", team.get().getName(),
                    "player", player.getName(), "message", message)));
        }
        return true;
    }

    /**
     * Replaces the server's default rendering with the configured template.
     *
     * <p>Done through {@code renderer} rather than by cancelling and re-sending, so
     * that other plugins listening after this one still see a live event, and so the
     * message keeps whatever signature the client attached to it.
     */
    private void formatPublic(AsyncChatEvent event, Player player, String message,
                              ChatSettings settings) {
        int kills = module.getStats() == null ? 0
                : module.getStats().getManager().find(player.getUniqueId())
                        .map(PlayerStats::getKills).orElse(0);
        String prefix = module.getDecorations().prefix(player);
        String suffix = module.getDecorations().suffix(player);
        String line = LangManager.colorize(
                settings.render(prefix, player.getName(), suffix, kills, message));

        if (settings.rangeBlocks() > 0) {
            restrictToRange(event, player, settings.rangeBlocks());
        }
        event.renderer((source, displayName, msg, viewer) ->
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacySection().deserialize(line));
    }

    /**
     * Drops viewers further away than the configured range.
     *
     * <p>Off by default: HCF chat is normally server-wide. When it is on, the console
     * is kept - a local chat that staff cannot read in the log is a moderation
     * problem, not a feature.
     */
    private void restrictToRange(AsyncChatEvent event, Player player, int range) {
        UUID from = player.getUniqueId();
        Positions positions = module.getPositions();
        event.viewers().removeIf(viewer -> viewer instanceof Player other
                && !positions.within(from, other.getUniqueId(), range));
    }
}
