package dev.vexsoft.core.paper.scene;

import java.util.List;
import net.kyori.adventure.text.Component;

/** A caption that replaces the previous text at a point on the scene timeline. */
public record SceneCaption(long startMillis, long revealMillis, List<Component> lines) {

    public SceneCaption {
        lines = List.copyOf(lines);
        if (startMillis < 0 || revealMillis < 0 || lines.size() > 8) {
            throw new IllegalArgumentException("Invalid scene caption time or line count");
        }
    }
}
