package com.lawkeys.hcfcore.warmup;

import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.warmup.listener.WarmupListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * The countdowns that damage or movement cancels, for every module that needs one.
 *
 * <p>Its own module, started before any of the modules that use it, because two of
 * them need it and neither may depend on the other: {@code claim/} for
 * {@code /team hq}, {@code /team base} and {@code /team stuck}, and {@code general/}
 * for {@code /spawn} and {@code /logout}. {@code general/} can be switched off whole,
 * so the countdowns could not live there; and one shared {@link Warmups} is what
 * stops a {@code /team hq} from being stacked on a {@code /logout}.
 *
 * <p>The rules - who is counting down, have they moved, is it due - are in
 * {@link Warmups}, pure and tested. This class adds what a countdown does when it
 * ends, which only the caller knows.
 */
public final class WarmupModule {

    /** How often, in ticks, due countdowns are looked for: a quarter of a second late at worst. */
    private static final long TICK_INTERVAL = 5L;

    private final Plugin plugin;
    private final LangManager lang;
    private final Warmups warmups = new Warmups();
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private BukkitTask task;

    /** What a countdown does when it ends, and what its holder is told if it is cut short. */
    private record Pending(Consumer<Player> onFinish, String cancelledMessageKey) {
    }

    public WarmupModule(Plugin plugin, LangManager lang) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lang = Objects.requireNonNull(lang, "lang");
    }

    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(new WarmupListener(this), plugin);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        warmups.clearAll();
        pending.clear();
    }

    /**
     * Starts a countdown at the player's current block.
     *
     * @param kind                what it is for, for {@link #of}
     * @param seconds             how long; {@code 0} or less runs {@code onFinish} at once
     * @param cancelledMessageKey what the player is told if damage or movement cuts it short
     * @param onFinish            what happens when it ends, on the main thread; it should
     *                            check again whatever the command checked, since the world
     *                            has had {@code seconds} to change
     * @return {@code false}, changing nothing, if a countdown of any kind is already
     *         running for this player
     */
    public boolean begin(Player player, String kind, long seconds, String cancelledMessageKey,
                         Consumer<Player> onFinish) {
        Objects.requireNonNull(onFinish, "onFinish");
        UUID playerId = player.getUniqueId();
        if (seconds <= 0) {
            if (warmups.isWarmingUp(playerId)) {
                return false;
            }
            onFinish.accept(player);
            return true;
        }
        Location at = player.getLocation();
        if (!warmups.start(playerId, kind, System.currentTimeMillis() + seconds * 1000L,
                at.getWorld().getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ())) {
            return false;
        }
        pending.put(playerId, new Pending(onFinish, cancelledMessageKey));
        return true;
    }

    public Optional<Warmups.Warmup> of(UUID playerId) {
        return warmups.of(playerId);
    }

    public boolean isWarmingUp(UUID playerId) {
        return warmups.isWarmingUp(playerId);
    }

    /** Cuts a countdown short and tells the player, if there was one. */
    public void cancel(Player player) {
        UUID playerId = player.getUniqueId();
        Optional<Warmups.Warmup> cancelled = warmups.cancel(playerId);
        Pending what = pending.remove(playerId);
        if (cancelled.isPresent() && what != null && what.cancelledMessageKey() != null) {
            lang.send(player, what.cancelledMessageKey());
        }
    }

    /** Drops a countdown without a word - its holder has left. */
    public void forget(UUID playerId) {
        warmups.cancel(playerId);
        pending.remove(playerId);
    }

    private void tick() {
        for (Warmups.Warmup due : warmups.pollDue(System.currentTimeMillis())) {
            Pending what = pending.remove(due.playerId());
            Player player = Bukkit.getPlayer(due.playerId());
            if (what == null || player == null) {
                continue;
            }
            try {
                what.onFinish().accept(player);
            } catch (RuntimeException e) {
                // One failed countdown must not stop the others finishing this tick.
                plugin.getLogger().log(Level.WARNING,
                        "A '" + due.kind() + "' countdown for " + player.getName() + " failed to finish.", e);
            }
        }
    }
}
