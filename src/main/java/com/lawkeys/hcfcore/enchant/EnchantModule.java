package com.lawkeys.hcfcore.enchant;

import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.enchant.command.EnchantCommand;
import com.lawkeys.hcfcore.enchant.listener.EnchantListener;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.util.Cooldowns;
import com.lawkeys.hcfcore.util.Durations;
import com.lawkeys.hcfcore.util.EffectCaps;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Custom enchants (FEATURES.md section 7): the well-known HCF and kitmap enchants,
 * as the project owner asked on 12/09/2026 - effect enchants (Fire Resistance,
 * Speed, Night Vision...), Hellforged, Implanted, Recover and Autosmelt.
 *
 * <p>Integrated in the core, as decided on 28/08/2026. An enchant lives on the item
 * itself - its level in the item's persistent data, one lore line to show it - so a
 * kit, a chest, a death drop or a trade carries it like anything else about the
 * item. It is put there by a staff command, or by a book dragged onto the item, and
 * books can be handed out by anything that runs a command: a shop, a crate, a
 * redeem code.
 *
 * <p>The rules that need no server - which item a kind goes on, how a book merges -
 * are in {@link EnchantTarget} and {@link EnchantLevels}, pure and tested.
 */
public final class EnchantModule {

    public static final String ADMIN_PERMISSION = "hcfcore.enchant.admin";

    /** Effect durations: long enough that Night Vision never reaches its last-seconds flicker. */
    private static final int EFFECT_TICKS = 400;
    private static final int REFRESH_BELOW_TICKS = 300;
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final Plugin plugin;
    private final LangManager lang;
    private final NamespacedKey loreLines;
    private final NamespacedKey bookId;
    private final NamespacedKey bookLevel;

    private volatile boolean enabled = true;
    private volatile Map<String, CustomEnchant> enchants = Map.of();
    private volatile Map<String, PotionEffectType> effects = Map.of();
    /** Main thread only. */
    private final Map<UUID, Set<PotionEffectType>> applied = new HashMap<>();
    private volatile EffectCaps effectCaps = EffectCaps.NONE;
    private final Map<UUID, Map<String, Long>> lastFed = new HashMap<>();
    private final Cooldowns recoverCooldowns = new Cooldowns();
    private Map<Material, ItemStack> smelting;
    private BukkitTask task;

    public EnchantModule(Plugin plugin, LangManager lang) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lang = Objects.requireNonNull(lang, "lang");
        this.loreLines = new NamespacedKey(plugin, "ce_lore_lines");
        this.bookId = new NamespacedKey(plugin, "ce_book");
        this.bookLevel = new NamespacedKey(plugin, "ce_book_level");
    }

    /** The effect caps of {@code limiters.yml}, asked before giving an effect; filled after startup. */
    public void setEffectCaps(EffectCaps effectCaps) {
        this.effectCaps = Objects.requireNonNull(effectCaps, "effectCaps");
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public LangManager getLang() {
        return lang;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<CustomEnchant> list() {
        return List.copyOf(enchants.values());
    }

    public Optional<CustomEnchant> find(String id) {
        return Optional.ofNullable(id == null ? null : enchants.get(id.trim().toLowerCase(Locale.ROOT)));
    }

    public void enable() {
        reloadSettings();
        plugin.getServer().getPluginManager().registerEvents(new EnchantListener(this), plugin);
        PluginCommand command = plugin.getServer().getPluginCommand("cenchant");
        if (command == null) {
            plugin.getLogger().severe("The 'cenchant' command is missing from plugin.yml.");
        } else {
            EnchantCommand executor = new EnchantCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickWorn, 20L, 20L);
    }

    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        // The effects this module gave are taken back, so a plugin disabled while the
        // server runs leaves nobody with permanent Speed.
        for (Player player : Bukkit.getOnlinePlayers()) {
            takeBackEffects(player, Set.of());
        }
        applied.clear();
    }

    // ------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------

    public void reloadSettings() {
        // Rebuilt on next use: a datapack reloaded since may have changed the recipes.
        this.smelting = null;
        ConfigurationSection section = ConfigManager.loadFile(plugin, "enchants.yml");
        if (section == null) {
            plugin.getLogger().warning("enchants.yml is missing or empty - no custom enchants.");
            this.enchants = Map.of();
            this.effects = Map.of();
            return;
        }
        this.enabled = section.getBoolean("enabled", true);
        Map<String, CustomEnchant> loaded = new LinkedHashMap<>();
        Map<String, PotionEffectType> loadedEffects = new HashMap<>();
        ConfigurationSection list = section.getConfigurationSection("enchants");
        if (list != null) {
            for (String rawId : list.getKeys(false)) {
                String id = rawId.toLowerCase(Locale.ROOT);
                ConfigurationSection entry = list.getConfigurationSection(rawId);
                CustomEnchant enchant = entry == null ? null : load(id, entry);
                if (enchant == null) {
                    continue;
                }
                if (enchant.kind() == EnchantKind.EFFECT) {
                    NamespacedKey key = NamespacedKey.fromString(enchant.effect().toLowerCase(Locale.ROOT));
                    PotionEffectType type = key == null ? null : Registry.MOB_EFFECT.get(key);
                    if (type == null) {
                        warn(id, "effect '" + enchant.effect() + "' does not exist on this server; skipped.");
                        continue;
                    }
                    loadedEffects.put(id, type);
                }
                loaded.put(id, enchant);
            }
        }
        this.enchants = loaded;
        this.effects = loadedEffects;
    }

    private CustomEnchant load(String id, ConfigurationSection entry) {
        if (!VALID_ID.matcher(id).matches()) {
            warn(id, "an id is 1 to 32 letters, digits, - or _; skipped.");
            return null;
        }
        EnchantKind kind;
        try {
            kind = EnchantKind.valueOf(entry.getString("type", "").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warn(id, "type must be one of effect, hellforged, implanted, recover, autosmelt; skipped.");
            return null;
        }
        Set<EnchantTarget> targets = new HashSet<>();
        for (String word : entry.getStringList("applies-to")) {
            Set<EnchantTarget> named = EnchantTarget.parse(word);
            if (named.isEmpty()) {
                warn(id, "applies-to names '" + word + "', which is not a kind of item; ignored.");
            }
            targets.addAll(named);
        }
        if (targets.isEmpty()) {
            warn(id, "applies-to names no kind of item; skipped.");
            return null;
        }
        String effect = entry.getString("effect", "");
        if (kind == EnchantKind.EFFECT && effect.isBlank()) {
            warn(id, "an effect enchant needs an effect; skipped.");
            return null;
        }
        int maxLevel = entry.getInt("max-level", 1);
        if (maxLevel < 1) {
            warn(id, "max-level must be at least 1; skipped.");
            return null;
        }
        return new CustomEnchant(id, entry.getString("name", id), kind, maxLevel, targets, effect,
                Math.max(0.0, entry.getDouble(amountKey(kind), defaultAmount(kind))),
                Math.max(1L, Durations.capSeconds(entry.getLong("interval-seconds", 5L), "enchants.yml: interval-seconds", plugin.getLogger()::warning)),
                Math.max(1L, Durations.capSeconds(entry.getLong("regeneration-seconds", 5L), "enchants.yml: regeneration-seconds", plugin.getLogger()::warning)),
                Math.max(0L, Durations.capSeconds(entry.getLong("cooldown-seconds", 60L), "enchants.yml: cooldown-seconds", plugin.getLogger()::warning)));
    }

    /** @return the {@code enchants.yml} key of the one number a kind reads */
    private static String amountKey(EnchantKind kind) {
        return switch (kind) {
            case HELLFORGED -> "repair-per-level";
            case IMPLANTED -> "food-per-level";
            case RECOVER -> "below-health";
            default -> "amount";
        };
    }

    private static double defaultAmount(EnchantKind kind) {
        return switch (kind) {
            case HELLFORGED, IMPLANTED -> 1.0;
            case RECOVER -> 8.0;
            default -> 0.0;
        };
    }

    private void warn(String id, String message) {
        plugin.getLogger().warning("enchants.yml: '" + id + "': " + message);
    }

    // ------------------------------------------------------------------
    // Items
    // ------------------------------------------------------------------

    private NamespacedKey key(CustomEnchant enchant) {
        return new NamespacedKey(plugin, "ce_" + enchant.id());
    }

    /**
     * @return the custom enchants on an item, in configuration order - each at most at
     *         today's {@code max-level}, so lowering it in {@code enchants.yml} lowers
     *         the items already made
     */
    public Map<CustomEnchant, Integer> levels(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return Map.of();
        }
        var data = item.getPersistentDataContainer();
        Map<CustomEnchant, Integer> levels = new LinkedHashMap<>();
        for (CustomEnchant enchant : enchants.values()) {
            Integer level = data.get(key(enchant), PersistentDataType.INTEGER);
            if (level != null && level > 0) {
                levels.put(enchant, Math.min(level, enchant.maxLevel()));
            }
        }
        return levels;
    }

    /** @return whether this enchant can go on this item at all */
    public static boolean fits(CustomEnchant enchant, ItemStack item) {
        return item != null && !item.isEmpty()
                && EnchantTarget.of(item.getType().name()).map(enchant::fits).orElse(false);
    }

    /** Sets a level outright, {@code 0} removing it, and redraws the item's lines. */
    public void set(ItemStack item, CustomEnchant enchant, int level) {
        item.editPersistentDataContainer(data -> {
            if (level > 0) {
                data.set(key(enchant), PersistentDataType.INTEGER, Math.min(level, enchant.maxLevel()));
            } else {
                data.remove(key(enchant));
            }
        });
        redraw(item);
    }

    /**
     * Rewrites the enchant lines at the top of the lore. How many lines are this
     * module's is kept on the item, so the rest of the lore - a kit's description, a
     * name tag's - is never touched.
     */
    private void redraw(ItemStack item) {
        Map<CustomEnchant, Integer> levels = levels(item);
        Integer owned = item.getPersistentDataContainer().get(loreLines, PersistentDataType.INTEGER);
        item.editMeta(meta -> {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            for (int i = 0; i < (owned == null ? 0 : owned) && !lore.isEmpty(); i++) {
                lore.remove(0);
            }
            List<Component> lines = new ArrayList<>();
            levels.forEach((enchant, level) -> lines.add(line(lang.get(EnchantMessages.LORE_LINE,
                    "level", EnchantLevels.roman(level), "enchant", LangManager.colorize(enchant.displayName())))));
            lines.addAll(lore);
            meta.lore(lines.isEmpty() ? null : lines);
            // The glint says "enchanted" on an item that has only custom enchants.
            meta.setEnchantmentGlintOverride(levels.isEmpty() ? null : Boolean.TRUE);
        });
        item.editPersistentDataContainer(data -> {
            if (levels.isEmpty()) {
                data.remove(loreLines);
            } else {
                data.set(loreLines, PersistentDataType.INTEGER, levels.size());
            }
        });
    }

    private static Component line(String legacy) {
        return LEGACY.deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }

    /** @return a book that gives this enchant at this level when dragged onto an item */
    public ItemStack book(CustomEnchant enchant, int level) {
        int capped = Math.max(1, Math.min(level, enchant.maxLevel()));
        ItemStack book = ItemStack.of(Material.BOOK);
        book.editPersistentDataContainer(data -> {
            data.set(bookId, PersistentDataType.STRING, enchant.id());
            data.set(bookLevel, PersistentDataType.INTEGER, capped);
        });
        book.editMeta(meta -> {
            meta.customName(line(lang.get(EnchantMessages.BOOK_NAME,
                    "level", EnchantLevels.roman(capped), "enchant", LangManager.colorize(enchant.displayName()))));
            meta.lore(List.of(line(lang.get(EnchantMessages.BOOK_LORE))));
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
        });
        return book;
    }

    /** One custom enchant book: which enchant, and at which level. */
    public record Book(CustomEnchant enchant, int level) {
    }

    public Optional<Book> bookOf(ItemStack item) {
        if (item == null || item.isEmpty() || item.getType() != Material.BOOK) {
            return Optional.empty();
        }
        var data = item.getPersistentDataContainer();
        String id = data.get(bookId, PersistentDataType.STRING);
        Integer level = data.get(bookLevel, PersistentDataType.INTEGER);
        return id == null || level == null ? Optional.empty() : find(id).map(enchant -> new Book(enchant, level));
    }

    // ------------------------------------------------------------------
    // What worn enchants do
    // ------------------------------------------------------------------

    /** @return the worn enchants that apply: each on a piece of the kind it goes on */
    private Map<CustomEnchant, Integer> worn(Player player) {
        Map<CustomEnchant, Integer> worn = new LinkedHashMap<>();
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == null || piece.isEmpty()) {
                continue;
            }
            Optional<EnchantTarget> kind = EnchantTarget.of(piece.getType().name());
            if (kind.isEmpty()) {
                continue;
            }
            levels(piece).forEach((enchant, level) -> {
                if (enchant.isWorn() && enchant.fits(kind.get())) {
                    worn.merge(enchant, level, Math::max);
                }
            });
        }
        return worn;
    }

    /** Every second: effect enchants, and Implanted's feeding. */
    private void tickWorn() {
        if (!enabled || enchants.isEmpty()) {
            // Switched off by a reload: what was given goes now, not in twenty seconds.
            if (!applied.isEmpty()) {
                Bukkit.getOnlinePlayers().forEach(player -> takeBackEffects(player, Set.of()));
            }
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<CustomEnchant, Integer> worn = worn(player);
            Map<PotionEffectType, Integer> wanted = new HashMap<>();
            worn.forEach((enchant, level) -> {
                if (enchant.kind() == EnchantKind.EFFECT) {
                    PotionEffectType type = effects.get(enchant.id());
                    if (type != null) {
                        wanted.merge(type, level - 1, Math::max);
                    }
                } else if (enchant.kind() == EnchantKind.IMPLANTED) {
                    feed(player, enchant, level, now);
                }
            });
            // Within the effect caps, so what is given is what is recognised as ours.
            wanted.forEach((type, amplifier) -> {
                int allowed = effectCaps.allowed(type, amplifier);
                if (allowed >= 0) {
                    giveEffect(player, type, allowed);
                }
            });
            takeBackEffects(player, wanted.keySet());
        }
    }

    /**
     * Gives an enchant's effect, never over a stronger one the player has from
     * elsewhere - a Speed II potion is not cut down to a Speed I boot.
     */
    private void giveEffect(Player player, PotionEffectType type, int amplifier) {
        PotionEffect current = player.getPotionEffect(type);
        if (current != null && current.getAmplifier() > amplifier) {
            return;
        }
        if (current == null || current.getAmplifier() < amplifier
                || (isOurs(current) && current.getDuration() < REFRESH_BELOW_TICKS)) {
            player.addPotionEffect(new PotionEffect(type, EFFECT_TICKS, amplifier, true, false, true));
        }
        applied.computeIfAbsent(player.getUniqueId(), id -> new HashSet<>()).add(type);
    }

    /**
     * Takes back what was given for a piece no longer worn - only when the effect in
     * place is still this module's, recognised by its signature (ambient, no
     * particles): a potion drunk since is left alone.
     */
    private void takeBackEffects(Player player, Set<PotionEffectType> stillWanted) {
        Set<PotionEffectType> given = applied.get(player.getUniqueId());
        if (given == null) {
            return;
        }
        for (Iterator<PotionEffectType> it = given.iterator(); it.hasNext(); ) {
            PotionEffectType type = it.next();
            if (stillWanted.contains(type)) {
                continue;
            }
            PotionEffect current = player.getPotionEffect(type);
            if (current != null && isOurs(current)) {
                player.removePotionEffect(type);
            }
            it.remove();
        }
    }

    private static boolean isOurs(PotionEffect effect) {
        return effect.isAmbient() && !effect.hasParticles() && effect.getDuration() <= EFFECT_TICKS;
    }

    private void feed(Player player, CustomEnchant enchant, int level, long now) {
        Map<String, Long> fedAt = lastFed.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>());
        Long last = fedAt.get(enchant.id());
        if (last != null && now - last < enchant.intervalSeconds() * 1000L) {
            return;
        }
        fedAt.put(enchant.id(), now);
        if (player.getFoodLevel() < 20) {
            player.setFoodLevel(Math.min(20, player.getFoodLevel() + (int) Math.round(level * enchant.amount())));
        }
    }

    /**
     * A wearer took damage: Hellforged repairs its pieces, Recover may fire.
     *
     * @param healthAfter the wearer's health once this hit lands
     */
    public void onHurt(Player player, double healthAfter) {
        if (!enabled || enchants.isEmpty()) {
            return;
        }
        ItemStack[] armour = player.getInventory().getArmorContents();
        boolean repaired = false;
        for (ItemStack piece : armour) {
            if (piece == null || piece.isEmpty()) {
                continue;
            }
            Optional<EnchantTarget> kind = EnchantTarget.of(piece.getType().name());
            for (Map.Entry<CustomEnchant, Integer> entry : levels(piece).entrySet()) {
                CustomEnchant enchant = entry.getKey();
                if (enchant.kind() == EnchantKind.HELLFORGED && kind.isPresent() && enchant.fits(kind.get())
                        && piece.getItemMeta() instanceof Damageable worn && worn.hasDamage()) {
                    int restore = (int) Math.round(entry.getValue() * enchant.amount());
                    piece.editMeta(Damageable.class, meta -> meta.setDamage(Math.max(0, meta.getDamage() - restore)));
                    repaired = true;
                }
            }
        }
        if (repaired) {
            player.getInventory().setArmorContents(armour);
        }
        worn(player).forEach((enchant, level) -> {
            if (enchant.kind() == EnchantKind.RECOVER && healthAfter > 0 && healthAfter <= enchant.amount()) {
                recover(player, enchant, level);
            }
        });
    }

    private void recover(Player player, CustomEnchant enchant, int level) {
        long now = System.currentTimeMillis();
        // One wait per player, whichever recover enchant fired, as before.
        if (!recoverCooldowns.tryUse(player.getUniqueId(), "recover", enchant.cooldownSeconds(), now)) {
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                (int) Math.min(Integer.MAX_VALUE, enchant.durationSeconds() * 20L), level - 1));
        lang.send(player, EnchantMessages.RECOVERED, "enchant", LangManager.colorize(enchant.displayName()));
    }

    /**
     * What a furnace would make of a drop, for Autosmelt.
     *
     * <p>Read from the server's own furnace recipes - built once, on first use -
     * rather than a list written here, so it smelts exactly what a furnace smelts,
     * datapacks included.
     *
     * @return the smelted stack, as many as the drop, or empty when a furnace would not take it
     */
    public Optional<ItemStack> smelted(ItemStack drop) {
        if (smelting == null) {
            Map<Material, ItemStack> table = new EnumMap<>(Material.class);
            for (Iterator<Recipe> it = Bukkit.recipeIterator(); it.hasNext(); ) {
                if (it.next() instanceof FurnaceRecipe recipe
                        && recipe.getInputChoice() instanceof RecipeChoice.MaterialChoice choice) {
                    for (Material input : choice.getChoices()) {
                        table.putIfAbsent(input, recipe.getResult());
                    }
                }
            }
            smelting = table;
        }
        ItemStack result = smelting.get(drop.getType());
        if (result == null) {
            return Optional.empty();
        }
        ItemStack out = result.clone();
        out.setAmount(Math.min(out.getMaxStackSize(), drop.getAmount() * result.getAmount()));
        return Optional.of(out);
    }

    /** Forgets a player who has left. */
    public void forget(UUID playerId) {
        applied.remove(playerId);
        lastFed.remove(playerId);
        recoverCooldowns.forget(playerId);
    }
}
