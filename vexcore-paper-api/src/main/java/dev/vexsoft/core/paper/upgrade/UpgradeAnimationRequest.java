package dev.vexsoft.core.paper.upgrade;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Localized presentation, frozen chance for visual pacing, and an authoritative resolver invoked at the start. */
public record UpgradeAnimationRequest(
    UpgradeAnimationTexts texts, UpgradeAnimationSettings settings, Supplier<CompletableFuture<Boolean>> resolve,
    double successChance
) {

    /** Preserves requests without an explicit frozen chance; fixed-charge settings retain their existing pacing. */
    public UpgradeAnimationRequest(
        UpgradeAnimationTexts texts, UpgradeAnimationSettings settings, Supplier<CompletableFuture<Boolean>> resolve
    ) {
        this(texts, settings, resolve, 0.5);
    }

    public UpgradeAnimationRequest {
        Objects.requireNonNull(texts);
        Objects.requireNonNull(settings);
        Objects.requireNonNull(resolve);
        if (!Double.isFinite(successChance) || successChance < 0 || successChance > 1) {
            throw new IllegalArgumentException("Upgrade success chance must be within 0..1");
        }
    }
}
