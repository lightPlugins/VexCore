package dev.vexsoft.core.paper.upgrade;

import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Viewer-localized screen content; the progress component supports the literal %progress% placeholder. */
public record UpgradeAnimationTexts(
    Component title, Component subject, Component upgrade, Component progress,
    Component charging, Component holding, Component success, Component failure, Component skipHint
) {

    /** Preserves callers that do not display an optional localized skip instruction. */
    public UpgradeAnimationTexts(
        Component title, Component subject, Component upgrade, Component progress,
        Component charging, Component holding, Component success, Component failure
    ) {
        this(title, subject, upgrade, progress, charging, holding, success, failure, Component.empty());
    }

    public UpgradeAnimationTexts {
        Objects.requireNonNull(title);
        Objects.requireNonNull(subject);
        Objects.requireNonNull(upgrade);
        Objects.requireNonNull(progress);
        Objects.requireNonNull(charging);
        Objects.requireNonNull(holding);
        Objects.requireNonNull(success);
        Objects.requireNonNull(failure);
        skipHint = skipHint == null ? Component.empty() : skipHint;
    }
}
