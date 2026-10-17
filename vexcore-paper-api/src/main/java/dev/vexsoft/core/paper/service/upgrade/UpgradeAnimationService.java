package dev.vexsoft.core.paper.service.upgrade;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.upgrade.UpgradeAnimationOutcome;
import dev.vexsoft.core.paper.upgrade.UpgradeAnimationRequest;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;

/** Reusable owner-bound pixel upgrade ritual; gameplay reservations and recovery belong to its caller. */
public interface UpgradeAnimationService extends VexService {

    /** Starts a reserved subject's ritual, resolves once at start, and completes exactly once after cleanup. */
    CompletableFuture<UpgradeAnimationOutcome> play(Player player, UpgradeAnimationRequest request);

    /** Cancels the viewer's ritual without cancelling an already running durable resolver. */
    void cancel(Player player);

    /** Skips remaining presentation and resolves once, completing with the durable result without refund or reroll. */
    void skip(Player player);

    /** Returns whether the viewer currently owns an active ritual in this plugin scope. */
    boolean active(Player player);
}
