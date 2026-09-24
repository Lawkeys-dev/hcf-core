package com.lawkeys.hcfcore.team.command;

import com.lawkeys.hcfcore.team.JoinMode;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamAction;
import com.lawkeys.hcfcore.team.TeamMenuButton;
import com.lawkeys.hcfcore.team.TeamMessages;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.team.TeamPermission;
import com.lawkeys.hcfcore.team.TeamResult;
import com.lawkeys.hcfcore.team.TeamRole;
import com.lawkeys.hcfcore.team.TeamSettings;
import com.lawkeys.hcfcore.theme.MenuLayout;
import com.lawkeys.hcfcore.theme.MenuStyle;
import com.lawkeys.hcfcore.util.ItemText;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /team settings}: the team set up from one window, with no command to
 * type - the owner's request of 24/09/2026, for accessibility. Every member opens
 * it; each button checks what it does, and says so under it when the viewer may not.
 *
 * <ul>
 *   <li><strong>Profile</strong> - name, description, Discord invitation, typed in a
 *       dialog ({@link TeamDialogs}).</li>
 *   <li><strong>Join mode</strong> - closed, on invitation, open; a click moves to
 *       the next the server allows.</li>
 *   <li><strong>Permissions</strong> - the lowest role that may do each thing, the
 *       team's own choice or the server's ({@code required-roles}). Left click raises
 *       it a role, right click lowers it, shift-click gives it back to the server.</li>
 *   <li><strong>Members</strong> - left click promotes, right click demotes,
 *       shift + right click kicks.</li>
 *   <li><strong>Invitations</strong> - a player to invite, typed in a dialog; who
 *       holds one (a click takes it back).</li>
 *   <li>What other modules add ({@link TeamMenuButton}): the claim lock, the HQ.</li>
 * </ul>
 *
 * <p>Members and invitations run the {@code /team} commands themselves, so the menu
 * can do nothing the command would refuse, and says the same thing.
 */
public final class TeamSettingsMenu implements InventoryHolder {

    private static final int COLUMNS = 9;

    enum Page {
        HOME, PERMISSIONS, MEMBERS, INVITES
    }

    /** The home page's own buttons, in order; other modules' follow. */
    private enum Button {
        PROFILE, JOIN_MODE, PERMISSIONS, MEMBERS, INVITES
    }

    /** The first item of the invitations page: a player to invite. */
    private enum InviteButton {
        INVITE
    }

    private final TeamModule module;
    private final UUID teamId;
    private final Page page;
    private final MenuLayout layout;
    /** What each item slot of this page stands for, in order. */
    private final List<Object> entries;
    private final int back;
    private final Inventory inventory;

    private TeamSettingsMenu(TeamModule module, Team team, Player viewer, Page page, int pageNumber) {
        this.module = module;
        this.teamId = team.getId();
        this.page = page;
        this.entries = entries(module, team, page);
        this.layout = MenuStyle.layout(Math.max(1, entries.size()), pageNumber);

        int size = layout.size();
        int backSlot = -1;
        if (page != Page.HOME) {
            int bottomMiddle = (layout.rows() - 1) * COLUMNS + 4;
            if (layout.frameSlots().contains(bottomMiddle) || layout.pages() > 1) {
                backSlot = bottomMiddle;
            } else if (layout.rows() < 6) {
                size += COLUMNS;
                backSlot = layout.rows() * COLUMNS + 4;
            }
        }
        this.back = backSlot;
        this.inventory = Bukkit.createInventory(this, size, MenuStyle.title(module.getLang().get(title(page),
                "team", team.getName())));
        draw(team, viewer);
    }

    public static void open(TeamModule module, Player viewer, Team team) {
        open(module, viewer, team, Page.HOME, 0);
    }

    static void open(TeamModule module, Player viewer, Team team, Page page, int pageNumber) {
        viewer.openInventory(new TeamSettingsMenu(module, team, viewer, page, pageNumber).getInventory());
    }

    private static String title(Page page) {
        return switch (page) {
            case HOME -> TeamMessages.SETTINGS_TITLE;
            case PERMISSIONS -> TeamMessages.SETTINGS_PERMISSIONS_TITLE;
            case MEMBERS -> TeamMessages.SETTINGS_MEMBERS_TITLE;
            case INVITES -> TeamMessages.SETTINGS_INVITES_TITLE;
        };
    }

    private static List<Object> entries(TeamModule module, Team team, Page page) {
        return switch (page) {
            case HOME -> {
                List<Object> buttons = new ArrayList<>(List.of(Button.values()));
                buttons.addAll(module.getMenuButtons());
                yield buttons;
            }
            case PERMISSIONS -> new ArrayList<>(module.getPermissions());
            case MEMBERS -> team.getMembers().entrySet().stream()
                    .sorted(Comparator.<java.util.Map.Entry<UUID, TeamRole>>comparingInt(e -> -e.getValue().weight())
                            .thenComparing(e -> module.nameOf(e.getKey()).toLowerCase(java.util.Locale.ROOT)))
                    .map(e -> (Object) e.getKey())
                    .toList();
            case INVITES -> {
                List<Object> invites = new ArrayList<>(List.of(InviteButton.INVITE));
                invites.addAll(module.getManager().getInvitesOf(team));
                yield invites;
            }
        };
    }

    private void draw(Team team, Player viewer) {
        TeamSettings.CustomRules rules = module.getSettings().custom();
        for (int i = 0; i < layout.itemSlots().size(); i++) {
            int index = layout.firstItem() + i;
            if (index >= entries.size()) {
                break;
            }
            Object entry = entries.get(index);
            ItemStack icon = switch (entry) {
                case Button button -> button(button, team, viewer, rules);
                case TeamMenuButton button -> item(material(rules.icon(button.key(), button.defaultIcon()),
                        material(button.defaultIcon(), Material.PAPER)), button.name(team, viewer), button.lore(team, viewer));
                case TeamPermission permission -> permission(permission, team, rules);
                case InviteButton ignored -> item(material(rules.icon("invite", "WRITABLE_BOOK"), Material.WRITABLE_BOOK),
                        module.getLang().get(TeamMessages.SETTINGS_BUTTON_INVITE),
                        List.of(module.getLang().get(TeamMessages.SETTINGS_BUTTON_INVITE_LORE)));
                case UUID id when page == Page.MEMBERS -> member(id, team);
                case UUID id -> invite(id);
                default -> null;
            };
            inventory.setItem(layout.itemSlots().get(i), icon);
        }
        if (back >= 0) {
            inventory.setItem(back, item(material(rules.icon("back", "ARROW"), Material.ARROW),
                    module.getLang().get(TeamMessages.SETTINGS_BUTTON_BACK), List.of()));
        }
        MenuStyle.decorate(inventory, layout, module.getLang());
    }

    private ItemStack button(Button button, Team team, Player viewer, TeamSettings.CustomRules rules) {
        var lang = module.getLang();
        return switch (button) {
            case PROFILE -> {
                List<String> lore = new ArrayList<>();
                lore.add(lang.get(TeamMessages.SETTINGS_PROFILE_DESCRIPTION, "description",
                        team.getDescription().orElse(lang.get(TeamMessages.SETTINGS_PROFILE_NONE))));
                lore.add(lang.get(TeamMessages.SETTINGS_PROFILE_DISCORD, "link",
                        team.getDiscord().orElse(lang.get(TeamMessages.SETTINGS_PROFILE_NONE))));
                lore.add("");
                lore.add(allowed(team, viewer, TeamAction.SETTINGS)
                        ? lang.get(TeamMessages.SETTINGS_PROFILE_HINT) : notAllowed(team, TeamAction.SETTINGS));
                yield item(material(rules.icon("profile", "NAME_TAG"), Material.NAME_TAG),
                        lang.get(TeamMessages.SETTINGS_BUTTON_PROFILE, "team", team.getName()), lore);
            }
            case JOIN_MODE -> {
                JoinMode mode = module.getManager().joinMode(team);
                List<String> lore = new ArrayList<>();
                for (JoinMode each : JoinMode.values()) {
                    if (rules.allows(each)) {
                        lore.add((each == mode ? "{primary}➥ " : "{muted}  ") + lang.get(TeamMessages.joinMode(each)));
                    }
                }
                lore.add("");
                lore.add(allowed(team, viewer, TeamAction.SETTINGS)
                        ? lang.get(TeamMessages.SETTINGS_BUTTON_JOIN_MODE_LORE) : notAllowed(team, TeamAction.SETTINGS));
                String fallback = switch (mode) {
                    case CLOSED -> "RED_DYE";
                    case INVITE -> "YELLOW_DYE";
                    case OPEN -> "LIME_DYE";
                };
                yield item(material(rules.icon("join-mode-" + mode.configKey(), fallback), Material.PAPER),
                        lang.get(TeamMessages.SETTINGS_BUTTON_JOIN_MODE, "mode", lang.get(TeamMessages.joinMode(mode))),
                        lore);
            }
            case PERMISSIONS -> item(material(rules.icon("permissions", "WRITABLE_BOOK"), Material.WRITABLE_BOOK),
                    lang.get(TeamMessages.SETTINGS_BUTTON_PERMISSIONS),
                    List.of(lang.get(TeamMessages.SETTINGS_BUTTON_PERMISSIONS_LORE)));
            case MEMBERS -> item(material(rules.icon("members", "PLAYER_HEAD"), Material.PLAYER_HEAD),
                    lang.get(TeamMessages.SETTINGS_BUTTON_MEMBERS),
                    List.of(lang.get(TeamMessages.SETTINGS_BUTTON_MEMBERS_LORE,
                            "count", String.valueOf(team.getMemberCount()))));
            case INVITES -> item(material(rules.icon("invites", "PAPER"), Material.PAPER),
                    lang.get(TeamMessages.SETTINGS_BUTTON_INVITES),
                    List.of(lang.get(TeamMessages.SETTINGS_BUTTON_INVITES_LORE,
                            "count", String.valueOf(module.getManager().getInvitesOf(team).size()))));
        };
    }

    private boolean allowed(Team team, Player viewer, TeamAction action) {
        return module.getManager().denied(team, viewer.getUniqueId(), action).isEmpty();
    }

    /** The line under a button its viewer may not use: which rank may. */
    private String notAllowed(Team team, TeamAction action) {
        TeamRole role = module.getManager().requiredRole(team, action.configKey(),
                module.getSettings().requiredRole(action));
        return module.getLang().get(TeamMessages.SETTINGS_BUTTON_NOT_ALLOWED, "role", module.roleName(role));
    }

    private ItemStack permission(TeamPermission permission, Team team, TeamSettings.CustomRules rules) {
        var lang = module.getLang();
        TeamRole server = permission.serverRole().get();
        TeamRole current = module.getManager().requiredRole(team, permission.key(), server);
        boolean own = rules.editable(permission.key()) && team.getPermission(permission.key()).isPresent();
        List<String> lore = new ArrayList<>();
        lore.add(lang.get(TeamMessages.SETTINGS_PERMISSION_ROLE, "role", module.roleName(current)));
        lore.add(own ? lang.get(TeamMessages.SETTINGS_PERMISSION_OWN, "role", module.roleName(server))
                : lang.get(TeamMessages.SETTINGS_PERMISSION_SERVER));
        lore.add("");
        lore.add(lang.get(rules.editable(permission.key())
                ? TeamMessages.SETTINGS_PERMISSION_HINT : TeamMessages.SETTINGS_PERMISSION_LOCKED));
        String fallback = permission.icon();
        return item(material(rules.icon(permission.key(), fallback), material(fallback, Material.PAPER)),
                lang.get(TeamMessages.SETTINGS_PERMISSION_NAME,
                        "permission", lang.get("team.settings.permissions." + permission.key())),
                lore);
    }

    private ItemStack member(UUID id, Team team) {
        var lang = module.getLang();
        ItemStack head = head(id);
        TeamRole role = team.getRole(id).orElse(TeamRole.MEMBER);
        List<String> lore = List.of(lang.get(TeamMessages.SETTINGS_MEMBER_ROLE, "role", module.roleName(role)), "",
                lang.get(TeamMessages.SETTINGS_MEMBER_HINT));
        return named(head, lang.get(TeamMessages.SETTINGS_MEMBER_NAME, "player", module.nameOf(id)), lore);
    }

    private ItemStack invite(UUID id) {
        var lang = module.getLang();
        return named(head(id), lang.get(TeamMessages.SETTINGS_INVITE_NAME, "player", module.nameOf(id)),
                List.of(lang.get(TeamMessages.SETTINGS_INVITE_HINT)));
    }

    /** A player's head, from the name the server already knows - never a lookup. */
    private ItemStack head(UUID id) {
        ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(Bukkit.createProfile(id, module.nameOf(id))));
        return head;
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        return named(ItemStack.of(material), name, lore);
    }

    private ItemStack named(ItemStack item, String name, List<String> lore) {
        List<Component> lines = new ArrayList<>();
        Component separator = MenuStyle.separator(module.getLang());
        if (separator != null && !lore.isEmpty()) {
            lines.add(separator);
        }
        for (String line : lore) {
            lines.add(line.isEmpty() ? Component.empty() : ItemText.line(line));
        }
        item.editMeta(meta -> {
            meta.customName(ItemText.line(name));
            meta.lore(lines);
        });
        return item;
    }

    private static Material material(String name, Material fallback) {
        Material material = name == null ? null : Material.matchMaterial(name);
        return material == null || !material.isItem() ? fallback : material;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** The clicks: a page opened, a role changed, a command run - never an item taken. */
    public static final class Clicks implements Listener {

        private final TeamModule module;

        public Clicks(TeamModule module) {
            this.module = module;
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onClick(InventoryClickEvent event) {
            if (!(event.getInventory().getHolder(false) instanceof TeamSettingsMenu menu)) {
                return;
            }
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getInventory()) {
                return;
            }
            // The team as it is now: it may have been disbanded, or the viewer kicked,
            // since the window opened.
            Optional<Team> team = module.getManager().getTeamOf(player.getUniqueId())
                    .filter(t -> t.getId().equals(menu.teamId));
            if (team.isEmpty()) {
                player.closeInventory();
                return;
            }
            int slot = event.getRawSlot();
            if (slot == menu.back) {
                open(module, player, team.get());
                return;
            }
            if (slot == menu.layout.previous() || slot == menu.layout.next()) {
                int page = menu.layout.page() + (slot == menu.layout.next() ? 1 : -1);
                open(module, player, team.get(), menu.page, page);
                return;
            }
            int index = menu.layout.itemSlots().indexOf(slot);
            if (index < 0 || menu.layout.firstItem() + index >= menu.entries.size()) {
                return;
            }
            Object entry = menu.entries.get(menu.layout.firstItem() + index);
            ClickType click = event.getClick();
            switch (entry) {
                case Button button -> {
                    if (home(player, team.get(), button)) {
                        return; // a page or a dialog took the window's place
                    }
                }
                case TeamMenuButton button -> button.click(team.get(), player, click);
                case TeamPermission permission -> permission(player, team.get(), permission, click);
                case InviteButton ignored -> {
                    TeamDialogs.invite(module, player, team.get());
                    return;
                }
                case UUID id when menu.page == Page.MEMBERS -> member(player, id, click);
                case UUID id -> run(player, "uninvite " + module.nameOf(id));
                default -> {
                }
            }
            // Drawn again as it now is - a tick later, once the command has done its work.
            Bukkit.getScheduler().runTask(module.getPlugin(), () -> module.getManager()
                    .getTeamOf(player.getUniqueId())
                    .filter(t -> t.getId().equals(menu.teamId) && player.isOnline()
                            && player.getOpenInventory().getTopInventory().getHolder(false) == menu)
                    .ifPresent(t -> open(module, player, t, menu.page, menu.layout.page())));
        }

        /** @return whether the window was replaced - by a page or a dialog - rather than to be redrawn */
        private boolean home(Player player, Team team, Button button) {
            switch (button) {
                case PROFILE -> {
                    Optional<TeamResult> denied = module.getManager().denied(team, player.getUniqueId(), TeamAction.SETTINGS);
                    if (denied.isPresent()) {
                        module.report(player, denied.get());
                        return false;
                    }
                    TeamDialogs.profile(module, player, team);
                    return true;
                }
                case JOIN_MODE -> {
                    module.report(player, module.getManager().setJoinMode(team, player.getUniqueId(), next(team)));
                    return false;
                }
                case PERMISSIONS -> open(module, player, team, Page.PERMISSIONS, 0);
                case MEMBERS -> open(module, player, team, Page.MEMBERS, 0);
                case INVITES -> open(module, player, team, Page.INVITES, 0);
            }
            return true;
        }

        /** The next join mode the server allows, closed → invitation → open → closed. */
        private JoinMode next(Team team) {
            JoinMode current = module.getManager().joinMode(team);
            JoinMode[] modes = JoinMode.values();
            for (int step = 1; step <= modes.length; step++) {
                JoinMode candidate = modes[(current.ordinal() + step) % modes.length];
                if (module.getSettings().custom().allows(candidate)) {
                    return candidate;
                }
            }
            return current;
        }

        private void permission(Player player, Team team, TeamPermission permission, ClickType click) {
            TeamRole server = permission.serverRole().get();
            TeamRole wanted;
            if (click.isShiftClick()) {
                wanted = null;
            } else {
                TeamRole current = module.getManager().requiredRole(team, permission.key(), server);
                Optional<TeamRole> next = click.isLeftClick() ? current.promoted() : current.demoted();
                if (next.isEmpty()) {
                    return;
                }
                wanted = next.get();
            }
            module.report(player, module.getManager().setPermission(team, player.getUniqueId(),
                    permission.key(), wanted == server ? null : wanted, server));
        }

        private void member(Player player, UUID target, ClickType click) {
            String name = module.nameOf(target);
            if (click == ClickType.SHIFT_RIGHT) {
                run(player, "kick " + name);
            } else if (click.isLeftClick()) {
                run(player, "promote " + name);
            } else if (click.isRightClick()) {
                run(player, "demote " + name);
            }
        }

        /** Runs {@code /team <command>} as the player typed it. */
        private void run(Player player, String command) {
            player.performCommand("hcfcore:team " + command);
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onDrag(InventoryDragEvent event) {
            if (event.getInventory().getHolder(false) instanceof TeamSettingsMenu) {
                event.setCancelled(true);
            }
        }
    }
}
