package com.lawkeys.hcfcore;

import com.lawkeys.hcfcore.util.Durations;
import com.lawkeys.hcfcore.chat.ChatModule;
import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.command.HcfCommand;
import com.lawkeys.hcfcore.dtr.DtrModule;
import com.lawkeys.hcfcore.economy.EconomyModule;
import com.lawkeys.hcfcore.general.GeneralModule;
import com.lawkeys.hcfcore.events.EventModule;
import com.lawkeys.hcfcore.integration.lunar.LunarIntegration;
import com.lawkeys.hcfcore.integration.lunar.LunarSources;
import com.lawkeys.hcfcore.integration.vault.VaultIntegration;
import com.lawkeys.hcfcore.killstreak.KillstreakModule;
import com.lawkeys.hcfcore.ability.AbilityModule;
import com.lawkeys.hcfcore.effectcommand.EffectCommandModule;
import com.lawkeys.hcfcore.enchant.EnchantModule;
import com.lawkeys.hcfcore.hologram.HologramModule;
import com.lawkeys.hcfcore.limiter.LimiterModule;
import com.lawkeys.hcfcore.lives.LivesModule;
import com.lawkeys.hcfcore.crowbar.CrowbarModule;
import com.lawkeys.hcfcore.redeem.RedeemModule;
import com.lawkeys.hcfcore.schedule.ScheduleModule;
import com.lawkeys.hcfcore.settings.SettingsModule;
import com.lawkeys.hcfcore.warmup.WarmupModule;
import com.lawkeys.hcfcore.kit.KitModule;
import com.lawkeys.hcfcore.pvp.PvpModule;
import com.lawkeys.hcfcore.pvpclass.ClassModule;
import com.lawkeys.hcfcore.resourcenode.ResourceNodeModule;
import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.database.DatabaseManager;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.mode.GameMode;
import com.lawkeys.hcfcore.phase.PhaseModule;
import com.lawkeys.hcfcore.startup.StartupBarrier;
import com.lawkeys.hcfcore.staff.StaffModule;
import com.lawkeys.hcfcore.stats.StatsModule;
import com.lawkeys.hcfcore.startup.StartupGate;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.ui.UiModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;

/**
 * Main plugin class for HCFCore.
 *
 * <p>This plugin is designed to run in two distinct game modes on separate
 * server instances (see ARCHITECTURE.md section 7 "Modes de jeu configurables"):
 * <ul>
 *     <li>HCF mode (default): all mechanics enabled (Lives system, DTR, claims, etc.)</li>
 *     <li>Kitmap mode ({@code kitmap-mode: true} in config.yml): a subset of mechanics
 *     is disabled (at minimum, the Lives system).</li>
 * </ul>
 *
 * <p>Modules that are conditional on the active game mode MUST check
 * {@link GameMode} state at {@link #onEnable()} time and skip initialization
 * entirely if disabled for the current mode - see CONTRIBUTING.md section 3
 * (maximum configurability) and ARCHITECTURE.md section 8.
 */
public final class HCFCore extends JavaPlugin {

    private static HCFCore instance;

    private ConfigManager configManager;
    private LangManager langManager;
    private DatabaseManager databaseManager;
    private GameMode gameMode;
    private StartupGate startupGate;
    private TeamModule teamModule;
    private ClaimModule claimModule;
    private DtrModule dtrModule;
    private PvpModule pvpModule;
    private EconomyModule economyModule;
    private EventModule eventModule;
    private ResourceNodeModule resourceNodeModule;
    private PhaseModule phaseModule;
    private StaffModule staffModule;
    private StatsModule statsModule;
    private ChatModule chatModule;
    private LimiterModule limiterModule;
    private WarmupModule warmupModule;
    private ScheduleModule scheduleModule;
    private SettingsModule settingsModule;
    private RedeemModule redeemModule;
    private CrowbarModule crowbarModule;
    private LivesModule livesModule;
    private HologramModule hologramModule;
    private EnchantModule enchantModule;
    private ClassModule classModule;
    private EffectCommandModule effectCommandModule;
    private AbilityModule abilityModule;
    private LunarIntegration lunarIntegration;
    private UiModule uiModule;
    private GeneralModule generalModule;
    private KitModule kitModule;
    private KillstreakModule killstreakModule;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Load and validate configuration first - every other module depends on it.
        this.configManager = new ConfigManager(this);
        this.configManager.load();

        // 2. Messages, so any later startup failure can already be reported properly.
        this.langManager = new LangManager(this);
        this.langManager.load(configManager.getConfig().getString("language", "en"));

        // 3. Resolve active game mode (HCF vs Kitmap) from config before initializing
        //    any mode-conditional module.
        this.gameMode = GameMode.fromConfig(this.configManager);
        getLogger().info("Starting in game mode: " + this.gameMode);

        // The startup gate goes up before anything is loaded: players are refused,
        // and data commands answer "still loading", until every load declared below
        // has landed - for good if one fails (package startup).
        this.startupGate = new StartupGate(this, this.langManager);
        getServer().getPluginManager().registerEvents(this.startupGate, this);

        // Everything after the gate is itself a load. A crash anywhere in it - a
        // dependency that will not link, a module that throws - would otherwise
        // disable the plugin, take the gate down with it and open the server with no
        // HCF at all. It fails the load instead, and the server stays closed until it
        // is restarted: the rule the project owner set for any failed load.
        StartupBarrier.Load startup = this.startupGate.expect("the plugin's startup");
        try {
            enableModules();
            startup.succeeded();
            getLogger().info("HCFCore enabled.");
        } catch (RuntimeException | LinkageError e) {
            getLogger().log(java.util.logging.Level.SEVERE,
                    "HCFCore could not start. The server stays closed to players until it is restarted.", e);
            startup.failed();
            this.startupGate.seal();
        }
    }

    /** Every module, in the order their dependencies need. */
    private void enableModules() {
        // 4. Initialize persistence layer (HikariCP pool). Schema migrations are not
        //    run here: each module applies its own versioned migrations from its own
        //    async startup task (ARCHITECTURE.md section 4).
        this.databaseManager = new DatabaseManager(this, this.configManager);
        this.databaseManager.init();
        // A pool that would not open counts as a failed load. The modules then fall
        // back to memory-only stores whose own loads succeed on nothing, and
        // letting players in would mean playing on an empty cache that saves nowhere.
        StartupBarrier.Load database = this.startupGate.expect("the database");
        if (this.databaseManager.isAvailable()) {
            database.succeeded();
        } else {
            database.failed();
        }

        registerAdminCommand();

        // 5. Soft-dependency integrations (Vault, LuckPerms, Apollo) each detect
        //    their plugin at runtime and do nothing without it - see ARCHITECTURE.md
        //    section 11. None can start here: Vault publishes the economy module,
        //    LuckPerms feeds the chat module (ChatModule reads it), and Apollo reads
        //    most of the others, so each starts once what it needs exists.

        // 6. Initialize feature modules.
        //    Order matters: team -> claim -> dtr -> pvp are tightly coupled
        //    (see CONTRIBUTING.md section 4, "Module order").
        long saveInterval = Durations.capSeconds(configManager.getConfig().getLong("storage.save-interval-seconds", 300L), "config.yml: storage.save-interval-seconds", getLogger()::warning);
        DataSource dataSource =
                databaseManager.isAvailable() ? databaseManager.getDataSource() : null;

        // The countdowns that damage or movement cancels. Before every feature module,
        // because two of them share it - claim/ (/team hq, /team stuck) and general/
        // (/spawn, /logout) - and one shared set is what stops two running at once.
        this.warmupModule = new WarmupModule(this, this.langManager);
        this.warmupModule.enable();

        // Statistics come first among the feature modules: the chat format, the
        // scoreboard, the leaderboards and the killstreak rewards all read from it,
        // and it depends on nothing itself.
        this.statsModule = new StatsModule(this, this.langManager, this.startupGate);
        this.statsModule.enable(dataSource, saveInterval);

        this.teamModule = new TeamModule(this, this.langManager, this.startupGate);
        this.teamModule.enable(dataSource, saveInterval);

        // Claims come after teams and depend on them: territory belongs to a team,
        // and the /team claim family is grafted onto the team command.
        this.claimModule = new ClaimModule(this, this.teamModule, this.langManager, this.startupGate,
                this.warmupModule);
        this.claimModule.enable(dataSource, saveInterval);

        // DTR comes last of the three: enabling it installs the real
        // RaidabilityPolicy on the claim module, which is what makes territory
        // protection follow DTR (FEATURES.md section 3). Before this line, and if
        // this module is ever removed, claims are simply always protected.
        this.dtrModule = new DtrModule(this, this.teamModule, this.claimModule, this.langManager,
                this.startupGate);
        this.dtrModule.enable(dataSource, saveInterval);

        // PvP consults the claim module for safe zones. Unlike the claim-to-dtr
        // relationship, no seam is needed here: claim/ already exists when this
        // runs, so a direct call is simpler (ARCHITECTURE.md section 14).
        this.pvpModule = new PvpModule(this, this.claimModule, this.langManager, this.startupGate);
        this.pvpModule.enable(dataSource, saveInterval);

        // Economy comes last of the current modules: it extends /team with the
        // bank subcommands that team/ had to leave out, which is only possible
        // once a player wallet exists to move the money from.
        this.economyModule = new EconomyModule(this, this.teamModule, this.langManager, this.startupGate);
        this.economyModule.enable(dataSource, saveInterval);

        // Capture events credit the team module for a win and read team membership
        // to decide who holds a zone, so they come after it. Kill the King lives
        // here too: its event zone is the claim module's warzone, and the items it
        // takes from a King are stashes loaded at startup like any other data.
        this.eventModule = new EventModule(this, this.teamModule, this.claimModule, this.langManager,
                this.startupGate);
        this.eventModule.enable(dataSource);

        // Resource nodes are the other family of events (ARCHITECTURE.md section 9):
        // a mountain that refills, with nothing to capture. It comes after the
        // capture module and the claim module because it closes a seam in each -
        // its regions become unclaimable, and its refills appear in /events - but
        // it depends on neither being present to work.
        this.resourceNodeModule = new ResourceNodeModule(this, this.claimModule, this.langManager);
        this.resourceNodeModule.enable(this.eventModule);

        // SOTW and EOTW change what combat, deaths, claims and raids do, through
        // seams those modules declared - so this comes after all of them, and after
        // DTR in particular: it wraps the raid policy DTR installed rather than
        // replacing it.
        this.phaseModule = new PhaseModule(this, this.langManager, this.startupGate);
        this.phaseModule.enable(dataSource, this.claimModule, this.dtrModule, this.pvpModule, this.eventModule);

        // Staff tools come after the claim module, whose BuildOverride seam they
        // answer so that /staffbuild lifts territory protection. They work without
        // it: the toggle then simply has nothing to lift.
        this.staffModule = new StaffModule(this, this.claimModule, this.langManager, this.startupGate);
        this.staffModule.enable(dataSource, saveInterval);

        // Chat comes after teams and stats, both of which it reads: the team channel
        // decides whether a line is routed, and the kill count goes in the line. It
        // runs without either.
        this.chatModule = new ChatModule(this, this.teamModule, this.statsModule, this.langManager);
        this.chatModule.enable();

        // Kits and refill signs. Independent of everything above.
        this.kitModule = new KitModule(this, this.langManager, this.startupGate);
        this.kitModule.enable(dataSource, saveInterval);

        // Killstreak rewards install themselves on the stats module's observer seam,
        // so the two share nothing but an integer.
        this.killstreakModule = new KillstreakModule(this, this.langManager, this.statsModule);
        this.killstreakModule.enable();

        // Enchantment and potion caps, and blocks per claim - all idle until an
        // operator writes a limit: the file ships with none. Reads territory.
        this.limiterModule = new LimiterModule(this, this.langManager, this.startupGate, this.claimModule);
        this.limiterModule.enable(dataSource, saveInterval);

        // The crowbar reads territory and teams to decide where it may be used, and
        // the economy for its optional cost - all three exist by now.
        this.crowbarModule = new CrowbarModule(this, this.langManager, this.teamModule, this.claimModule,
                this.economyModule);
        this.crowbarModule.enable();

        // Lives lift deathbans, through the pvp module's waiver seam. Not started at
        // all in kitmap mode, as decided on 28/08/2026: kitmap has no deathban.
        if (this.gameMode.isHcf()) {
            this.livesModule = new LivesModule(this, this.langManager, this.startupGate, this.pvpModule);
            this.livesModule.enable(dataSource, saveInterval);
        } else {
            // Declared in plugin.yml for both modes; without this, kitmap would answer
            // with the raw usage line instead of saying why there is nothing here.
            for (String name : new String[] {"lives", "revive"}) {
                PluginCommand unused = getCommand(name);
                if (unused != null) {
                    unused.setExecutor((sender, command, label, args) -> {
                        this.langManager.send(sender, com.lawkeys.hcfcore.lives.LivesMessages.DISABLED);
                        return true;
                    });
                }
            }
        }

        // Custom enchants live on items; independent of every other module.
        this.enchantModule = new EnchantModule(this, this.langManager);
        this.enchantModule.enable();

        // Classes read teams (whom a Bard buffs) and pvp/ (whom a debuff may reach),
        // both running by now, so they call them directly (ARCHITECTURE.md section 14).
        this.classModule = new ClassModule(this, this.langManager, this.teamModule, this.pvpModule);
        this.classModule.enable();
        // The effect caps of limiters.yml: the modules that give effects themselves
        // ask before giving, so they still recognise what they gave.
        this.enchantModule.setEffectCaps(this.limiterModule::allowedAmplifier);
        this.classModule.setEffectCaps(this.limiterModule::allowedAmplifier);
        if (this.eventModule.getKing() != null) {
            this.eventModule.getKing().setEffectCaps(this.limiterModule::allowedAmplifier);
        }

        // Partner items (abilities.yml): reads classes for the archer tag, pvp for who
        // may be harmed, events for where abilities are refused - all started above.
        this.abilityModule = new AbilityModule(this, this.langManager, this.teamModule, this.claimModule,
                this.pvpModule, this.classModule, this.eventModule);
        this.abilityModule.enable();
        // A Citadel refuses partner items: events/ asks this module what one is.
        this.eventModule.setPartnerItems(this.abilityModule::isPartnerItem);
        // Nor do they start the pearl cooldown or an item cooldown: they have their own.
        this.pvpModule.setPartnerItems(this.abilityModule::isPartnerItem);
        // /cooldown reset: partner items' cooldowns and pvp.yml's, whichever module keeps them.
        org.bukkit.command.PluginCommand cooldown = getCommand("cooldown");
        if (cooldown != null) {
            com.lawkeys.hcfcore.command.CooldownCommand executor = new com.lawkeys.hcfcore.command.CooldownCommand(
                    this.langManager, this.abilityModule, this.pvpModule);
            cooldown.setExecutor(executor);
            cooldown.setTabCompleter(executor);
        } else {
            getLogger().severe("The 'cooldown' command is missing from plugin.yml.");
        }

        // /speed and the like: an effect until death (effect-commands.yml).
        this.effectCommandModule = new EffectCommandModule(this, this.langManager,
                this.limiterModule::allowedAmplifier);
        this.effectCommandModule.enable();

        // Holograms read the leaderboards of the stats module, and nothing else.
        this.hologramModule = new HologramModule(this, this.langManager, this.startupGate, this.statsModule);
        this.hologramModule.enable(dataSource, saveInterval);
        // The capture zones' holograms: events/ answers hologram/'s seam.
        this.hologramModule.addSource(this.eventModule::zoneHolograms);

        // Redeem codes. Independent of everything: a reward is console commands.
        this.redeemModule = new RedeemModule(this, this.langManager, this.startupGate);
        this.redeemModule.enable(dataSource, saveInterval);

        // Tips, daily schedules, custom timers and the key-all. Independent of the
        // rest; read by the scoreboard, which shows the timers.
        this.scheduleModule = new ScheduleModule(this, this.langManager);
        this.scheduleModule.enable();

        // The utility commands of section 10. Nothing here is HCF-specific; it reads
        // the claim module only to borrow its combat guard, so that /spawn and /top
        // refuse a tagged player without this module knowing what combat is.
        this.generalModule = new GeneralModule(this, this.langManager, this.claimModule, this.warmupModule);
        this.generalModule.enable();
        // The King may not reach spawn by /spawn either: events/ answers the guard.
        if (this.eventModule != null && this.eventModule.getKing() != null) {
            this.generalModule.setSpawnGuard(this.eventModule.getKing()::refuseSpawn);
        }
        // A tagged player's /logout would be a combat log: pvp/ refuses it.
        if (this.pvpModule != null) {
            this.generalModule.setLogoutGuard(this.pvpModule::refuseLogout);
        }
        // Allies fight only in event areas (pvp.yml, friendly-fire): events/ says
        // where those are (ARCHITECTURE.md section 14).
        if (this.eventModule != null && this.pvpModule != null) {
            this.pvpModule.setAllyCombatZone(this.eventModule::coversAllyCombat);
        }

        // The scoreboard reads from every module above and is read by none, so it
        // comes last. Anything missing simply leaves its placeholders empty, and a
        // row that is entirely empty is dropped.
        this.uiModule = new UiModule(this, this.langManager, this.teamModule, this.dtrModule,
                this.pvpModule, this.economyModule, this.statsModule, this.claimModule,
                this.phaseModule, this.scheduleModule, this.eventModule);
        this.uiModule.enable();
        // The class rows (%class_line%...) are drawn by the class module itself.
        this.uiModule.addPlaceholderSource(this.classModule::placeholders);

        // Player settings switch things the scoreboard, the utility commands and the
        // schedule do, through their seams - so it comes after all three.
        this.settingsModule = new SettingsModule(this, this.langManager, this.startupGate,
                this.uiModule, this.generalModule, this.scheduleModule);
        this.settingsModule.enable(dataSource, saveInterval);

        // Publish the economy to other plugins, if Vault is here to publish it to.
        // Soft dependency: a server without Vault loses nothing but the exposure.
        VaultIntegration.register(this, this.economyModule);

        // Lunar Client, through Apollo when it is installed; reads the modules above.
        this.lunarIntegration = LunarIntegration.start(this, this.langManager, new LunarSources(
                this.teamModule, this.claimModule, this.dtrModule, this.pvpModule, this.eventModule,
                this.abilityModule, this.warmupModule, this.classModule, this.crowbarModule));

        // Every module has declared its load by now. Sealing is what lets the gate
        // open: before it, a fast load could not tell that another was still to come.
        this.startupGate.seal();
    }

    @Override
    public void onDisable() {
        // Modules are shut down in reverse dependency order, and before the pool
        // they persist through.
        if (this.settingsModule != null) {
            this.settingsModule.disable();
        }
        if (this.redeemModule != null) {
            this.redeemModule.disable();
        }
        if (this.livesModule != null) {
            this.livesModule.disable();
        }
        if (this.hologramModule != null) {
            this.hologramModule.disable();
        }
        if (this.abilityModule != null) {
            this.abilityModule.disable();
        }
        if (this.classModule != null) {
            this.classModule.disable();
        }
        if (this.enchantModule != null) {
            this.enchantModule.disable();
        }
        if (this.lunarIntegration != null) {
            this.lunarIntegration.disable();
        }
        if (this.limiterModule != null) {
            this.limiterModule.disable();
        }
        if (this.staffModule != null) {
            this.staffModule.disable();
        }
        if (this.kitModule != null) {
            this.kitModule.disable();
        }
        if (this.generalModule != null) {
            this.generalModule.disable();
        }
        if (this.uiModule != null) {
            this.uiModule.disable();
        }
        if (this.scheduleModule != null) {
            this.scheduleModule.disable();
        }
        if (this.chatModule != null) {
            this.chatModule.disable();
        }
        if (this.statsModule != null) {
            this.statsModule.disable();
        }
        if (this.phaseModule != null) {
            this.phaseModule.disable();
        }
        if (this.resourceNodeModule != null) {
            this.resourceNodeModule.disable();
        }
        if (this.eventModule != null) {
            this.eventModule.disable();
        }
        if (this.economyModule != null) {
            this.economyModule.disable();
        }
        if (this.pvpModule != null) {
            this.pvpModule.disable();
        }
        if (this.dtrModule != null) {
            this.dtrModule.disable();
        }
        if (this.claimModule != null) {
            this.claimModule.disable();
        }
        if (this.teamModule != null) {
            this.teamModule.disable();
        }
        if (this.warmupModule != null) {
            this.warmupModule.disable();
        }
        if (this.databaseManager != null) {
            this.databaseManager.shutdown();
        }
        getLogger().info("HCFCore disabled.");
    }

    private void registerAdminCommand() {
        PluginCommand command = getCommand("hcf");
        if (command == null) {
            getLogger().severe("The 'hcf' command is missing from plugin.yml; /hcf reload is unavailable.");
            return;
        }
        HcfCommand executor = new HcfCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    /**
     * Re-reads every configuration and language file, applying the new values to
     * the running modules without a restart (ARCHITECTURE.md section 2).
     *
     * <p>The game mode is deliberately <em>not</em> re-resolved: switching between
     * HCF and Kitmap decides which modules exist at all, and modules that skipped
     * initialization cannot be brought up mid-session. That needs a restart.
     */
    public void reloadEverything() {
        configManager.load();
        langManager.load(configManager.getConfig().getString("language", "en"));
        if (teamModule != null) {
            teamModule.reloadSettings();
        }
        if (claimModule != null) {
            claimModule.reloadSettings();
        }
        if (dtrModule != null) {
            dtrModule.reloadSettings();
        }
        if (pvpModule != null) {
            pvpModule.reloadSettings();
        }
        if (economyModule != null) {
            economyModule.reloadSettings();
        }
        if (eventModule != null) {
            eventModule.reloadSettings();
        }
        if (resourceNodeModule != null) {
            resourceNodeModule.reloadSettings();
        }
        if (phaseModule != null) {
            phaseModule.reloadSettings();
        }
        if (staffModule != null) {
            staffModule.reloadSettings();
        }
        if (chatModule != null) {
            chatModule.reloadSettings();
        }
        if (uiModule != null) {
            uiModule.reloadSettings();
        }
        if (generalModule != null) {
            generalModule.reloadSettings();
        }
        if (kitModule != null) {
            kitModule.reloadSettings();
        }
        if (killstreakModule != null) {
            killstreakModule.reloadSettings();
        }
        if (limiterModule != null) {
            limiterModule.reloadSettings();
        }
        if (scheduleModule != null) {
            scheduleModule.reloadSettings();
        }
        if (settingsModule != null) {
            settingsModule.reloadSettings();
        }
        if (redeemModule != null) {
            redeemModule.reloadSettings();
        }
        if (crowbarModule != null) {
            crowbarModule.reloadSettings();
        }
        if (livesModule != null) {
            livesModule.reloadSettings();
        }
        if (hologramModule != null) {
            hologramModule.reloadSettings();
        }
        if (classModule != null) {
            classModule.reloadSettings();
        }
        if (enchantModule != null) {
            enchantModule.reloadSettings();
        }
        if (effectCommandModule != null) {
            effectCommandModule.reloadSettings();
        }
        if (abilityModule != null) {
            abilityModule.reloadSettings();
        }
        if (lunarIntegration != null) {
            lunarIntegration.reload();
        }
    }

    public static HCFCore getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LangManager getLangManager() {
        return langManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public TeamModule getTeamModule() {
        return teamModule;
    }

    public ClaimModule getClaimModule() {
        return claimModule;
    }

    public DtrModule getDtrModule() {
        return dtrModule;
    }

    public PvpModule getPvpModule() {
        return pvpModule;
    }

    public EconomyModule getEconomyModule() {
        return economyModule;
    }

    public EventModule getEventModule() {
        return eventModule;
    }

    public ResourceNodeModule getResourceNodeModule() {
        return resourceNodeModule;
    }

    public PhaseModule getPhaseModule() {
        return phaseModule;
    }

    public StaffModule getStaffModule() {
        return staffModule;
    }

    public StatsModule getStatsModule() {
        return statsModule;
    }

    public ChatModule getChatModule() {
        return chatModule;
    }

    public UiModule getUiModule() {
        return uiModule;
    }

    public GeneralModule getGeneralModule() {
        return generalModule;
    }

    public KitModule getKitModule() {
        return kitModule;
    }

    public KillstreakModule getKillstreakModule() {
        return killstreakModule;
    }
}
