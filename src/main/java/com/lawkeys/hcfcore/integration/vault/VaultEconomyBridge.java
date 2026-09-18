package com.lawkeys.hcfcore.integration.vault;

import com.lawkeys.hcfcore.economy.EconomyManager;
import com.lawkeys.hcfcore.economy.EconomyModule;
import com.lawkeys.hcfcore.economy.EconomyResult;
import com.lawkeys.hcfcore.startup.StartupBarrier;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Exposes this plugin's economy to other plugins through Vault's {@code Economy}
 * interface (ARCHITECTURE.md section 11, FEATURES.md section 5).
 *
 * <p>A pure adapter: it translates Vault's vocabulary into calls on
 * {@link EconomyManager} and translates the answers back. Not one rule lives
 * here - a third-party shop plugin gets exactly the same ceiling, the same
 * refusals and the same concurrency guarantees as {@code /pay} does.
 *
 * <p><strong>Signatures verified at source</strong> (CONTRIBUTING.md section 6) on
 * 29/08/2026 against {@code github.com/MilkBowl/VaultAPI} at the {@code 1.7}
 * tag, the version pinned in {@code build.gradle.kts}. Every money-carrying
 * member of the interface is a {@code double}, which is why balances are stored
 * as {@code DOUBLE} rather than integer cents.
 *
 * <p><strong>Two deliberate limitations.</strong>
 *
 * <p>First, <em>Vault banks are not team banks.</em> {@link #hasBankSupport()}
 * returns {@code false} and every {@code bank*} method answers
 * {@code NOT_IMPLEMENTED}. Mapping them onto HCF team banks would look elegant
 * and would be a gameplay hole: Vault's bank calls carry no actor, so any
 * installed plugin could move a team's money with no rank check at all. Nothing
 * in FEATURES.md asks for it. If the project owner does want team banks visible
 * to third-party plugins, that is a decision to take explicitly, not a side
 * effect of writing this adapter.
 *
 * <p>Second, <em>world-scoped balances do not exist here.</em> A player's money
 * is the same in every world, so the {@code worldName} overloads ignore that
 * argument rather than pretending to a per-world ledger the storage has no
 * column for.
 *
 * <p><strong>Nothing moves before the balances are loaded.</strong> Until
 * {@code StartupGate} opens, every balance reads as {@code 0} and every deposit
 * or withdrawal is refused with a reason: a movement made then would be undone
 * when the load lands, and a read would report the starting balance for
 * everyone. If a load failed, that lasts until the restart, as it does for
 * players and commands.
 */
public final class VaultEconomyBridge implements Economy {

    /** Shown by Vault and by {@code /vault-info}; must identify this plugin. */
    private static final String NAME = "HCFCore";

    private final EconomyModule module;

    public VaultEconomyBridge(EconomyModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    private EconomyManager economy() {
        return module.getManager();
    }

    /** @return whether balances have loaded, so that they can be read and moved */
    private boolean loaded() {
        return module.getStartup().isReady();
    }

    // ------------------------------------------------------------------
    // Identity and formatting
    // ------------------------------------------------------------------

    @Override
    public boolean isEnabled() {
        // Deliberately blind to loading. A plugin enabled after this one may call
        // isEnabled() once, from its own onEnable - before any load can be relied
        // on to have landed - and a "no" there would make it give up on the
        // economy for the whole session. Refusing each movement until the
        // balances are in is the narrower answer.
        return economy() != null && module.getSettings().enabled();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int fractionalDigits() {
        // The interface asks for the number of digits kept, or -1 when nothing is
        // rounded. Balances are stored at full double precision and only rounded
        // when rendered, so -1 is the truthful answer.
        return -1;
    }

    @Override
    public String format(double amount) {
        EconomyManager economy = economy();
        return economy == null ? String.valueOf(amount) : economy.format(amount);
    }

    @Override
    public String currencyNameSingular() {
        return module.getSettings().currency().singular();
    }

    @Override
    public String currencyNamePlural() {
        return module.getSettings().currency().plural();
    }

    // ------------------------------------------------------------------
    // Accounts
    // ------------------------------------------------------------------

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return uuidOf(player).isPresent();
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        // Accounts are implicit: an untouched player already reports the starting
        // balance, and a row appears the first time their money actually moves.
        // So there is nothing to create, and reporting failure would make callers
        // think the player cannot be paid.
        return uuidOf(player).isPresent();
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        EconomyManager economy = economy();
        return economy == null || !loaded()
                ? 0.0
                : uuidOf(player).map(economy::getBalance).orElse(0.0);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    // ------------------------------------------------------------------
    // Movements
    // ------------------------------------------------------------------

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return move(player, amount, true);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return move(player, amount, false);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    private EconomyResponse move(OfflinePlayer player, double amount, boolean credit) {
        EconomyManager economy = economy();
        if (economy == null) {
            return failure(0.0, "The economy module is not running.");
        }
        if (!loaded()) {
            return failure(0.0, notLoadedReason());
        }
        Optional<UUID> uuid = uuidOf(player);
        if (uuid.isEmpty()) {
            return failure(0.0, "Unknown player.");
        }
        EconomyResult result = credit
                ? economy.deposit(uuid.get(), amount)
                : economy.withdraw(uuid.get(), amount);
        double balance = economy.getBalance(uuid.get());
        return result.isOk()
                ? new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null)
                : failure(balance, describe(result));
    }

    /**
     * @return a caller-facing reason for a refusal
     *
     * <p>Deliberately plain English rather than a language key: this string goes
     * to another plugin's own error handling, not to a player through
     * {@code LangManager}, and a raw key would be worse than useless there.
     */
    private static String describe(EconomyResult result) {
        return switch (result) {
            case INSUFFICIENT_FUNDS -> "Insufficient funds.";
            case INVALID_AMOUNT -> "Invalid amount.";
            case ABOVE_MAXIMUM -> "That would exceed the maximum balance allowed.";
            case DISABLED -> "The economy is disabled on this server.";
            case SAME_PLAYER -> "Source and destination are the same player.";
            case PAY_DISABLED -> "Player-to-player transfers are disabled.";
            case BELOW_MINIMUM -> "Below the minimum transfer amount.";
            case OK -> "";
        };
    }

    /** @return why nothing can move yet, in the same plain English as {@link #describe} */
    private String notLoadedReason() {
        return module.getStartup().state() == StartupBarrier.State.FAILED
                ? "HCFCore could not load its data; see the server console."
                : "HCFCore is still loading its data; try again in a few seconds.";
    }

    private static EconomyResponse failure(double balance, String message) {
        return new EconomyResponse(0.0, balance, EconomyResponse.ResponseType.FAILURE, message);
    }

    private static Optional<UUID> uuidOf(OfflinePlayer player) {
        return player == null ? Optional.empty() : Optional.ofNullable(player.getUniqueId());
    }

    // ------------------------------------------------------------------
    // Banks - see the class javadoc for why these are not team banks
    // ------------------------------------------------------------------

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return noBanks();
    }

    private static EconomyResponse noBanks() {
        return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED,
                "HCFCore does not expose team banks through Vault.");
    }

    // ------------------------------------------------------------------
    // Name-keyed overloads, deprecated in VaultAPI since 1.4
    // ------------------------------------------------------------------
    //
    // Implemented rather than left throwing: plugins written against the old API
    // still call them, and a refusal there would look like the player has no
    // money. Each resolves the name to a uuid and defers to the modern overload.

    @Override
    @Deprecated
    public boolean hasAccount(String playerName) {
        return module.resolvePlayer(playerName).isPresent();
    }

    @Override
    @Deprecated
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    @Deprecated
    public boolean createPlayerAccount(String playerName) {
        return hasAccount(playerName);
    }

    @Override
    @Deprecated
    public boolean createPlayerAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    @Deprecated
    public double getBalance(String playerName) {
        EconomyManager economy = economy();
        return economy == null || !loaded()
                ? 0.0
                : module.resolvePlayer(playerName).map(economy::getBalance).orElse(0.0);
    }

    @Override
    @Deprecated
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    @Deprecated
    public boolean has(String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }

    @Override
    @Deprecated
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    @Deprecated
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return moveByName(playerName, amount, true);
    }

    @Override
    @Deprecated
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return moveByName(playerName, amount, true);
    }

    @Override
    @Deprecated
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return moveByName(playerName, amount, false);
    }

    @Override
    @Deprecated
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return moveByName(playerName, amount, false);
    }

    private EconomyResponse moveByName(String playerName, double amount, boolean credit) {
        EconomyManager economy = economy();
        if (economy == null) {
            return failure(0.0, "The economy module is not running.");
        }
        if (!loaded()) {
            return failure(0.0, notLoadedReason());
        }
        Optional<UUID> uuid = module.resolvePlayer(playerName);
        if (uuid.isEmpty()) {
            return failure(0.0, "Unknown player.");
        }
        EconomyResult result = credit
                ? economy.deposit(uuid.get(), amount)
                : economy.withdraw(uuid.get(), amount);
        double balance = economy.getBalance(uuid.get());
        return result.isOk()
                ? new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null)
                : failure(balance, describe(result));
    }

    @Override
    @Deprecated
    public EconomyResponse createBank(String name, String player) {
        return noBanks();
    }

    @Override
    @Deprecated
    public EconomyResponse isBankOwner(String name, String playerName) {
        return noBanks();
    }

    @Override
    @Deprecated
    public EconomyResponse isBankMember(String name, String playerName) {
        return noBanks();
    }
}
