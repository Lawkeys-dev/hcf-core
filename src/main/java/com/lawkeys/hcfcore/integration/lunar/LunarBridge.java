package com.lawkeys.hcfcore.integration.lunar;

/**
 * The running integration, as {@link LunarIntegration} sees it: no Apollo type in
 * sight, so that nothing Apollo is loaded on a server without it.
 */
interface LunarBridge {

    /** Takes new settings: everything sent is taken back, then sent again as they say. */
    void apply(LunarSettings settings);

    /** Takes back everything sent and stops. */
    void stop();
}
