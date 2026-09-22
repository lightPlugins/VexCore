package dev.vexsoft.core.paper.ui;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/** Renders configurable progress segments without owning gameplay state. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProgressBar {

    /** Renders bounded progress using independent segment symbols and colors. */
    public static Component render(
        final double progress,
        final int length,
        final String filledSymbol,
        final String emptySymbol,
        final TextColor filledColor,
        final TextColor emptyColor
    ) {
        if (length < 1 || length > 100 || !Double.isFinite(progress)) {
            throw new IllegalArgumentException("Invalid progress bar length or progress");
        }
        int filled = (int) Math.floor(Math.clamp(progress, 0, 1) * length);
        return Component.text(filledSymbol.repeat(filled), filledColor)
            .append(Component.text(emptySymbol.repeat(length - filled), emptyColor));
    }
}
