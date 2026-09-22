package com.lawkeys.hcfcore.ui;

import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of {@code ui.yml}.
 *
 * <p><strong>The scoreboard is a list of template lines, not a fixed layout.</strong>
 * Which numbers an HCF server puts on a player's screen is a matter of taste and of
 * what that server runs - a kitmap has no DTR, a server without events has no KOTH
 * timer - so the lines are the operator's to write, from the placeholders listed in
 * {@code ui.yml}. A line whose placeholders all resolve to nothing is dropped rather
 * than rendered blank, which is what makes a timer line appear only while the timer
 * is running.
 */
public record UiSettings(ScoreboardRules scoreboard, TablistRules tablist) {

    public UiSettings {
        Objects.requireNonNull(scoreboard, "scoreboard");
        Objects.requireNonNull(tablist, "tablist");
    }

    /**
     * @param updateTicks how often the board is redrawn; 20 ticks is once a second,
     *                    which is as fine as any countdown on it needs
     * @param title       the heading, colour codes allowed
     * @param lines       the body, top to bottom
     */
    public record ScoreboardRules(boolean enabled, long updateTicks, String title, List<String> lines) {

        public ScoreboardRules {
            Objects.requireNonNull(title, "title");
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        }
    }

    /**
     * The tab list: the HCF grid or the classic list (the project owner's request,
     * 19/09/2026), chosen by {@code style}.
     *
     * @param updateTicks how often it is redrawn
     */
    public record TablistRules(boolean enabled, com.lawkeys.hcfcore.ui.tab.TabStyle style, long updateTicks,
                               GridRules hcf, ClassicRules classic) {

        public TablistRules {
            Objects.requireNonNull(style, "style");
            Objects.requireNonNull(hcf, "hcf");
            Objects.requireNonNull(classic, "classic");
        }
    }

    /**
     * The HCF grid.
     *
     * @param columns   the four columns, each up to twenty cells, top to bottom
     * @param latency   the connection bars every cell shows, in milliseconds; below 0 for none
     * @param texture   a skin for every cell's head; empty for the game's default heads
     * @param signature that skin's signature
     */
    public record GridRules(List<String> header, List<String> footer, List<List<String>> columns,
                            int latency, String texture, String signature) {

        public GridRules {
            header = List.copyOf(Objects.requireNonNull(header, "header"));
            footer = List.copyOf(Objects.requireNonNull(footer, "footer"));
            columns = Objects.requireNonNull(columns, "columns").stream().map(List::copyOf).toList();
            Objects.requireNonNull(texture, "texture");
            Objects.requireNonNull(signature, "signature");
        }
    }

    /**
     * The classic list: the real players, each written from {@code name}.
     *
     * @param name how each player is written; their LuckPerms prefix and suffix,
     *             their team, kills and ping are theirs, not the viewer's
     * @param sort the order players are listed in
     */
    public record ClassicRules(List<String> header, List<String> footer, String name,
                               com.lawkeys.hcfcore.ui.tab.TabSort sort) {

        public ClassicRules {
            header = List.copyOf(Objects.requireNonNull(header, "header"));
            footer = List.copyOf(Objects.requireNonNull(footer, "footer"));
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(sort, "sort");
        }
    }

    /**
     * The grid's default head: a plain dark grey square, the classic HCF filler
     * (mineskin.org, "soju-tab-gray8", texture 4d9948d8...). A head the game shows in
     * the tab list must be signed by Mojang, so it cannot be drawn here: this is one
     * that was, taken from mineskin.org's public gallery.
     */
    static final String GREY_HEAD_TEXTURE = "ewogICJ0aW1lc3RhbXAiIDogMTYyMTcwNTk1MDY4MCwKICAicHJvZmlsZUlkIiA6ICJjZGM5MzQ0NDAzODM0ZDdkYmRmOWUyMmVjZmM5MzBiZiIsCiAgInByb2ZpbGVOYW1lIiA6ICJSYXdMb2JzdGVycyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS80ZDk5NDhkODUyNWQyNzU4YWY1YzFiNmJhMzIyZmZiMzgzM2Q5Nzk3NDUwMGQ0YTZhYTliZjkxODU4MjNlOGVjIgogICAgfQogIH0KfQ==";
    static final String GREY_HEAD_SIGNATURE = "IoKU9gDR+OW5f4AKhJyU6v16kCr4Efu0+s4SRAUaFtcccSKVxjg8TKW35MbOFkOsEagzXgiRcxDFV3E5k7GEaSqVl/YxiWkJ3oxts+hcz5zKsLyOfb6uvefSfAkCLPdQOGS/XYU2jZCLsmxynUG33B7suf/uEsRobNoKbdYDAvqDiKYMkmLK8qJTMoQgQJqRTf31wJShTNLFcFkTr/NdfsPQQhRl9Ayf8VtbClEVEpxZRtgO2LwJtrIgRQezS6Ie4GHD5hBpgLe678UQHvcSnMZqurI0QU4afH4J4Frmv0LW9DTF3KaxEsHf5eQLNelUZ+ywY81Xjb8JBAf+HdmRuVRGd5Gjt13OYecItODhLcWsmh0f9PJZR7EwFdk/nBeCkAwgGXg9SgIt+S8GGbDfQqvrgBCHWvl/iGjSl2wIPRyUpjDHJ3p4+k5HMqTRVA6JY6rNutj+tpEGuVh2hvRLgzXdsEs67diJ3Bvdxi2mam3dtCR/BIxzG5lg8P1S5tiIIEnuiynjwPmprylSjU61L0BZj+o0xlIT3jEqLGZUlZq/8vZuydLDGSLO0Ims78HwlYo08n18lfSeMG7zpN3RSv7VkAf5mGus+IPeInzWFkZ6XDI0fzaYDeHj12o6AxmQxB9U6uCjVi/JAYffAByFc8+oD1+tBCjpfV257efV4Wk=";

    /** Built-in fallback, mirroring {@code resources/ui.yml}. */
    public static UiSettings defaults() {
        return new UiSettings(
                new ScoreboardRules(true, 20L, "{primary}&lHCF", List.of(
                        "{dark}▪ ▪ ▪ ▪ ▪ ▪ ▪ ▪ ▪ ▪ ▪ ▪",
                        "[team]{primary}Team {dark}{bullet} {secondary}%team%",
                        "[team]{primary}DTR {dark}{bullet} %dtr_coloured%",
                        "[stats]{primary}Kills {dark}{bullet} {secondary}%kills%",
                        "[stats]{primary}Streak {dark}{bullet} {secondary}%killstreak%",
                        "[balance]{primary}Balance {dark}{bullet} {success}%balance%",
                        "[team]%focus_line%",
                        "[team]%rally_line%",
                        "[combat]%combat_line%",
                        "[cooldowns]%pearl_line%",
                        "[cooldowns]%cooldown_notch-apple_line%",
                        "[cooldowns]%cooldown_golden-apple_line%",
                        "[cooldowns]%cooldown_chorus-fruit_line%",
                        "[cooldowns]%cooldown_totem_line%",
                        "[class]%class_line%",
                        "[class]%class_energy_line%",
                        "[class]%archer_tag_line%",
                        "[events]%phase_line%",
                        "[events]%event_line%",
                        "[events]%king_line%",
                        "[events]%king_location_line%",
                        "[events]%conquest_line%",
                        "[events]%conquest_zone_1%",
                        "[events]%conquest_zone_2%",
                        "[events]%conquest_zone_3%",
                        "[events]%conquest_zone_4%",
                        "[events]%dtc_line%",
                        "[events]%dtc_team_line%",
                        "[events]%last_break_line%",
                        "[events]%slide_line%",
                        "[events]%slide_top_1%",
                        "[events]%slide_top_2%",
                        "[events]%slide_top_3%",
                        "[timers]%timer_1%",
                        "[timers]%timer_2%",
                        "[timers]%timer_3%",
                        "{dark}▪ ▪ ▪ ▪ ▪ ▪ ▪ ▪ ▪ ▪ ▪ ▪",
                        "{muted}play.yourserver.net")),
                new TablistRules(true, com.lawkeys.hcfcore.ui.tab.TabStyle.AUTO, 20L,
                        new GridRules(
                                List.of("", "{primary}&lHCF", ""),
                                List.of("", "{muted}play.yourserver.net", ""),
                                List.of(
                                        List.of("", "[head:self]{primary}&lPlayer Info", "{text}Kills {dark}{bullet} {secondary}%kills%", "{text}Deaths {dark}{bullet} {secondary}%deaths%", "{text}K/D {dark}{bullet} {secondary}%kdr%", "{text}Streak {dark}{bullet} {secondary}%killstreak%", "{text}Balance {dark}{bullet} {success}%balance%", "", "[head:MHF_ArrowRight]{primary}&lLocation", "%location%", "{secondary}%x%{muted}, {secondary}%z% {muted}(%direction%)", "", "%combat_line%", "%pearl_line%", "%class_line%", "%class_energy_line%"),
                                        List.of("", "[head:MHF_Chest]{primary}&lTeam", "%team_name_line%", "{text}DTR {dark}{bullet} %dtr_coloured%", "{text}Online {dark}{bullet} {secondary}%members_online%{muted}/{secondary}%members_total%", "{text}Balance {dark}{bullet} {success}%team_balance%", "{text}Points {dark}{bullet} {secondary}%team_points%", "", "[head:MHF_Villager]%members_title%", "[head:member:1]%member_1%", "[head:member:2]%member_2%", "[head:member:3]%member_3%", "[head:member:4]%member_4%", "[head:member:5]%member_5%", "[head:member:6]%member_6%", "[head:member:7]%member_7%", "[head:member:8]%member_8%", "[head:member:9]%member_9%", "[head:member:10]%member_10%", "[head:member:11]%member_11%"),
                                        List.of("", "[head:MHF_Question]{primary}&lServer", "{text}Online {dark}{bullet} {secondary}%online%", "{text}Ping {dark}{bullet} {secondary}%ping%ms", "", "[head:MHF_Exclamation]{primary}&lEvents", "%no_event_line%", "%phase_line%", "%event_line%", "%king_line%", "%conquest_line%", "%dtc_line%", "%last_break_line%", "%slide_line%", "%timer_1%", "%timer_2%", "%timer_3%"),
                                        List.of("", "[head:MHF_Present1]{primary}&lTop Teams", "%top_team_1%", "%top_team_2%", "%top_team_3%", "%top_team_4%", "%top_team_5%", "%top_team_6%", "%top_team_7%", "%top_team_8%", "%top_team_9%", "%top_team_10%")),
                                0, GREY_HEAD_TEXTURE, GREY_HEAD_SIGNATURE),
                        new ClassicRules(
                                List.of("", "{primary}&lKITMAP", "{muted}Online {dark}{bullet} {secondary}%online%", ""),
                                List.of("", "{muted}play.yourserver.net", ""),
                                "%prefix%{secondary}%player%%suffix%",
                                com.lawkeys.hcfcore.ui.tab.TabSort.RANK)));
    }
}
