package dev.vexsoft.core.paper.service.profile;

import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.service.player.PlayerService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.profile.ProfileItemRealm;
import dev.vexsoft.core.paper.profile.ProfileProjectileRealm;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.block.Container;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;

/** Protects scoped physical possessions from other profiles, automation and public persistent containers. */
@Dependencies({PlayerService.class, ProfileSessionService.class, ScheduleService.class, ProfileWorldItemService.class})
public final class ProfileItemListener implements Listener {

    private final PlayerService players;
    private final ProfileSessionService sessions;
    private final ScheduleService schedules;
    private final ProfileWorldItemService worldItems;
    private final Map<UUID, PendingDeath> deaths = new ConcurrentHashMap<>();

    public ProfileItemListener(final VexServiceRegistry services) {
        players = services.require(PlayerService.class);
        sessions = services.require(ProfileSessionService.class);
        schedules = services.require(ScheduleService.class);
        worldItems = services.require(ProfileWorldItemService.class);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drop(final PlayerDropItemEvent event) {
        PlayerProfileContext context = context(event.getPlayer());
        if (!ProfileItemRealm.permits(event.getItemDrop().getItemStack(), context)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void scopeDrop(final PlayerDropItemEvent event) {
        PlayerProfileContext context = context(event.getPlayer());
        if (context == null) {
            return;
        }
        // Vanilla already removed the exact source slot, including Q/Ctrl-Q inside an inventory.
        // Do not cancel and reconstruct that removal by searching for an equivalent stack.
        ItemStack stack = event.getItemDrop().getItemStack().clone();
        ProfileItemRealm.bind(stack, context);
        event.getItemDrop().setItemStack(stack);
        event.getItemDrop().setCanMobPickup(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pickup(final EntityPickupItemEvent event) {
        var stack = event.getItem().getItemStack();
        if (!(event.getEntity() instanceof Player player)) {
            if (ProfileItemRealm.realm(stack) != null || worldItems.isProjection(event.getItem())) {
                event.setCancelled(true);
            }
            return;
        }
        PlayerProfileContext context = context(player);
        if (context != null) {
            if (!ProfileItemRealm.permits(stack, context)) {
                event.setCancelled(true);
            } else if (worldItems.isProjection(event.getItem())) {
                // Existing journal projections still settle their authoritative record; new native drops do not.
                event.setCancelled(true);
                players.find(player.getUniqueId()).ifPresent(owner -> report(worldItems.collect(owner, event.getItem())));
            }
        } else if (ProfileItemRealm.realm(stack) != null || worldItems.isProjection(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void merge(final ItemMergeEvent event) {
        if (worldItems.isProjection(event.getEntity()) || worldItems.isProjection(event.getTarget())
            || !Objects.equals(ProfileItemRealm.realm(event.getEntity().getItemStack()),
            ProfileItemRealm.realm(event.getTarget().getItemStack()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void arrow(final PlayerPickupArrowEvent event) {
        PlayerProfileContext context = context(event.getPlayer());
        if (!ProfileProjectileRealm.permitsPickup(event.getArrow(), context)) {
            event.setCancelled(true);
        } else if (context != null) {
            var owner = players.require(event.getPlayer().getUniqueId());
            if (!sessions.isReady(owner)) {
                event.setCancelled(true);
                return;
            }
            if (!ProfileProjectileRealm.current(event.getArrow(), null)) {
                var stack = event.getItem().getItemStack().clone();
                ProfileItemRealm.bind(stack, context);
                event.getItem().setItemStack(stack);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void tracked(final PlayerTrackEntityEvent event) {
        if (event.getEntity() instanceof Item item
            && !ProfileItemRealm.permits(item.getItemStack(), context(event.getPlayer()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hopper(final InventoryPickupItemEvent event) {
        if (ProfileItemRealm.realm(event.getItem().getItemStack()) != null || worldItems.isProjection(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void automation(final InventoryMoveItemEvent event) {
        if (ProfileItemRealm.realm(event.getItem()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void container(final PlayerInteractEvent event) {
        var block = event.getClickedBlock();
        if (context(event.getPlayer()) != null && block != null && block.getState(false) instanceof Container) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void itemFrame(final PlayerInteractEntityEvent event) {
        if (context(event.getPlayer()) != null && (event.getRightClicked() instanceof ItemFrame
            || event.getRightClicked() instanceof ArmorStand || event.getRightClicked() instanceof InventoryHolder)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void armorStand(final PlayerArmorStandManipulateEvent event) {
        if (context(event.getPlayer()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void openPhysicalContainer(final InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && context(player) != null
            && (event.getInventory().getHolder(false) instanceof Container
                || event.getInventory().getHolder(false) instanceof Entity)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(final PlayerDeathEvent event) {
        PlayerProfileContext context = context(event.getPlayer());
        if (context == null || event.getKeepInventory()) {
            return;
        }
        var drops = event.getDrops().stream().map(ItemStack::clone).toList();
        deaths.put(context.playerId(), new PendingDeath(context, drops, event.getPlayer().getLocation().clone()));
        event.getDrops().clear();
        // Keep the durable source until respawn can atomically replace it with the shared world journal.
        // A disconnect or failed settlement retains the possessions rather than replaying a world grant.
        event.setKeepInventory(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void respawn(final PlayerRespawnEvent event) {
        PendingDeath death = deaths.remove(event.getPlayer().getUniqueId());
        if (death != null) {
            schedules.runForLater(event.getPlayer(), 1, () -> players.find(death.context().playerId()).ifPresent(owner -> {
                if (owner.isCurrentContext(death.context()) && sessions.isReady(owner)) {
                    report(worldItems.drop(owner, death.items(), death.location()));
                }
            }), null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void joined(final PlayerJoinEvent event) {
        reconcile(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleported(final PlayerTeleportEvent event) {
        reconcile(event.getPlayer());
    }

    @EventHandler
    public void chunkLoaded(final ChunkLoadEvent event) {
        worldItems.chunkLoaded(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void despawn(final ItemDespawnEvent event) {
        if (worldItems.isProjection(event.getEntity())) {
            event.setCancelled(true);
            report(worldItems.expire(event.getEntity()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void itemDamage(final EntityDamageEvent event) {
        if (event.getEntity() instanceof Item item && worldItems.isProjection(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(final PlayerQuitEvent event) {
        deaths.remove(event.getPlayer().getUniqueId());
        players.find(event.getPlayer().getUniqueId()).ifPresent(worldItems::forget);
    }

    private void reconcile(final Player platform) {
        players.find(platform.getUniqueId()).ifPresent(owner -> report(sessions.readiness(owner)
            .thenCompose(ignored -> worldItems.reconcile(owner))));
    }

    private void report(final CompletableFuture<Void> operation) {
        operation.exceptionally(failure -> {
            schedules.getOwner().getLogger().log(Level.WARNING,
                "Profile world item settlement could not complete", failure);
            return null;
        });
    }

    private PlayerProfileContext context(final Player player) {
        return players.find(player.getUniqueId()).flatMap(sessions::activeContext).orElse(null);
    }

    private record PendingDeath(PlayerProfileContext context, List<ItemStack> items, Location location) {
    }
}
