package dev.vexsoft.core.paper.screenui;

import java.util.List;
import net.kyori.adventure.text.Component;

/** A prewrapped visual page. Its line positions stay fixed while text is revealed. */
public record DialoguePage(List<Component> lines, int codePoints, String plainText) {

    /** Copies the prepared lines to preserve the page contents. */
    public DialoguePage {
        lines = List.copyOf(lines);
    }
}
