package dev.vexsoft.core.paper.vexui.element;

import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Native dialog hit target, made of transparent space glyphs on complete nine-pixel text lines. */
public record VexUIHotspot(VexUISize size, String action, Component tooltip) implements VexUIElement {
    public VexUIHotspot {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(tooltip, "tooltip");
        if (action.isBlank() || size.width() < 1 || size.height() < 9 || size.height() % 9 != 0) {
            throw new IllegalArgumentException("Invalid hotspot action or dimensions");
        }
    }
}
