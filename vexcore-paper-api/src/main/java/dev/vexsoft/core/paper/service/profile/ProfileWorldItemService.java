package dev.vexsoft.core.paper.service.profile;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexService;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.function.BiPredicate;

/** Keeps optional profile world items authoritative in storage while native entities remain projections. */
public interface ProfileWorldItemService extends VexService {

    /** Adds an owner-managed storage realm; close the registration before unloading its runtime. */
    default AutoCloseable registerContainerAccess(final BiPredicate<Player, Location> policy) {
        throw new UnsupportedOperationException("Managed profile containers are unavailable");
    }

    /** Checks registered storage realms locally; public containers remain denied by default. */
    default boolean allowsContainer(final Player player, final Location location) {
        return false;
    }

    /** Removes exact native possessions and records their world destination in the same profile transaction. */
    CompletableFuture<Void> drop(VexPlayer player, List<ItemStack> items, Location location);

    /** Grants an authoritative drop and removes or reduces its record in the same native/profile transaction. */
    CompletableFuture<Void> collect(VexPlayer player, Item projection);

    /** Refreshes projections for a ready profile, loading no chunks or inactive personal inventories. */
    CompletableFuture<Void> reconcile(VexPlayer player);

    /** Projects cached authoritative records when their chunk becomes available. */
    void chunkLoaded(World world, int x, int z);

    /** Expires one projection against the database clock without trusting an entity's displayed stack. */
    CompletableFuture<Void> expire(Item projection);

    /** Checks entity provenance locally, without database requests. */
    boolean isProjection(Item item);

    /** Removes connection-owned caches; durable records remain recoverable. */
    void forget(VexPlayer player);
}
