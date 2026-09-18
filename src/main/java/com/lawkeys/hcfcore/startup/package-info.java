/**
 * Startup: nothing reads or changes a module's data before that data is loaded.
 *
 * <p>Every module loads its cache on an async task, and every load starts by
 * clearing that cache, so a change made before the load lands is lost.
 * {@link com.lawkeys.hcfcore.startup.StartupBarrier} (pure Java, tested) counts
 * the loads; {@link com.lawkeys.hcfcore.startup.StartupGate} refuses logins and
 * data commands until they have all succeeded, and for good if one fails - a
 * server that could not load its data stays closed until it is restarted.
 *
 * <p>The database pool counts as a load of its own: if it cannot be opened, the
 * modules fall back to memory-only stores whose loads "succeed" on nothing, and
 * letting players in would mean playing on an empty cache that saves nowhere.
 */
package com.lawkeys.hcfcore.startup;
