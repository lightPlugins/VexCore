package dev.vexsoft.core.paper.screenui;

import java.util.Objects;
import net.kyori.adventure.key.Key;

/**
 * Pack contract: fonts {@code <fontPrefix>/y_0} through {@code /y_240} contain the glyph at the
 * declared visible size. glyphAdvance and glyphOffsetX describe transparent canvas padding. The
 * five-argument constructor uses an unpadded glyph with advance width + 1. No runtime file download
 * occurs.
 */
public record UiTexture(Key fontPrefix, int codePoint, int width, int height, int glyphAdvance, int glyphOffsetX) {

    public static final UiTexture PANEL = new UiTexture(Key.key("vexcore:ui/panel"), 0xE100, 280, 96, 397, -116);

    /** Rounded, translucent, tintable card. Supported heights: 20, 48, 64, 80, 96, 128. */
    public static UiTexture card(int height) {
        if (height != 20 && height != 48 && height != 64 && height != 80 && height != 96 && height != 128) {
            throw new IllegalArgumentException("Unsupported card height");
        }

        int width = height == 20 ? 160 : 240;

        return new UiTexture(Key.key("vexcore:ui/card_" + height), 0xE100, width, height, width + 9, -8);
    }

    /** Creates the layout using the supplied options and defaults for omitted settings. */
    public UiTexture(Key fontPrefix, int codePoint, int width, int height) {
        this(fontPrefix, codePoint, width, height, width + 1, 0);
    }

    /** Normalizes defaults and validates the configured layout values. */
    public UiTexture {
        Objects.requireNonNull(fontPrefix, "fontPrefix");

        if (!Character.isValidCodePoint(codePoint) || codePoint >= 0xD800 && codePoint <= 0xDFFF || width < 1
            || width > 512 || height < 1 || height > 240 || glyphAdvance < 1 || glyphAdvance > 1024
            || Math.abs((long) glyphOffsetX) > 512) {
            throw new IllegalArgumentException("Invalid texture metrics");
        }
    }
}
