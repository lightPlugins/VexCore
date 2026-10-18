package dev.vexsoft.core.paper.upgrade;

import dev.vexsoft.core.api.teleport.TransferPresentation.Sound;
import java.util.Objects;

/** Viewer-local background track with a fixed tempo and an optional repeat interval. */
public record UpgradeAnimationMusic(Sound sound, int durationTicks, boolean loop) {

    public UpgradeAnimationMusic {
        Objects.requireNonNull(sound, "sound");
        if (sound.pitch() != 1.0F || durationTicks < 20 || durationTicks > 12000) {
            throw new IllegalArgumentException("Upgrade music needs its original pitch and a bounded duration");
        }
    }
}
