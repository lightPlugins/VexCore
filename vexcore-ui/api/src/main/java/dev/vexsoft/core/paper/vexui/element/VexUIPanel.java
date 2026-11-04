package dev.vexsoft.core.paper.vexui.element;

import dev.vexsoft.core.paper.vexui.style.VexUITheme;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Frames one nested layout with a theme and pixel padding. */
public record VexUIPanel(VexUISize size, int padding, VexUITheme theme, VexUIElement content, Component tooltip)
    implements VexUIElement {

    /** Flat background panels may omit the inset; framed panels reserve room for their pixel borders. */
    public VexUIPanel {
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(tooltip, "tooltip");
        boolean flat = theme.background() == theme.outerBorder() && theme.background() == theme.lightBorder()
            && theme.background() == theme.darkBorder();
        if (padding < 0 || padding > 100 || !flat && padding < 3) {
            throw new IllegalArgumentException("Framed panels require at least three pixels of padding");
        }
    }

    /** Creates a panel without a hover description. */
    public VexUIPanel(final VexUISize size, final int padding, final VexUITheme theme, final VexUIElement content) {
        this(size, padding, theme, content, Component.empty());
    }

    /** Applies a native tooltip to the complete panel while nested buttons retain their own actions. */
    public VexUIPanel withTooltip(final Component description) {
        return new VexUIPanel(size, padding, theme, content, description);
    }
}
