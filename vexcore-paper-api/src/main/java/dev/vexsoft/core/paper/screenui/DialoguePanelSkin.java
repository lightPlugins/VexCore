package dev.vexsoft.core.paper.screenui;

import java.util.List;
import java.util.Objects;

/** Plugin-owned decorations positioned relative to the standard 280 by 96 dialogue panel. */
public record DialoguePanelSkin(List<Part> parts, int hintInset) {
    /** Copies the decorations and validates their count and the hint inset. */
    public DialoguePanelSkin {
        parts = List.copyOf(parts);
        if (parts.isEmpty() || parts.size() > 8 || hintInset < 0 || hintInset > 32) {
            throw new IllegalArgumentException("Invalid dialogue skin");
        }
    }

    /** One static texture inside the dialogue frame. */
    public record Part(UiTexture texture, int x, int y) {
        /** Validates that the texture fits inside the dialogue panel. */
        public Part {
            Objects.requireNonNull(texture, "texture");
            if (x < 0 || y < 0 || x + texture.width() > 280 || y + texture.height() > 96) {
                throw new IllegalArgumentException("Dialogue decoration exceeds panel bounds");
            }
        }
    }
}
