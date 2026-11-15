package dev.vexsoft.core.paper.screenui.progression;

import java.util.Objects;
import net.kyori.adventure.text.Component;

/** The localized text and within-level progress of one committed XP gain. */
public record ExperienceGainNotice(Component amount, Component source, Component status,
                                  double previousProgress, double progress) implements ProgressionOverlay {

    public ExperienceGainNotice {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(status, "status");
        if (!(previousProgress >= 0 && progress >= previousProgress && progress <= 1)) {
            throw new IllegalArgumentException("Experience progress must increase within the range from zero to one");
        }
    }
}
