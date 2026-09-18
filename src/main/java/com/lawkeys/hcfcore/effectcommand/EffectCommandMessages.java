package com.lawkeys.hcfcore.effectcommand;

/** Every language key the effect commands can produce. Audited against {@code lang/en.yml}. */
public final class EffectCommandMessages {

    // Not "on" and "off": YAML 1.1 reads those keys as booleans (CONTRIBUTING.md section 7).
    public static final String GIVEN = "effect-commands.given";
    public static final String TAKEN = "effect-commands.taken";
    public static final String FORBIDDEN = "effect-commands.forbidden";
    public static final String SWITCHED_OFF = "effect-commands.switched-off";

    private EffectCommandMessages() {
    }
}
