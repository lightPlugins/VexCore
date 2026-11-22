package dev.vexsoft.core.paper.vexui.element;

import java.util.Objects;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/** External pack glyph with explicit metrics and baseline variants named fontPrefix/y_0 through y_8. */
public record VexUITexture(Key fontPrefix, int codePoint, int width, int height, int advance,
                           Component tooltip, TextColor tint, boolean outgoing) implements VexUIElement {

    /** Validates a custom glyph's dimensions and measured advance. */
    public VexUITexture {
        Objects.requireNonNull(fontPrefix, "fontPrefix");
        Objects.requireNonNull(tooltip, "tooltip");
        Objects.requireNonNull(tint, "tint");
        if (!Character.isValidCodePoint(codePoint) || width < 1 || height < 1 || width > 256 || height > 256
            || advance < 1 || advance > 512) {
            throw new IllegalArgumentException("Invalid custom VexUI texture metrics");
        }
    }

    /** Creates an unmodified full-color sprite. */
    public VexUITexture(final Key fontPrefix, final int codePoint, final int width, final int height,
                        final int advance, final Component tooltip) {
        this(fontPrefix, codePoint, width, height, advance, tooltip, NamedTextColor.WHITE, false);
    }

    /** Creates a tinted sprite at its native position. */
    public VexUITexture(final Key fontPrefix, final int codePoint, final int width, final int height,
                        final int advance, final Component tooltip, final TextColor tint) {
        this(fontPrefix, codePoint, width, height, advance, tooltip, tint, false);
    }

    /** Emits this decorative sprite outside the native viewport; the scene shader restores it for its exit. */
    public VexUITexture outgoing(final TextColor exitTint) {
        return new VexUITexture(fontPrefix, codePoint, width, height, advance, Component.empty(), exitTint, true);
    }

    @Override
    public VexUISize size() {
        return VexUISize.fixed(Math.max(width, advance), height);
    }
}
