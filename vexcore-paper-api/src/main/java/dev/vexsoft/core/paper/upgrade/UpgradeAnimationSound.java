package dev.vexsoft.core.paper.upgrade;

import dev.vexsoft.core.api.teleport.TransferPresentation.Sound;
import java.util.Objects;

/** One owner-local sound scheduled relative to its named animation event. */
public record UpgradeAnimationSound(int delayTicks, Sound sound) {

    public UpgradeAnimationSound {
        Objects.requireNonNull(sound);
        if (delayTicks < 0 || delayTicks > 200) {
            throw new IllegalArgumentException("Upgrade sound delay must be within 0..200 ticks");
        }
    }
}
