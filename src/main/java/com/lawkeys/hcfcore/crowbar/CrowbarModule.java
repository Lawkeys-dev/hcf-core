package com.lawkeys.hcfcore.crowbar;

import com.lawkeys.hcfcore.claim.ClaimManager;
import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.crowbar.command.CrowbarCommand;
import com.lawkeys.hcfcore.crowbar.listener.CrowbarListener;
import com.lawkeys.hcfcore.economy.EconomyModule;
import com.lawkeys.hcfcore.economy.EconomyResult;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.team.TeamType;
import com.lawkeys.hcfcore.util.ChunkPosition;
import com.lawkeys.hcfcore.util.Cooldowns;
import com.lawkeys.hcfcore.util.Durations;
import com.lawkeys.hcfcore.util.ItemText;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The crowbar (FEATURES.md section 17): a tool that takes a placed End portal
 * frame out, in the wilderness or in its holder's own claim, and nowhere else.
 *
 * <p>Scope as the project owner reduced it on 28/08/2026: frames only - no
 * spawners, and no spawner economy. The zone rule is in {@link CrowbarRules}, pure
 * and tested; this class adds the item, the cooldown, the cost, and the removal.
 *
 * <p>After the zone rule, the removal is offered to every other protection as an
 * ordinary {@link BlockBreakEvent}, which any of them may cancel - a resource node,
 * another plugin's region. The crowbar is a way past a block's unbreakability, not
 * past anybody's protection.
 */
public final class CrowbarModule {

    public static final String ADMIN_PERMISSION = "hcfcore.crowbar.admin";

    /** @param uses uses a new crowbar has; {@code 0} is no limit */
    public record Settings(boolean enabled, Material material, String name, List<String> lore, int uses,
                           long cooldownSeconds, double cost) {

        public Settings {
            Objects.requireNonNull(material, "material");
            lore = List.copyOf(lore);
        }

        static Settings defaults() {
            return new Settings(true, Material.GOLDEN_HOE, "{primary}&lCrowbar",
                    List.of("{muted}Right-click an End portal frame to take it out.",
                            "{muted}Only in the wilderness or your own claim.",
                            "{muted}Uses left: {secondary}%uses%"),
                    0, 0L, 0.0);
        }
    }

    private final Plugin plugin;
    private final LangManager lang;
    private final TeamModule teams;
    private final ClaimModule claims;
    private final EconomyModule economy;
    private final NamespacedKey usesKey;
    private static final String COOLDOWN_KEY = "crowbar";

    private final Cooldowns cooldowns = new Cooldowns();

    private volatile Settings settings = Settings.defaults();

    /** @param economy may be {@code null}: a crowbar with a cost is then free */
    public CrowbarModule(Plugin plugin, LangManager lang, TeamModule teams, ClaimModule claims, EconomyModule economy) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lang = Objects.requireNonNull(lang, "lang");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.economy = economy;
        this.usesKey = new NamespacedKey(plugin, "crowbar_uses");
    }

    public LangManager getLang() {
        return lang;
    }

    /** @return whole seconds before this player may use a crowbar again, or {@code 0} */
    public long cooldownLeft(java.util.UUID playerId) {
        return cooldowns.remaining(playerId, COOLDOWN_KEY, System.currentTimeMillis());
    }

    public Settings getSettings() {
        return settings;
    }

    public void enable() {
        reloadSettings();
        plugin.getServer().getPluginManager().registerEvents(new CrowbarListener(this), plugin);
        PluginCommand command = plugin.getServer().getPluginCommand("crowbar");
        if (command == null) {
            plugin.getLogger().severe("The 'crowbar' command is missing from plugin.yml.");
            return;
        }
        CrowbarCommand executor = new CrowbarCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    public void reloadSettings() {
        ConfigurationSection section = ConfigManager.loadFile(plugin, "crowbar.yml");
        Settings defaults = Settings.defaults();
        if (section == null) {
            this.settings = defaults;
            return;
        }
        Material material = Material.matchMaterial(section.getString("item.material", defaults.material().name()));
        if (material == null || !material.isItem()) {
            plugin.getLogger().warning("crowbar.yml: item.material is not an item; using "
                    + defaults.material() + ".");
            material = defaults.material();
        }
        List<String> lore = section.contains("item.lore") ? section.getStringList("item.lore") : defaults.lore();
        this.settings = new Settings(
                section.getBoolean("enabled", defaults.enabled()),
                material,
                section.getString("item.name", defaults.name()),
                lore,
                Math.max(0, section.getInt("uses", defaults.uses())),
                Math.max(0L, Durations.capSeconds(section.getLong("cooldown-seconds", defaults.cooldownSeconds()), "crowbar.yml: cooldown-seconds", plugin.getLogger()::warning)),
                Math.max(0.0, section.getDouble("cost", defaults.cost())));
    }

    /** @return a new crowbar with the configured number of uses */
    public ItemStack create() {
        ItemStack item = ItemStack.of(settings.material());
        item.editPersistentDataContainer(data -> data.set(usesKey, PersistentDataType.INTEGER, settings.uses()));
        describe(item, settings.uses());
        return item;
    }

    public boolean isCrowbar(ItemStack item) {
        return item != null && !item.isEmpty() && item.getPersistentDataContainer().has(usesKey);
    }

    private int usesLeft(ItemStack item) {
        Integer uses = item.getPersistentDataContainer().get(usesKey, PersistentDataType.INTEGER);
        return uses == null ? 0 : uses;
    }

    private void describe(ItemStack item, int uses) {
        String usesText = uses <= 0 ? lang.get(CrowbarMessages.UNLIMITED) : String.valueOf(uses);
        List<Component> lore = new ArrayList<>();
        for (String line : settings.lore()) {
            lore.add(ItemText.line(LangManager.colorize(line.replace("%uses%", usesText))));
        }
        item.editMeta(meta -> {
            meta.customName(ItemText.line(LangManager.colorize(settings.name())));
            meta.lore(lore);
        });
    }

    /**
     * Takes a frame out with the crowbar in the player's main hand, or says why not.
     *
     * <p>In this order, so nothing is paid or spent for a refusal: the zone, the
     * cooldown, every other protection's say, and only then the cost.
     */
    public void use(Player player, Block frame) {
        ItemStack held = player.getInventory().getItemInMainHand();
        Settings current = settings;
        if (!current.enabled()) {
            lang.send(player, CrowbarMessages.DISABLED);
            return;
        }
        if (!claims.bypassesProtection(player)) {
            CrowbarRules.Verdict verdict = judge(player, frame);
            if (verdict != CrowbarRules.Verdict.ALLOWED) {
                lang.send(player, switch (verdict) {
                    case ENEMY_CLAIM -> CrowbarMessages.ENEMY_CLAIM;
                    case SERVER_LAND -> CrowbarMessages.SERVER_LAND;
                    case WARZONE -> CrowbarMessages.WARZONE;
                    case ALLOWED -> throw new IllegalStateException();
                });
                return;
            }
        }
        long now = System.currentTimeMillis();
        long wait = cooldowns.remaining(player.getUniqueId(), COOLDOWN_KEY, now);
        if (wait > 0) {
            lang.send(player, CrowbarMessages.COOLDOWN, "time", Durations.formatWithSeconds(wait));
            return;
        }
        BlockBreakEvent check = new BlockBreakEvent(frame, player);
        check.setDropItems(false);
        Bukkit.getPluginManager().callEvent(check);
        if (check.isCancelled()) {
            lang.send(player, CrowbarMessages.PROTECTED);
            return;
        }
        if (current.cost() > 0 && economy != null && economy.getManager() != null) {
            EconomyResult paid = economy.getManager().withdraw(player.getUniqueId(), current.cost());
            if (!paid.isOk()) {
                lang.send(player, CrowbarMessages.CANNOT_PAY, "cost", economy.getManager().format(current.cost()));
                return;
            }
        }

        boolean hadEye = frame.getBlockData() instanceof EndPortalFrame endFrame && endFrame.hasEye();
        Location at = frame.getLocation().add(0.5, 0.5, 0.5);
        frame.setType(Material.AIR);
        frame.getWorld().dropItemNaturally(at, ItemStack.of(Material.END_PORTAL_FRAME));
        if (hadEye) {
            frame.getWorld().dropItemNaturally(at, ItemStack.of(Material.ENDER_EYE));
        }
        cooldowns.start(player.getUniqueId(), COOLDOWN_KEY, current.cooldownSeconds(), now);

        int after = CrowbarRules.afterUse(usesLeft(held));
        if (after == 0) {
            player.getInventory().setItemInMainHand(null);
            lang.send(player, CrowbarMessages.SPENT);
            return;
        }
        if (after != Integer.MAX_VALUE) {
            held.editPersistentDataContainer(data -> data.set(usesKey, PersistentDataType.INTEGER, after));
            describe(held, after);
            player.getInventory().setItemInMainHand(held);
        }
        lang.send(player, CrowbarMessages.REMOVED,
                "uses", after == Integer.MAX_VALUE ? lang.get(CrowbarMessages.UNLIMITED) : String.valueOf(after));
    }

    private CrowbarRules.Verdict judge(Player player, Block frame) {
        ClaimManager manager = claims.getManager();
        ChunkPosition chunk = ClaimModule.toChunk(frame.getLocation());
        Optional<Team> owner = manager == null ? Optional.empty() : manager.getOwner(chunk);
        Optional<Team> own = teams.getManager() == null
                ? Optional.empty()
                : teams.getManager().getTeamOf(player.getUniqueId());
        return CrowbarRules.judge(
                owner.map(Team::getId).orElse(null),
                owner.map(team -> team.getType() == TeamType.SYSTEM).orElse(false),
                manager != null && manager.isWarzone(chunk),
                own.map(Team::getId).orElse(null));
    }

    /** Forgets a player's cooldown when they leave. */
    public void forget(UUID playerId) {
        cooldowns.forget(playerId);
    }
}
