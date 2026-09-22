package com.lawkeys.hcfcore.events.totem;

/** What a block broken by a team other than the one on its way to winning does. */
public enum RivalBreak {

    /** The totem starts over, every block whole again; that break counts for nobody. */
    RESET,
    /** The totem starts over, and that break is the first of the team that made it. */
    RESET_AND_START
}
