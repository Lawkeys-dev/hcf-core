package com.lawkeys.hcfcore.effectcommand;

import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.util.EffectCaps;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Effect commands ({@code effect-commands.yml}): {@code /speed} and the like, each
 * giving its user an effect at a configured level until they die - or until they
 * type it again, which takes it off.
 *
 * <p>The commands are named by the file, so they cannot be declared in
 * {@code plugin.yml}: they are registered through Paper's command lifecycle event
 * ({@code LifecycleEvents.COMMANDS}, which a plugin may register a handler for in
 * {@code onEnable}, 26.2 javadoc), whose aliases never take over a command that
 * already exists. Which commands exist is therefore fixed at startup; what each gives
 * is read again by {@code /hcf reload}.
 *
 * <p>The effect given is infinite, not ambient, without particles: that signature
 * is how a second use recognises it to take it off, and one no other module's
 * effects carry. Death takes it, as it takes every effect.
 */
public final class EffectCommandModule {

    private final JavaPlugin plugin;
    private final LangManager lang;
    private final EffectCaps effectCaps;

    private volatile boolean enabled = true;
    private volatile EffectCommandSet commands = EffectCommandSet.empty();
    /** Each command's effect, resolved against the server's registry; absent when it has none such. */
    private volatile Map<String, PotionEffectType> types = Map.of();

    public EffectCommandModule(JavaPlugin plugin, LangManager lang, EffectCaps effectCaps) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lang = Objects.requireNonNull(lang, "lang");
        this.effectCaps = Objects.requireNonNull(effectCaps, "effectCaps");
    }

    public void enable() {
        reloadSettings();
        if (!enabled) {
            return;
        }
        EffectCommandSet atStartup = commands;
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            for (EffectCommand command : atStartup.all()) {
                Set<String> labels = event.registrar().register(command.name(),
                        "Toggles " + command.effect() + " " + command.level(), command.aliases(),
                        new Executor(command));
                for (String alias : command.aliases()) {
                    if (!labels.contains(alias)) {
                        plugin.getLogger().warning("effect-commands.yml: /" + alias + " (alias of /"
                                + command.name() + ") is another command's already; not registered.");
                    }
                }
            }
        });
    }

    public void reloadSettings() {
        ConfigurationSection section = ConfigManager.loadFile(plugin, "effect-commands.yml");
        this.enabled = section == null || section.getBoolean("enabled", true);
        List<EffectCommandSet.Entry> entries = new ArrayList<>();
        ConfigurationSection list = section == null ? null : section.getConfigurationSection("commands");
        if (list != null) {
            for (String name : list.getKeys(false)) {
                ConfigurationSection entry = list.getConfigurationSection(name);
                if (entry == null) {
                    continue;
                }
                entries.add(new EffectCommandSet.Entry(name, entry.getString("effect"), entry.getInt("level", 1),
                        entry.getStringList("aliases"), entry.getString("permission")));
            }
        }
        EffectCommandSet loaded = EffectCommandSet.of(entries,
                warning -> plugin.getLogger().warning("effect-commands.yml: " + warning));
        Map<String, PotionEffectType> resolved = new HashMap<>();
        for (EffectCommand command : loaded.all()) {
            NamespacedKey key = NamespacedKey.fromString(command.effect());
            PotionEffectType type = key == null ? null : Registry.MOB_EFFECT.get(key);
            if (type == null) {
                plugin.getLogger().warning("effect-commands.yml: /" + command.name() + ": this server has no effect '"
                        + command.effect() + "'; the command does nothing.");
            } else {
                resolved.put(command.name(), type);
            }
            if (plugin.getServer().getPluginManager().getPermission(command.permission()) == null) {
                plugin.getServer().getPluginManager().addPermission(new Permission(command.permission(),
                        "Use /" + command.name(), PermissionDefault.OP));
            }
        }
        this.commands = loaded;
        this.types = Map.copyOf(resolved);
    }

    /**
     * Gives the effect, or takes it off when the player already has it from this
     * command. At its level, within the effect caps of {@code limiters.yml}.
     */
    void toggle(Player player, String name) {
        EffectCommand command = enabled ? commands.get(name).orElse(null) : null;
        PotionEffectType type = types.get(name);
        if (command == null || type == null) {
            lang.send(player, EffectCommandMessages.SWITCHED_OFF);
            return;
        }
        PotionEffect current = player.getPotionEffect(type);
        String effectName = displayName(type);
        if (current != null && isOurs(current)) {
            player.removePotionEffect(type);
            lang.send(player, EffectCommandMessages.TAKEN, "effect", effectName);
            return;
        }
        int amplifier = effectCaps.allowed(type, command.level() - 1);
        if (amplifier < 0) {
            lang.send(player, EffectCommandMessages.FORBIDDEN, "effect", effectName);
            return;
        }
        // A stronger effect in place stays: the server holds this one back until it ends.
        player.addPotionEffect(new PotionEffect(type, PotionEffect.INFINITE_DURATION, amplifier, false, false, true));
        lang.send(player, EffectCommandMessages.GIVEN, "effect", effectName, "level", EffectCommandSet.roman(amplifier + 1),
                "command", name);
    }

    static boolean isOurs(PotionEffect effect) {
        return effect.isInfinite() && !effect.isAmbient() && !effect.hasParticles();
    }

    private static String displayName(PotionEffectType type) {
        String[] words = type.getKey().getKey().split("_");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return name.toString();
    }

    /** One registered command; what it gives is looked up on every use, so a reload holds. */
    private final class Executor implements BasicCommand {

        private final EffectCommand registered;

        private Executor(EffectCommand registered) {
            this.registered = registered;
        }

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            CommandSender sender = source.getSender();
            Player player = source.getExecutor() instanceof Player executor ? executor
                    : sender instanceof Player self ? self : null;
            if (player == null) {
                lang.send(sender, "general.player-only");
                return;
            }
            toggle(player, registered.name());
        }

        @Override
        public String permission() {
            return commands.get(registered.name()).map(EffectCommand::permission).orElse(registered.permission());
        }
    }
}
