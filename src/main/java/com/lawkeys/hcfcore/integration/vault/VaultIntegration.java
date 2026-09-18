package com.lawkeys.hcfcore.integration.vault;

import com.lawkeys.hcfcore.economy.EconomyModule;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import java.util.Objects;

/**
 * Registers {@link VaultEconomyBridge} with Bukkit's service manager when Vault
 * is installed.
 *
 * <p><strong>Soft dependency, and it has to stay one</strong> (ARCHITECTURE.md
 * section 11): with Vault absent the internal economy keeps working in full, it
 * is simply not visible to other plugins. So nothing here may be reached unless
 * Vault is actually present - which is why the {@code Economy} interface is only
 * ever named from {@link VaultEconomyBridge}, a class this method does not touch
 * until after the plugin check passes. Loading a class whose interface is
 * missing throws {@link NoClassDefFoundError}, and that would take the whole
 * plugin down on a server that simply chose not to install Vault.
 */
public final class VaultIntegration {

    private static final String VAULT = "Vault";

    private VaultIntegration() {
    }

    /**
     * @param plugin  this plugin, used as the service provider
     * @param economy the module whose balances are being exposed
     * @return whether the economy was published to Vault
     */
    public static boolean register(Plugin plugin, EconomyModule economy) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(economy, "economy");

        // Enabled, not merely loaded: a Vault whose own start failed takes no service.
        if (!plugin.getServer().getPluginManager().isPluginEnabled(VAULT)) {
            plugin.getLogger().info(
                    "Vault is not installed or not running - the economy works normally, it is just not "
                            + "exposed to other plugins.");
            return false;
        }

        try {
            plugin.getServer().getServicesManager().register(
                    net.milkbowl.vault.economy.Economy.class,
                    new VaultEconomyBridge(economy),
                    plugin,
                    ServicePriority.Normal);
        } catch (LinkageError e) {
            // Vault is installed but its economy API is not the one we compiled
            // against (NoClassDefFoundError and friends are all LinkageError).
            // against. Refusing to publish is the correct outcome; taking the
            // server down over an optional integration is not.
            plugin.getLogger().warning(
                    "Vault is installed but its economy API could not be used ("
                            + e + "). The economy still works internally.");
            return false;
        }

        plugin.getLogger().info("Economy registered with Vault.");
        return true;
    }
}
