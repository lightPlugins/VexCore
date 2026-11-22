package dev.vexsoft.core.paper.vexui.template;

import dev.vexsoft.core.paper.vexui.element.VexUIEntrance;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.object.ObjectContents;

/** Draws a full-viewport dialog panorama using the bitmap and GUI atlas assets of a scene. */
public final class VexUIPanorama {

    private static final Key VANILLA_FONT = Key.key("minecraft:default");
    private static final Key GUI_ATLAS = Key.key("minecraft:gui");
    private static final TextColor PANORAMA_UNDERLAY = TextColor.color(0xFFFFFC);
    // Each vanilla space is four pixels wide. Four row rewinds in the loaded panorama font cancel this advance.
    private static final Component PANORAMA_LEAD = Component.text(" ".repeat(1008)).font(VANILLA_FONT);
    private static final String PANORAMA_ORIGIN = "\uE10D".repeat(4);
    private static final Component TITLE_PADDING = Component.text(" ".repeat(512)).font(VANILLA_FONT);

    private VexUIPanorama() {
    }

    /** Builds the native title, crossfading from the previous theme when supplied. */
    public static Component title(
        final Key theme, final Key previousTheme, final long started, final boolean hasPanoramaSprites
    ) {
        var title = Component.text().append(PANORAMA_LEAD);
        boolean crossfade = previousTheme != null;
        if (crossfade && !hasPanoramaSprites) {
            // The shader centers the outgoing tiles; their native position is outside the viewport.
            title.append(panorama(backgroundFont(previousTheme), VexUIEntrance.exitColor(started, 0)));
        }
        var font = backgroundFont(theme);
        // Only this font can rewind to the visible origin. Missing-glyph boxes stay beyond the right edge.
        title.append(Component.text(PANORAMA_ORIGIN).font(font));
        var currentTint = crossfade ? VexUIEntrance.color(started, 0) : VexUIEntrance.SETTLED;
        if (hasPanoramaSprites) {
            // The bitmap also works with an older pack. Put native 8px objects outside the viewport
            // so a client using a different GUI text shader sees only the full-size bitmap.
            title.append(panorama(font, PANORAMA_UNDERLAY)).append(PANORAMA_LEAD);
            if (crossfade) {
                title.append(panoramaSprites(previousTheme, VexUIEntrance.exitColor(started, 0)));
            }
            return title.append(panoramaSprites(theme, currentTint)).build();
        }
        title.append(panorama(font, currentTint));
        // The native header expands from screen x=0. Its out-of-bounds warning button is then relocated
        // by DialogScreen to the top-right corner, retaining its actual clickable help screen.
        return title.append(TITLE_PADDING).build();
    }

    private static Key backgroundFont(final Key asset) {
        return Key.key(asset.namespace(), asset.value() + "/background");
    }

    private static Component panoramaSprites(final Key asset, final TextColor tint) {
        var sprites = Component.text();
        for (int row = 0; row < 3; row++) {
            // The low nibbles carry the same animation clock as the bitmap sprites.
            // The upper nibbles identify this as a GUI atlas draw and select its vertical position.
            var rowTint = TextColor.color(0xC0 | (tint.red() & 15), (0xB0 + row * 16) | (tint.green() & 15),
                0xF0 | (tint.blue() & 15));
            var sprite = Key.key(asset.namespace(), asset.value() + "/panorama_" + row);
            sprites.append(Component.object(ObjectContents.sprite(GUI_ATLAS, sprite)).fallback(Component.empty())
                .color(rowTint).shadowColor(ShadowColor.none()));
        }
        return sprites.build();
    }

    private static Component panorama(final Key font, final TextColor tint) {
        // The tiles and their negative advances must become available together on a backend switch.
        var trimGlyphGap = Component.text("\uE10C").font(font);
        var rewindRow = Component.text("\uE10D").font(font);
        var rows = Component.text();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 4; column++) {
                rows.append(Component.text(Character.toString(0xE100 + row * 4 + column))
                    .font(font).color(tint).shadowColor(ShadowColor.none()));
                rows.append(trimGlyphGap);
            }
            rows.append(rewindRow);
        }
        return rows.build();
    }
}
