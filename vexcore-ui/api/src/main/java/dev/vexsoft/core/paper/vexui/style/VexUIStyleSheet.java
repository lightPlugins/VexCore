package dev.vexsoft.core.paper.vexui.style;

import dev.vexsoft.core.paper.vexui.component.VexUIPagination;
import java.util.Objects;

/** Plugin-wide component skins and logical default dimensions, configured exclusively through Java. */
public record VexUIStyleSheet(
    VexUITheme normal,
    VexUITheme primary,
    VexUITheme selected,
    VexUITheme danger,
    VexUITheme disabled,
    VexUITheme panel,
    int gap,
    int padding,
    int buttonHeight,
    int navigationWidth
) {

    /** Validates complete palettes and dimensions shared by the reusable components. */
    public VexUIStyleSheet {
        Objects.requireNonNull(normal, "normal");
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(selected, "selected");
        Objects.requireNonNull(danger, "danger");
        Objects.requireNonNull(disabled, "disabled");
        Objects.requireNonNull(panel, "panel");
        if (gap < 0 || gap > 100 || padding < 3 || padding > 100 || buttonHeight < 20 || buttonHeight > 100
            || navigationWidth < 25 || navigationWidth > 200) {
            throw new IllegalArgumentException("Invalid VexUI stylesheet dimensions");
        }
    }

    /** Returns the desaturated purple default stylesheet. */
    public static VexUIStyleSheet defaults() {
        return new VexUIStyleSheet(VexUITheme.BUTTON, VexUITheme.PRIMARY, VexUITheme.SELECTED,
            VexUITheme.DANGER, VexUITheme.DISABLED, VexUITheme.PANEL, 4, 5, 20,
            VexUIPagination.DEFAULT_BUTTON_WIDTH);
    }

    /** Resolves a semantic button role without changing explicitly supplied custom skins. */
    public VexUITheme theme(final VexUIButtonRole role) {
        return switch (role) {
            case NORMAL -> normal;
            case PRIMARY -> primary;
            case SELECTED -> selected;
            case DANGER -> danger;
        };
    }
}
