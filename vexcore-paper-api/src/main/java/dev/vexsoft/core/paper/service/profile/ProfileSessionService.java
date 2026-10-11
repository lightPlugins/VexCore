package dev.vexsoft.core.paper.service.profile;

import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.profile.ProfileMutation;
import dev.vexsoft.core.api.player.profile.ProfileSelection;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.profile.PaperProfileParticipant;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.List;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

/** Serializes optional profile activation, native capture, readiness, and server-transfer retirement. */
public interface ProfileSessionService extends VexService {

    /** Registers one domain adapter; closing the registration retires its participation. */
    AutoCloseable register(PaperProfileParticipant participant);

    /** Asynchronously initializes every participating domain after the native player is bound. */
    CompletableFuture<Void> initialize(VexPlayer player);

    /** Activates a captured target through save, atomic selection commit, install, and restore. */
    CompletableFuture<Void> activate(VexPlayer player, ProfileSelection selection);

    /**
     * Freezes interactions, executes on the entity thread, commits native/profile state, and rolls back failed writes.
     * The supplier may mutate only containers explicitly scoped to the participating domain.
     */
    <T> CompletableFuture<T> transact(VexPlayer player, Supplier<ProfileMutation<T>> mutation);

    /** Captures native state on the entity thread before saving the complete active context asynchronously. */
    CompletableFuture<Void> save(VexPlayer player);

    /** Captures and drains a session, then releases fenced ownership before disconnect or transfer. */
    CompletableFuture<Void> retire(VexPlayer player);

    /** Reacquires the durable selected context after a failed transfer, using a fresh session identity. */
    CompletableFuture<Void> resume(VexPlayer player);

    /** Returns this account session's initialization/activation readiness. */
    CompletableFuture<Void> readiness(VexPlayer player);

    /** Checks cached participation state for event guards without database access. */
    boolean isReady(VexPlayer player);

    /** Allows movement in a restored, writable session while inventory transactions or saves are pending. */
    default boolean isMovementAllowed(final VexPlayer player) {
        return isReady(player);
    }

    /** Returns the cached context of the domain owning native state, without copying the domain map. */
    default Optional<PlayerProfileContext> activeContext(final VexPlayer player) {
        return Optional.empty();
    }

    /** Checks the adapter's projection filter on the entity thread before a physical possession is persisted. */
    default boolean persistItem(final VexPlayer player, final ItemStack item) {
        return true;
    }

    /** Queues overflow inside the current player transaction, or returns false for account-only participation. */
    boolean deferDelivery(VexPlayer player, List<ItemStack> items);

    /** Collects profile overflow through the same durable wallet/native-inventory boundary. */
    CompletableFuture<Void> collectDeliveries(VexPlayer player);
}
