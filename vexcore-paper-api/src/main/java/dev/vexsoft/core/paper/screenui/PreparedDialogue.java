package dev.vexsoft.core.paper.screenui;

import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Immutable prepared content; contains no player, task or UI handle. */
public record PreparedDialogue(Component speaker, List<DialoguePage> pages, DialoguePanelLayout layout) {

    /** Validates the speaker and layout and copies the prepared pages. */
    public PreparedDialogue {
        Objects.requireNonNull(speaker, "speaker");
        Objects.requireNonNull(layout, "layout");
        pages = List.copyOf(pages);

        if (pages.isEmpty() || pages.size() > 128) {
            throw new IllegalArgumentException("Dialogue requires 1..128 pages");
        }
    }
}
