package dev.vexsoft.core.paper.vexui.component;

import dev.vexsoft.core.paper.vexui.element.VexUIButton;
import dev.vexsoft.core.paper.vexui.element.VexUIColumn;
import dev.vexsoft.core.paper.vexui.element.VexUIItemIcon;
import dev.vexsoft.core.paper.vexui.element.VexUIRow;
import dev.vexsoft.core.paper.vexui.element.VexUISize;
import dev.vexsoft.core.paper.vexui.element.VexUIText;
import dev.vexsoft.core.paper.vexui.style.VexUIButtonRole;
import dev.vexsoft.core.paper.vexui.style.VexUIStyleSheet;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Standard item tile with an icon, title, detail line and independent tooltip. */
public record VexUIProductCard(VexUIItemIcon icon, Component title, Component detail, Component tooltip) {

    /** Requires resolved immutable presentation without embedding shop or economy rules. */
    public VexUIProductCard {
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(tooltip, "tooltip");
    }

    /** Creates a themed tile; the supplied height must fit its icon and two text lines. */
    public VexUIButton button(
        final String action,
        final boolean selected,
        final boolean enabled,
        final int height,
        final VexUIStyleSheet style
    ) {
        return new VexUIButton(new VexUISize(-1, height, 0), new VexUIRow(style.gap(), icon,
            new VexUIColumn(VexUISize.flexible(), 0, List.of(new VexUIText(title), new VexUIText(detail)))),
            style.normal(), enabled, action, tooltip)
            .withRole(selected ? VexUIButtonRole.SELECTED : VexUIButtonRole.NORMAL);
    }
}
