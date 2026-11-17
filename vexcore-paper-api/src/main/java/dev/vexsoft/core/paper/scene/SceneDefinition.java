package dev.vexsoft.core.paper.scene;

import dev.vexsoft.core.paper.screenui.layout.TextBlockLayout;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.sound.Sound;

/** A self-contained narration and caption timeline supplied by the scene's owner. */
public record SceneDefinition(
    long durationMillis, Sound narration, TextBlockLayout layout, List<SceneCaption> captions, SceneCinematic cinematic
) {

    public SceneDefinition(
        final long durationMillis, final Sound narration, final TextBlockLayout layout, final List<SceneCaption> captions
    ) {
        this(durationMillis, narration, layout, captions, null);
    }

    public SceneDefinition {
        Objects.requireNonNull(layout, "layout");
        captions = List.copyOf(captions);
        if (durationMillis <= 0 || layout.anchor() == null || layout.animation() != null || layout.transition() != null
            || captions.isEmpty()) {
            throw new IllegalArgumentException("Scenes require a duration, a fixed screen anchor and captions");
        }
        if (cinematic != null && cinematic.shots().getLast().startMillis() >= durationMillis) {
            throw new IllegalArgumentException("The final cinematic shot must start before the scene ends");
        }
        long previous = -1;
        for (SceneCaption caption : captions) {
            if (caption.startMillis() <= previous || caption.startMillis() >= durationMillis) {
                throw new IllegalArgumentException("Scene captions must be ordered within the scene duration");
            }
            previous = caption.startMillis();
        }
    }
}
