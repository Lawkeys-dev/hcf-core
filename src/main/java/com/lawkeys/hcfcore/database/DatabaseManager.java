package com.lawkeys.hcfcore.database;

import com.lawkeys.hcfcore.HCFCore;
import com.lawkeys.hcfcore.config.ConfigManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;

/**
 * Manages the HikariCP connection pool and picks between MySQL (production)
 * and SQLite (solo/dev) based on {@code storage.type} in config.yml, without
 * requiring any application-code change between the two (see ARCHITECTURE.md
 * section 3-4).
 *
 * <p>Per CONTRIBUTING.md section 3 (async first): NOTHING in this class or its DAOs
 * should ever be called from the main server thread. Callers are responsible
 * for dispatching to an async scheduler task before touching the database.
 * In-memory caches (per-module Managers) are the runtime source of truth;
 * this class only handles persistence.
 */
public class DatabaseManager {

    /** Overrides {@code storage.mysql.password} when set and not blank. */
    public static final String PASSWORD_VARIABLE = "HCFCORE_MYSQL_PASSWORD";

    private final HCFCore plugin;
    private final ConfigManager configManager;
    private HikariDataSource dataSource;

    public DatabaseManager(HCFCore plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void init() {
        String storageType = configManager.getConfig().getString("storage.type", "sqlite");

        HikariConfig hikariConfig = new HikariConfig();

        if ("mysql".equalsIgnoreCase(storageType)) {
            String host = configManager.getConfig().getString("storage.mysql.host", "localhost");
            int port = configManager.getConfig().getInt("storage.mysql.port", 3306);
            String database = configManager.getConfig().getString("storage.mysql.database", "hcfcore");
            String username = configManager.getConfig().getString("storage.mysql.username", "root");
            // The password may come from the environment, so that a real one never
            // has to sit in a config.yml (CONTRIBUTING.md section 5). This comment and the
            // one in config.yml said so from the start, and nothing read it until
            // 13/09/2026. Set and not blank, the variable wins over the file.
            String fromEnvironment = System.getenv(PASSWORD_VARIABLE);
            boolean environment = fromEnvironment != null && !fromEnvironment.isBlank();
            String password = environment
                    ? fromEnvironment
                    : configManager.getConfig().getString("storage.mysql.password", "");
            plugin.getLogger().info("MySQL password read from "
                    + (environment ? "the " + PASSWORD_VARIABLE + " environment variable." : "config.yml."));

            hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
            // Connector/J properties from storage.mysql.properties, as they are. The
            // URL used to force useSSL=false (legacy for sslMode=DISABLED) and
            // autoReconnect=true: MySQL 8's default login, caching_sha2_password,
            // then refused every connection - over plain text the driver will not
            // fetch the server's key, "Public Key Retrieval is not allowed" (found on
            // a real MySQL 8.4, 13/09/2026) - and the Connector/J documentation calls
            // autoReconnect "not recommended"; the pool replaces dead connections
            // itself. Without the section, the driver's own default: sslMode PREFERRED.
            ConfigurationSection properties = configManager.getConfig()
                    .getConfigurationSection("storage.mysql.properties");
            if (properties == null) {
                hikariConfig.addDataSourceProperty("sslMode", "PREFERRED");
            } else {
                for (String key : properties.getKeys(false)) {
                    hikariConfig.addDataSourceProperty(key, String.valueOf(properties.get(key)));
                }
            }
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        } else {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            File dbFile = new File(dataFolder, "data.db");
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
            // SQLite only supports a single writer connection at a time.
            hikariConfig.setMaximumPoolSize(1);
        }

        hikariConfig.setPoolName("HCFCore-HikariPool");

        // A database that will not come up must not take the server down with it:
        // the plugin stays enabled and the console stays usable. It does not open
        // to players either - HCFCore counts the pool as a failed load, and the
        // startup gate keeps everyone out until the server is restarted.
        // LinkageError too: a JDBC driver whose native library will not link throws
        // UnsatisfiedLinkError, an Error, and the pool is just as unavailable.
        try {
            this.dataSource = new HikariDataSource(hikariConfig);
            plugin.getLogger().info("Database initialized (" + storageType + ").");
        } catch (RuntimeException | LinkageError e) {
            this.dataSource = null;
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not open the " + storageType + " connection pool.", e);
        }

        // Schema creation is not done here: each module owns its own versioned
        // migrations and applies them from its own async startup task
        // (ARCHITECTURE.md section 4). See com.lawkeys.hcfcore.team.TeamSchema.
    }

    /** @return {@code true} if a working connection pool is available */
    public boolean isAvailable() {
        return dataSource != null && !dataSource.isClosed();
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
