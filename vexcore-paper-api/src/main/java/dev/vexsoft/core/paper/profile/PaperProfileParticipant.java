package dev.vexsoft.core.paper.profile;

import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.profile.ProfileDomain;
import dev.vexsoft.core.api.player.profile.ProfileSelection;
import dev.vexsoft.core.api.player.VexPlayer;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;

/** Supplies game policy and entity-thread cleanup/restoration to Core's optional profile lifecycle. */
public interface PaperProfileParticipant {

    /** Returns this game's explicitly participating namespace. */
    ProfileDomain domain();

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
