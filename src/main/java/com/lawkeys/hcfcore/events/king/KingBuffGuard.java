package com.lawkeys.hcfcore.events.king;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionEffect;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Nobody helps the King (the project owner's rule, 23/09/2026): no Bard buff, no
 * partner item, no potion thrown by somebody else, no beacon - in either mode. The
 * King fights with the kit and what they drink or eat themselves; harm still lands,
 * an enemy Bard's debuff as much as a sword.
 *
 * <ul>
 *   <li>a splash potion or a lingering cloud from anybody but the King passes the
 *       King by entirely - a potion of healing included, which is how teammates
 *       would heal them;</li>
 *   <li>a beneficial effect given by the plugin - a Bard's, a partner item's, an
 *       effect command's - is refused, except the kit's own
 *       ({@link #grantingKit});</li>
 *   <li>and so is one from a beacon or a conduit.</li>
 * </ul>
 */
public final class KingBuffGuard implements Listener {

    private final Predicate<Player> isKing;
    private final BooleanSupplier grantingKit;

    /**
     * @param grantingKit whether the kit's own effects are being put on right now -
     *                    the one plugin effect the King keeps
     */
    public KingBuffGuard(Predicate<Player> isKing, BooleanSupplier grantingKit) {
        this.isKing = Objects.requireNonNull(isKing, "isKing");
        this.grantingKit = Objects.requireNonNull(grantingKit, "grantingKit");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        for (LivingEntity hit : event.getAffectedEntities()) {
            if (hit instanceof Player king && isKing.test(king) && !king.equals(event.getPotion().getShooter())) {
                event.setIntensity(king, 0.0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCloud(AreaEffectCloudApplyEvent event) {
        event.getAffectedEntities().removeIf(hit -> hit instanceof Player king && isKing.test(king)
                && !king.equals(event.getEntity().getSource()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player king) || !isKing.test(king)) {
            return;
        }
        PotionEffect effect = event.getNewEffect();
        if (effect == null || effect.getType().getEffectCategory() != org.bukkit.potion.PotionEffectType.Category.BENEFICIAL) {
            return; // A removal, or harm: harm always lands.
        }
        boolean outsideHelp = switch (event.getCause()) {
            case PLUGIN -> !grantingKit.getAsBoolean();
            case BEACON, CONDUIT -> true;
            default -> false;
        };
        if (outsideHelp) {
            event.setCancelled(true);
        }
    }
}
