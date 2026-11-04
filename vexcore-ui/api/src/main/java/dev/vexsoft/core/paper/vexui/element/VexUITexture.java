package dev.vexsoft.core.paper.vexui.element;

import java.util.Objects;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

/** External pack glyph with explicit metrics and baseline variants named fontPrefix/y_0 through y_8. */
public record VexUITexture(Key fontPrefix, int codePoint, int width, int height, int advance,
                           Component tooltip) implements VexUIElement {

    /** Validates a custom glyph's dimensions and measured advance. */
    public VexUITexture {
        Objects.requireNonNull(fontPrefix, "fontPrefix");
        Objects.requireNonNull(tooltip, "tooltip");
        if (!Character.isValidCodePoint(codePoint) || width < 1 || height < 1 || width > 256 || height > 256
            || advance < 1 || advance > 512) {
            throw new IllegalArgumentException("Invalid custom VexUI texture metrics");
        }
    }

    @Override
    public VexUISize size() {
        return VexUISize.fixed(Math.max(width, advance), height);
    }
}
