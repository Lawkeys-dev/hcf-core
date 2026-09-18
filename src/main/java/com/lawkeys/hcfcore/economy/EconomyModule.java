package com.lawkeys.hcfcore.economy;

import com.lawkeys.hcfcore.command.KnownPlayers;
import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.database.dao.JdbcEconomyStore;
import com.lawkeys.hcfcore.economy.command.EconomyCommand;
import com.lawkeys.hcfcore.economy.command.PayCommand;
import com.lawkeys.hcfcore.economy.command.TeamBankSubCommands;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.startup.StartupBarrier;
import com.lawkeys.hcfcore.startup.StartupGate;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.team.command.TeamSubCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Wires the economy module into the server.
 *
 * <p>Enabled after {@code team/}, whose command it extends with the bank
 * subcommands that were left out when the team module shipped: crediting a team
 * bank means debiting a player, and there was no player balance until now.
 */
public final class EconomyModule {

    private final Plugin plugin;
    private final TeamModule teams;
    private final LangManager lang;
    private final StartupGate startup;

    private volatile EconomySettings settings = EconomySettings.defaults();
    private EconomyManager manager;
    private BukkitTask saveTask;

    public EconomyModule(Plugin plugin, TeamModule teams, LangManager lang, StartupGate startup) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.lang = Objects.requireNonNull(lang, "lang");
        this.startup = Objects.requireNonNull(startup, "startup");
    }

    public StartupGate getStartup() {
        return startup;
    }

    public EconomyManager getManager() {
        return manager;
    }

    public TeamModule getTeams() {
        return teams;
    }

    public LangManager getLang() {
        return lang;
    }

    public EconomySettings getSettings() {
        return settings;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public void enable(DataSource dataSource, long saveIntervalSeconds) {
        reloadSettings();

        EconomyStore store = dataSource == null
                ? EconomyStore.NO_OP
                : new JdbcEconomyStore(dataSource, message -> plugin.getLogger().info(message));

        this.manager = new EconomyManager(() -> settings, store);
        // A team bank reads in the same currency as a balance.
        if (teams.getManager() != null) {
            teams.getManager().setMoneyFormat(manager::format);
        }

        StartupBarrier.Load load = startup.expect("balances");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                manager.loadAll();
                plugin.getLogger().info("Loaded " + manager.getAccountCount() + " balances.");
                load.succeeded();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load balances.", e);
                load.failed();
            }
        });

        if (saveIntervalSeconds > 0) {
            long ticks = saveIntervalSeconds * 20L;
            this.saveTask = Bukkit.getScheduler()
                    .runTaskTimerAsynchronously(plugin, this::flushQuietly, ticks, ticks);
        }

        PayCommand payCommand = new PayCommand(this);
        registerCommand("pay", payCommand);
        registerCommand("balance", payCommand);
        registerCommand("eco", new EconomyCommand(this));

        // The team bank commands the team module had to leave out: they need a
        // player balance to move money from.
        for (TeamSubCommand subCommand : TeamBankSubCommands.all(this)) {
            teams.registerSubCommand(subCommand);
        }
    }

    private void registerCommand(String name, TabExecutor executor) {
        PluginCommand command = plugin.getServer().getPluginCommand(name);
        if (command == null) {
            plugin.getLogger().severe("The '" + name + "' command is missing from plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    public void reloadSettings() {
        this.settings = EconomySettingsLoader.load(
                ConfigManager.loadFile(plugin, "economy.yml"),
                warning -> plugin.getLogger().warning("economy.yml: " + warning));
    }

    public void disable() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (manager == null) {
            return;
        }
        try {
            int written = manager.flush();
            if (written > 0) {
                plugin.getLogger().info("Saved " + written + " balances on shutdown.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save balances on shutdown", e);
        }
    }

    private void flushQuietly() {
        try {
            manager.flush();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Periodic balance save failed; the affected accounts stay queued.", e);
        }
    }

    /**
     * Resolves a player name to a uuid, offline players included, since a balance
     * outlives the session that earned it.
     *
     * <p>Offline, through the server's name cache only. This used to walk
     * {@code Bukkit#getOfflinePlayers()}, which lists the player data folder on disk
     * at every call (read in the 26.2 server sources) - on the main thread, for every
     * {@code /pay}, {@code /balance} and name-based Vault call naming somebody
     * offline, at a cost growing with every player the server has ever seen (found in
     * the command review, 15/09/2026). The cache never waits on the disk or the
     * network; a name it does not know resolves to nobody, as a typo does.
     */
    public Optional<UUID> resolvePlayer(String name) {
        return KnownPlayers.idOf(name);
    }

    /** @return a display name for a uuid, falling back to the uuid itself. */
    public String nameOf(UUID player) {
        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            return online.getName();
        }
        String name = Bukkit.getOfflinePlayer(player).getName();
        return name != null ? name : player.toString();
    }

    /**
     * Parses an amount typed by a player.
     *
     * @return the amount, or empty if it is not a usable number
     */
    public static Optional<Double> parseAmount(String input) {
        try {
            double amount = Double.parseDouble(input);
            return Double.isFinite(amount) ? Optional.of(amount) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
