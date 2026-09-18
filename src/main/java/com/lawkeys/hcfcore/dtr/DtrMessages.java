package com.lawkeys.hcfcore.dtr;

/**
 * Every language key the DTR module can produce.
 *
 * <p>Audited against {@code lang/en.yml} by {@code TeamMessagesTest}, like the
 * team and claim modules.
 */
public final class DtrMessages {

    private DtrMessages() {
    }

    public static final String INFO_HEADER = "dtr.info.header";
    public static final String INFO_CURRENT = "dtr.info.current";
    public static final String INFO_RAIDABLE = "dtr.info.raidable";
    public static final String INFO_PROTECTED = "dtr.info.protected";
    public static final String INFO_FROZEN = "dtr.info.frozen";
    public static final String INFO_REGENERATING = "dtr.info.regenerating";
    public static final String INFO_AT_MAXIMUM = "dtr.info.at-maximum";
    public static final String INFO_MAP_WIDE_RAID = "dtr.info.map-wide-raid";

    public static final String DEATH_LOST = "dtr.death.lost";
    public static final String BECAME_RAIDABLE = "dtr.announce.became-raidable";
    public static final String NO_LONGER_RAIDABLE = "dtr.announce.no-longer-raidable";

    public static final String SET_SUCCESS = "dtr.set.success";
    public static final String SET_OUT_OF_RANGE = "dtr.set.out-of-range";
    public static final String SET_REGEN_SUCCESS = "dtr.setregen.success";
    public static final String SET_REGEN_NEGATIVE = "dtr.setregen.negative";

    public static final String DISABLED = "dtr.error.disabled";
    public static final String INVALID_NUMBER = "dtr.error.invalid-number";
    public static final String SYSTEM_TEAM = "dtr.error.system-team";
}
