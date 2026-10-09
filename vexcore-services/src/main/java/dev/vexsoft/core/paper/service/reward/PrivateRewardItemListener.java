package dev.vexsoft.core.paper.service.reward;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;

/** Keeps reward overflow invisible and inaccessible to anyone except its persistent owner. */
@Dependencies(ScheduleService.class)
public final class PrivateRewardItemListener implements Listener {

    static final NamespacedKey OWNER = new NamespacedKey("vexcore", "private_reward_owner");
    private final ScheduleService schedules;
    private final Map<UUID, Set<Item>> loaded = new ConcurrentHashMap<>();

    /** Captures the Core scheduler for owner visibility restoration. */
    public PrivateRewardItemListener(final VexServiceRegistry services) {
        schedules = services.require(ScheduleService.class);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pickup(final EntityPickupItemEvent event) {
        String owner = owner(event.getItem());
        if (owner != null && (!(event.getEntity() instanceof Player player)
            || !owner.equals(player.getUniqueId().toString()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hopper(final InventoryPickupItemEvent event) {
        if (owner(event.getItem()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void merge(final ItemMergeEvent event) {
        if (!Objects.equals(owner(event.getEntity()), owner(event.getTarget()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void load(final EntitiesLoadEvent event) {
        event.getEntities().forEach(entity -> {
            if (entity instanceof Item item) {
                restore(item);
            }
        });
    }

    @EventHandler
    public void join(final PlayerJoinEvent event) {
        schedules.runForLater(event.getPlayer(), 1L, () -> {
            Player player = event.getPlayer();
            loaded.getOrDefault(player.getUniqueId(), Set.of()).forEach(item ->
                player.showEntity(schedules.getOwner(), item));
        }, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void spawn(final ItemSpawnEvent event) {
        restore(event.getEntity());
    }

    @EventHandler
    public void remove(final EntityRemoveFromWorldEvent event) {
        if (event.getEntity() instanceof Item item && owner(item) != null) {
            try {
                loaded.computeIfPresent(UUID.fromString(owner(item)), (identity, items) -> {
                    items.remove(item);
                    return items.isEmpty() ? null : items;
                });
            } catch (IllegalArgumentException failure) {
                schedules.getOwner().getLogger().warning("Discarded invalid private reward owner on removed item");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void track(final PlayerTrackEntityEvent event) {
        if (event.getEntity() instanceof Item item && owner(item) != null
            && !event.getPlayer().getUniqueId().toString().equals(owner(item))) {
            event.setCancelled(true);
        }
    }

    private void restore(final Item item) {
        String owner = owner(item);
        if (owner == null) {
            return;
        }
        item.setVisibleByDefault(false);
        item.setCanMobPickup(false);
        try {
            UUID identity = UUID.fromString(owner);
            loaded.compute(identity, (ignored, current) -> {
                Set<Item> items = current == null ? ConcurrentHashMap.newKeySet() : current;
                items.add(item);
                return items;
            });
            Player player = Bukkit.getPlayer(identity);
            item.setOwner(identity);
            if (player != null) {
                schedules.runFor(player, () -> player.showEntity(schedules.getOwner(), item));
            }
        } catch (IllegalArgumentException failure) {
            item.remove();
        }
    }

    private static String owner(final Item item) {
        return item.getPersistentDataContainer().get(OWNER, PersistentDataType.STRING);
    }
}
