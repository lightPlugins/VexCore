package dev.vexsoft.core.paper.screenui.progression;

import java.util.Objects;
import net.kyori.adventure.text.Component;

/** The localized text and levels of one committed progression change. */
public record ProgressionNotice(Component title, Component subtitle, Component source, int previousLevel, int level) {

    public ProgressionNotice {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(subtitle, "subtitle");
        Objects.requireNonNull(source, "source");
        if (previousLevel < 0 || level <= previousLevel) {
            throw new IllegalArgumentException("Progression must increase the level");
        }
    }
}
