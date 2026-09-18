package com.lawkeys.hcfcore.startup;

/**
 * Every language key the startup gate can produce.
 *
 * <p>Same contract as {@code TeamMessages}: no player-facing text in Java, and
 * {@code TeamMessagesTest} fails the build if a key used here is missing from
 * {@code lang/en.yml}.
 */
public final class StartupMessages {

    private StartupMessages() {
    }

    // Shown on the disconnect screen of a player refused at login
    public static final String LOGIN_LOADING = "startup.login.loading";
    public static final String LOGIN_FAILED = "startup.login.failed";

    // Answered instead of running a command that reads or changes loaded data
    public static final String COMMAND_LOADING = "startup.command.loading";
    public static final String COMMAND_FAILED = "startup.command.failed";
}
