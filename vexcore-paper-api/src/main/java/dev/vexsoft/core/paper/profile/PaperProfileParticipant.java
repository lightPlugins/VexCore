package dev.vexsoft.core.paper.profile;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import dev.vexsoft.core.api.player.profile.ProfileSelection;
import dev.vexsoft.core.api.world.ServerPosition;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Supplies game policy and entity-thread cleanup/restoration to Core's optional profile lifecycle. */
public interface PaperProfileParticipant {

    /** Returns this game's explicitly participating namespace. */
    ProfileDomain domain();

    /** Advertises whether this game has the configuration required to accept routed arrivals. */
    default boolean acceptingTransfers() {
        return true;
    }

    /** Resolves or creates the selected membership asynchronously before gameplay becomes ready. */
    CompletableFuture<ProfileSelection> bootstrap(VexPlayer player);

    /** Validates and retires transient state on the player's execution context before native capture. */
    void prepare(Player player, PlayerProfileContext previous);

    /** Reconstructs game projections and contributions after the target save has been installed. */
    void restore(Player player, PlayerProfileContext current);

    /** Returns a safe activation location on the player's execution context. */
    Location spawn(Player player);

    /** Resolves a validated destination asynchronously; the initial flag distinguishes login from a switch. */
    default CompletableFuture<Location> destination(final Player player, final PaperProfileState state,
        final boolean initial) {
        return CompletableFuture.completedFuture(spawn(player));
    }

    /** Resolves an explicit arrival before readiness; future world adapters may asynchronously load its world here. */
    default CompletableFuture<Location> arrivalDestination(final Player player, final ServerPosition destination) {
        var key = destination.world();
        var world = Bukkit.getWorld(new NamespacedKey(key.namespace(), key.value()));
        if (world == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Arrival world is unavailable"));
        }
        return CompletableFuture.completedFuture(new Location(world, destination.x(), destination.y(), destination.z(),
            destination.yaw(), destination.pitch()));
    }

    /** Captures a game-approved position on the entity thread, or null when no position should be retained. */
    default PaperProfileState.Position capturePosition(final Player player) {
        return null;
    }

    /** Bounds destination resolution and teleport after the durable selection has committed. */
    default long restoreTimeoutSeconds() {
        return 30;
    }

    /** Excludes transient projections from native inventory persistence. */
    default boolean persistItem(final ItemStack item) {
        return true;
    }

    /** Identifies a unique possession on the entity thread, allowing games to reject duplicated individuals. */
    default Optional<String> possessionIdentity(final ItemStack item) {
        return Optional.empty();
    }

    /** Validates native and game-owned possessions on the entity thread before any save can commit. */
    default void validatePossessions(final VexPlayer player, final List<String> nativeIdentities) {
    }
}
