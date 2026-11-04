package dev.vexsoft.core.paper.vexui.component;

import dev.vexsoft.core.paper.vexui.element.VexUIButton;
import dev.vexsoft.core.paper.vexui.element.VexUIRow;
import dev.vexsoft.core.paper.vexui.element.VexUISize;
import dev.vexsoft.core.paper.vexui.element.VexUISpacer;
import dev.vexsoft.core.paper.vexui.element.VexUIText;
import dev.vexsoft.core.paper.vexui.style.VexUITheme;
import java.util.List;
import net.kyori.adventure.text.Component;

/** Shared Previous / current page / Next presentation with consistent boundary disabling. */
public final class VexUIPagination {

    /** Default navigation button width in logical GUI pixels; callers may supply their own width. */
    public static final int DEFAULT_BUTTON_WIDTH = 58;

    private VexUIPagination() {
    }

    /** Builds compact, owner-themed controls for one independently named pager in a narrow column. */
    public static VexUIRow compact(
        final VexUIPagedGrid<?> page,
        final Component previous,
        final Component current,
        final Component next,
        final String prefix,
        final VexUITheme theme
    ) {
        return new VexUIRow(3,
            new VexUIButton(new VexUISize(24, 22, 0), new VexUIText(VexUISize.flexible(), previous, true), theme,
                page.page() > 0, prefix + ".previous", Component.empty()),
            new VexUIText(new VexUISize(-1, 22, 1), current, true),
            new VexUIButton(new VexUISize(24, 22, 0), new VexUIText(VexUISize.flexible(), next, true), theme,
                page.page() + 1 < page.pages(), prefix + ".next", Component.empty()));
    }

    /** Builds controls from localized labels and stable action identifiers. */
    public static VexUIRow controls(final VexUIPagedGrid<?> page, final Component previous,
                                   final Component current, final Component next) {
        return controls(page, previous, current, next, DEFAULT_BUTTON_WIDTH);
    }

    /** Builds centered navigation using the owning plugin's shared button width. */
    public static VexUIRow controls(final VexUIPagedGrid<?> page, final Component previous,
                                   final Component current, final Component next, final int buttonWidth) {
        if (buttonWidth < 25 || buttonWidth > 200) {
            throw new IllegalArgumentException("Invalid pagination button width");
        }
        var controls = new VexUIRow(new VexUISize(buttonWidth * 2 + 120, -1, 0), 5, List.of(
            new VexUIButton(new VexUISize(buttonWidth, -1, 0), new VexUIText(previous), VexUITheme.BUTTON,
                page.page() > 0, "page.previous", Component.empty()),
            new VexUIText(new VexUISize(110, 22, 0), current, true),
            new VexUIButton(new VexUISize(buttonWidth, -1, 0), new VexUIText(next), VexUITheme.BUTTON,
                page.page() + 1 < page.pages(), "page.next", Component.empty())
        ));
        return new VexUIRow(0, new VexUISpacer(VexUISize.flexible()), controls,
            new VexUISpacer(VexUISize.flexible()));
    }
}
