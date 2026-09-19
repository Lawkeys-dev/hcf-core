package com.lawkeys.hcfcore.startup;

import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.startup.StartupBarrier.State;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * The server-facing half of {@link StartupBarrier}: keeps players out, and
 * commands from running, until every module has loaded its data.
 *
 * <p>Two doors, because data can be changed through two:
 * <ul>
 *     <li><strong>Logins</strong> are refused until the barrier is ready. No player
 *     online means no death, no block broken in a claim, no player command -
 *     every listener is covered without touching one.</li>
 *     <li><strong>Commands</strong> that read or change loaded data call
 *     {@link #refuseCommand} first, for the console, which is there from the
 *     start. The Vault bridge asks {@link #isReady()} for other plugins.</li>
 * </ul>
 * The managers themselves are untouched: nothing reaches them early.
 *
 * <p><strong>The login event, read in the Paper 26.2 sources rather than
 * assumed</strong> (CONTRIBUTING.md section 6): {@code PlayerLoginEvent} is deprecated
 * since 1.21.6, and its javadoc names {@link PlayerConnectionValidateLoginEvent}
 * for "pre-login logic (e.g. authentication or ban checks)". It is a synchronous
 * event, fired when a player first logs in and again when they finish being
 * configured; a non-null kick message refuses the connection. Unlike
 * {@code DeathbanListener}, this check needs no permission, so the missing
 * {@code Player} that keeps that listener on the deprecated event is no obstacle
 * here - which also means there is no bypass: while the barrier is closed, it is
 * closed to staff too, and the console is the way in.
 */
public final class StartupGate implements Listener {

    private final LangManager lang;
    private final StartupBarrier barrier;

    public StartupGate(Plugin plugin, LangManager lang) {
        this.lang = Objects.requireNonNull(lang, "lang");
        Logger logger = plugin.getLogger();
        this.barrier = new StartupBarrier(new StartupBarrier.Observer() {
            @Override
            public void ready() {
                logger.info("All data loaded; players can now join.");
            }

            @Override
            public void failed(String load) {
                logger.severe("HCFCore is staying closed because " + load + " could not be loaded: "
                        + "players cannot join and data commands are disabled. Fix the cause above, "
                        + "then restart the server.");
            }
        });
    }

    /** @see StartupBarrier#expect */
    public StartupBarrier.Load expect(String name) {
        return barrier.expect(name);
    }

    /** Called once every module is enabled: no load is declared after this. */
    public void seal() {
        barrier.seal();
    }

    public State state() {
        return barrier.state();
    }

    public boolean isReady() {
        return barrier.isReady();
    }

    /**
     * Guards a command that reads or changes loaded data.
     *
     * @return {@code true} if the command must not run, the sender having been
     *         told why
     */
    public boolean refuseCommand(CommandSender sender) {
        State state = barrier.state();
        if (state == State.READY) {
            return false;
        }
        lang.send(sender, state == State.FAILED ? StartupMessages.COMMAND_FAILED : StartupMessages.COMMAND_LOADING);
        return true;
    }

    // HIGHEST so that a plugin allowing the login at a lower priority does not
    // let a player in before the data is there; only MONITOR runs after this.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onValidateLogin(PlayerConnectionValidateLoginEvent event) {
        State state = barrier.state();
        // Already refused - by the whitelist, a ban, another plugin: that reason
        // is the one to show, and it keeps the player out just the same.
        if (state == State.READY || !event.isAllowed()) {
            return;
        }
        String message = lang.get(state == State.FAILED ? StartupMessages.LOGIN_FAILED : StartupMessages.LOGIN_LOADING);
        // LangManager already produces the section-sign form, so this is a straight
        // parse, as in DeathbanListener.
        event.kickMessage(com.lawkeys.hcfcore.util.LegacyText.SERIALIZER.deserialize(message));
    }
}
