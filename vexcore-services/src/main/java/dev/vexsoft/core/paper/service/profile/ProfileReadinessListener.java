package dev.vexsoft.core.paper.service.profile;

import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import dev.vexsoft.core.api.service.player.PlayerService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.profile.ProfileProjectileRealm;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/** Freezes mutable platform interactions while a participating domain is loading or changing. */
@Dependencies({PlayerService.class, ProfileSessionService.class})
public final class ProfileReadinessListener implements Listener {

    private final PlayerService players;
    private final ProfileSessionService profiles;

    public ProfileReadinessListener(final VexServiceRegistry services) {
        VexServiceRegistry checked = Objects.requireNonNull(services, "services");
        players = checked.require(PlayerService.class);
        profiles = checked.require(ProfileSessionService.class);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void click(final InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && blocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void drag(final InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && blocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void interact(final PlayerInteractEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void drop(final PlayerDropItemEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void pickup(final EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && blocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void swap(final PlayerSwapHandItemsEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void move(final PlayerMoveEvent event) {
        if (event.hasChangedPosition() && blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void damage(final EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && blocked(player)) {
            event.setCancelled(true);
        }
        if (event instanceof EntityDamageByEntityEvent attack) {
            var attacker = attack.getDamager();
            if (attacker instanceof Player player && blocked(player)
                || attacker instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter
                    && blocked(shooter)) {
                event.setCancelled(true);
            }
            if (attacker instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
                var context = players.find(shooter.getUniqueId()).flatMap(profiles::activeContext).orElse(null);
                if (!ProfileProjectileRealm.current(projectile, context)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void experience(final PlayerPickupExperienceEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void projectile(final ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter)) {
            return;
        }
        if (blocked(shooter)) {
            event.setCancelled(true);
            return;
        }
        players.find(shooter.getUniqueId()).flatMap(profiles::activeContext)
            .ifPresent(context -> ProfileProjectileRealm.bind(event.getEntity(), context));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void breakBlock(final BlockBreakEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void placeBlock(final BlockPlaceEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void entityInteraction(final PlayerInteractEntityEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void consume(final PlayerItemConsumeEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void fillBucket(final PlayerBucketFillEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void emptyBucket(final PlayerBucketEmptyEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void shoot(final EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player && blocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void food(final FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && blocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void command(final PlayerCommandPreprocessEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private boolean blocked(final Player player) {
        return players.find(player.getUniqueId()).map(value -> !profiles.isReady(value)).orElse(true);
    }
}
