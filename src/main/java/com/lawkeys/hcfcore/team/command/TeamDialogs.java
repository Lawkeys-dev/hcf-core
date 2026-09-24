package com.lawkeys.hcfcore.team.command;

import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamMessages;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.util.ItemText;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * The windows of {@code /team settings} that take typed text - the team's name,
 * description and Discord link, a player to invite - so nothing in the settings
 * needs a command (the owner's request of 24/09/2026, for accessibility). The
 * game's own dialogs (Paper's dialog API): real text fields, and a button to save
 * or cancel.
 *
 * <p>What is typed goes through the same checks as the commands: the rename and the
 * invitation run {@code /team rename} and {@code /team invite} as the player; the
 * description and the link go to the manager, which checks the permission.
 */
final class TeamDialogs {

    /** A dialog's buttons answer once, and not after five minutes. */
    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1).lifetime(Duration.ofMinutes(5)).build();
    private static final int WIDTH = 300;
    private static final int DISCORD_LENGTH = 100;

    private TeamDialogs() {
    }

    /** Name, description and Discord invitation, in one window. */
    static void profile(TeamModule module, Player player, Team team) {
        var lang = module.getLang();
        var rules = module.getSettings().custom();
        List<DialogInput> inputs = List.of(
                DialogInput.text("name", text(lang.get(TeamMessages.DIALOG_NAME)))
                        .initial(team.getName()).maxLength(module.getSettings().names().maxLength())
                        .width(WIDTH).build(),
                DialogInput.text("description", text(lang.get(TeamMessages.DIALOG_DESCRIPTION,
                                "max", String.valueOf(rules.descriptionLength()))))
                        .initial(team.getDescription().orElse("")).maxLength(Math.max(1, rules.descriptionLength()))
                        .width(WIDTH).build(),
                DialogInput.text("discord", text(lang.get(TeamMessages.DIALOG_DISCORD)))
                        .initial(team.getDiscord().orElse("")).maxLength(DISCORD_LENGTH).width(WIDTH).build());
        show(module, player, team, lang.get(TeamMessages.DIALOG_PROFILE_TITLE, "team", team.getName()), inputs,
                lang.get(TeamMessages.DIALOG_SAVE), (view, current) -> saveProfile(module, player, current, view),
                TeamSettingsMenu.Page.HOME);
    }

    /** The name of a player to invite. */
    static void invite(TeamModule module, Player player, Team team) {
        var lang = module.getLang();
        List<DialogInput> inputs = List.of(DialogInput.text("player", text(lang.get(TeamMessages.DIALOG_PLAYER)))
                .maxLength(16).width(WIDTH).build());
        show(module, player, team, lang.get(TeamMessages.DIALOG_INVITE_TITLE, "team", team.getName()), inputs,
                lang.get(TeamMessages.DIALOG_SEND), (view, current) -> {
                    String name = view.getText("player");
                    if (name != null && !name.isBlank()) {
                        player.performCommand("hcfcore:team invite " + name.trim().split("\\s+")[0]);
                    }
                }, TeamSettingsMenu.Page.INVITES);
    }

    /**
     * Shows a dialog whose confirm button hands what was typed to {@code onConfirm},
     * then - either button - reopens the settings where the player was.
     */
    private static void show(TeamModule module, Player player, Team team, String title, List<DialogInput> inputs,
                             String confirm, BiConsumer<DialogResponseView, Team> onConfirm,
                             TeamSettingsMenu.Page back) {
        UUID teamId = team.getId();
        var lang = module.getLang();
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(text(title)).canCloseWithEscape(true).inputs(inputs).build())
                .type(DialogType.confirmation(
                        ActionButton.builder(text(confirm)).width(120)
                                .action(DialogAction.customClick((view, audience) -> onMain(module, () ->
                                        current(module, player, teamId).ifPresent(current -> {
                                            onConfirm.accept(view, current);
                                            reopen(module, player, teamId, back);
                                        })), ONCE))
                                .build(),
                        ActionButton.builder(text(lang.get(TeamMessages.DIALOG_CANCEL))).width(120)
                                .action(DialogAction.customClick((view, audience) -> onMain(module, () ->
                                        reopen(module, player, teamId, back)), ONCE))
                                .build())));
        player.closeInventory();
        player.showDialog(dialog);
    }

    private static void saveProfile(TeamModule module, Player player, Team team, DialogResponseView view) {
        String name = view.getText("name");
        if (name != null && !name.isBlank() && !name.trim().equals(team.getName())) {
            player.performCommand("hcfcore:team rename " + name.trim().split("\\s+")[0]);
        }
        String description = Objects.requireNonNullElse(view.getText("description"), "").trim();
        if (!description.equals(team.getDescription().orElse(""))) {
            module.report(player, module.getManager().setDescription(team, player.getUniqueId(), description));
        }
        String discord = Objects.requireNonNullElse(view.getText("discord"), "").trim();
        if (!discord.equals(team.getDiscord().orElse(""))) {
            module.report(player, module.getManager().setDiscord(team, player.getUniqueId(), discord));
        }
    }

    /** The player's team, if it is still this one - a dialog can stay open a while. */
    private static java.util.Optional<Team> current(TeamModule module, Player player, UUID teamId) {
        return module.getManager().getTeamOf(player.getUniqueId()).filter(team -> team.getId().equals(teamId));
    }

    private static void reopen(TeamModule module, Player player, UUID teamId, TeamSettingsMenu.Page page) {
        if (player.isOnline()) {
            current(module, player, teamId).ifPresent(team -> TeamSettingsMenu.open(module, player, team, page, 0));
        }
    }

    /** A dialog's answer may come off the main thread; the team is changed on it. */
    private static void onMain(TeamModule module, Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(module.getPlugin(), task);
        }
    }

    private static Component text(String raw) {
        return ItemText.line(raw);
    }
}
