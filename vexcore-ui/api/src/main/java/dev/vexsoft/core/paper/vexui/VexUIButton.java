package dev.vexsoft.core.paper.vexui;

import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Clickable, padded content; a disabled button has no executable action. */
public record VexUIButton(
    VexUISize size,
    VexUIElement content,
    VexUITheme theme,
    boolean enabled,
    String action,
    Component tooltip,
    VexUIClickSound clickSound,
    VexUIButtonRole role,
    VexUIAttention attention,
    int padding
) implements VexUIElement {

    /** Validates immutable content, a stable action identifier and tooltip. */
    public VexUIButton {
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(tooltip, "tooltip");
        Objects.requireNonNull(attention, "attention");
        if (padding < 0 || padding > 16) {
            throw new IllegalArgumentException("Button padding must be between 0 and 16 pixels");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("Buttons require a stable action identifier");
        }
    }

    /** Preserves the original four-pixel button inset for existing templates and callers. */
    public VexUIButton(final VexUISize size, final VexUIElement content, final VexUITheme theme,
                       final boolean enabled, final String action, final Component tooltip,
                       final VexUIClickSound clickSound, final VexUIButtonRole role, final VexUIAttention attention) {
        this(size, content, theme, enabled, action, tooltip, clickSound, role, attention, 4);
    }

    /** Allows dense clickable cost rows without changing the dimensions of their item sprites. */
    public VexUIButton withPadding(final int value) {
        return new VexUIButton(size, content, theme, enabled, action, tooltip, clickSound, role, attention, value);
    }

    /** Creates a button inheriting its frame or plugin owner's click sound. */
    public VexUIButton(
        final VexUISize size,
        final VexUIElement content,
        final VexUITheme theme,
        final boolean enabled,
        final String action,
        final Component tooltip
    ) {
        this(size, content, theme, enabled, action, tooltip, null, null, VexUIAttention.none());
    }

    /** Creates a custom-skinned button with optional sound inheritance. */
    public VexUIButton(
        final VexUISize size,
        final VexUIElement content,
        final VexUITheme theme,
        final boolean enabled,
        final String action,
        final Component tooltip,
        final VexUIClickSound clickSound
    ) {
        this(size, content, theme, enabled, action, tooltip, clickSound, null, VexUIAttention.none());
    }

    /** Creates a semantic button without a reward or new-content marker. */
    public VexUIButton(
        final VexUISize size,
        final VexUIElement content,
        final VexUITheme theme,
        final boolean enabled,
        final String action,
        final Component tooltip,
        final VexUIClickSound clickSound,
        final VexUIButtonRole role
    ) {
        this(size, content, theme, enabled, action, tooltip, clickSound, role, VexUIAttention.none());
    }

    /** Overrides feedback for this button; null restores inheritance. */
    public VexUIButton withClickSound(final VexUIClickSound sound) {
        return new VexUIButton(size, content, theme, enabled, action, tooltip, sound, role, attention, padding);
    }

    /** Uses a semantic plugin-theme role; null restores the explicitly supplied skin. */
    public VexUIButton withRole(final VexUIButtonRole value) {
        return new VexUIButton(size, content, theme, enabled, action, tooltip, clickSound, value, attention, padding);
    }

    /** Adds independent gold navigation emphasis; the caller supplies localized count/new labels. */
    public VexUIButton withAttention(final VexUIAttention value) {
        return new VexUIButton(size, content, theme, enabled, action, tooltip, clickSound, role, value, padding);
    }

    /** Replaces the layout dimensions while preserving style, actions, sound and attention. */
    public VexUIButton withSize(final VexUISize value) {
        return new VexUIButton(value, content, theme, enabled, action, tooltip, clickSound, role, attention, padding);
    }

    /** Replaces native hover details while preserving layout, state and feedback. */
    public VexUIButton withTooltip(final Component description) {
        return new VexUIButton(size, content, theme, enabled, action, description, clickSound, role, attention, padding);
    }

    /** Creates an enabled text button using the standard skin. */
    public static VexUIButton text(final Component label, final String action) {
        return new VexUIButton(VexUISize.content(), new VexUIText(label), VexUITheme.BUTTON,
            true, action, Component.empty()).withRole(VexUIButtonRole.NORMAL);
    }
}
