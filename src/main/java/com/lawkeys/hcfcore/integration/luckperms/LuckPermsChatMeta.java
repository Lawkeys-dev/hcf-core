package com.lawkeys.hcfcore.integration.luckperms;

import com.lawkeys.hcfcore.chat.ChatDecorations;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Reads a player's prefix and suffix from LuckPerms.
 *
 * <p><strong>This is the only class in the plugin that names a LuckPerms type</strong>,
 * and it is loaded only after {@link LuckPermsIntegration} has confirmed the plugin
 * is present - the same arrangement as {@code VaultEconomyBridge}, and for the same
 * reason: loading a class whose interfaces are absent throws
 * {@code NoClassDefFoundError}, which would take the whole plugin down on a server
 * that simply chose not to install LuckPerms.
 *
 * <p>Read at the moment the message is sent rather than cached, which is what
 * FEATURES.md section 9 asks for: a rank change shows in the very next line the
 * player types, and no prefix is ever stored in two places.
 *
 * <p>The lookup is not a query. {@code getPlayerAdapter(...).getMetaData(player)}
 * reads the data LuckPerms already holds for an online player, using their active
 * query options - verified in the LuckPerms 5.5 API sources, the version pinned in
 * build.gradle.kts. Nothing here touches the database, so it is safe on the chat
 * thread.
 */
public final class LuckPermsChatMeta implements ChatDecorations {

    private final LuckPerms luckPerms;

    LuckPermsChatMeta(LuckPerms luckPerms) {
        this.luckPerms = Objects.requireNonNull(luckPerms, "luckPerms");
    }

    static ChatDecorations create() {
        return new LuckPermsChatMeta(LuckPermsProvider.get());
    }

    @Override
    public String prefix(Player player) {
        String prefix = meta(player).getPrefix();
        return prefix == null ? "" : prefix;
    }

    @Override
    public String suffix(Player player) {
        String suffix = meta(player).getSuffix();
        return suffix == null ? "" : suffix;
    }

    /** The primary group's weight, read from the groups LuckPerms holds loaded. */
    @Override
    public int weight(Player player) {
        String primary = meta(player).getPrimaryGroup();
        if (primary == null) {
            return 0;
        }
        net.luckperms.api.model.group.Group group = luckPerms.getGroupManager().getGroup(primary);
        return group == null ? 0 : group.getWeight().orElse(0);
    }

    private CachedMetaData meta(Player player) {
        return luckPerms.getPlayerAdapter(Player.class).getMetaData(player);
    }
}
