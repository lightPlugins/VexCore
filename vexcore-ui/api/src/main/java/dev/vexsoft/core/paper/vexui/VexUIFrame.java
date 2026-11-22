package dev.vexsoft.core.paper.vexui;

import dev.vexsoft.core.paper.vexui.action.VexUIAction;
import dev.vexsoft.core.paper.vexui.action.VexUIClickSound;
import dev.vexsoft.core.paper.vexui.element.VexUIElement;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;

/** Immutable render snapshot; action identifiers refer to this frame only. */
public record VexUIFrame(
    Component title,
    Component closeLabel,
    int width,
    VexUIElement content,
    Map<String, VexUIAction> actions,
    VexUIClickSound clickSound,
    boolean compactCloseButton,
    ShadowColor textShadow
) {

    public static final int API_VERSION = 3;
    public static final int PACK_FORMAT_VERSION = 3;
    private static final ShadowColor DARK_TEXT_SHADOW = ShadowColor.shadowColor(0xE0181412);

    /** Copies the action table and validates the logical canvas width. */
    public VexUIFrame {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(closeLabel, "closeLabel");
        Objects.requireNonNull(content, "content");
        actions = Map.copyOf(actions);
        if (width < 100 || width > 960) {
            throw new IllegalArgumentException("Frame width must be between 100 and 960 logical pixels");
        }
    }

    /** Retains the existing native and canvas text appearance when no shadow is requested. */
    public VexUIFrame(
        final Component title,
        final Component closeLabel,
        final int width,
        final VexUIElement content,
        final Map<String, VexUIAction> actions,
        final VexUIClickSound clickSound,
        final boolean compactCloseButton
    ) {
        this(title, closeLabel, width, content, actions, clickSound, compactCloseButton, null);
    }

    /** Retains the standard native Close action for existing callers. */
    public VexUIFrame(
        final Component title,
        final Component closeLabel,
        final int width,
        final VexUIElement content,
        final Map<String, VexUIAction> actions,
        final VexUIClickSound clickSound
    ) {
        this(title, closeLabel, width, content, actions, clickSound, false);
    }

    /** Creates a frame inheriting the plugin owner's click sound and standard native Close action. */
    public VexUIFrame(
        final Component title,
        final Component closeLabel,
        final int width,
        final VexUIElement content,
        final Map<String, VexUIAction> actions
    ) {
        this(title, closeLabel, width, content, actions, null);
    }

    /** Overrides feedback for this frame's buttons; individual buttons can override it again. */
    public VexUIFrame withClickSound(final VexUIClickSound sound) {
        return new VexUIFrame(title, closeLabel, width, content, actions, sound, compactCloseButton, textShadow);
    }

    /** Keeps the native cancellation action compact when the frame already supplies its own close control. */
    public VexUIFrame withCompactNativeCloseButton() {
        return new VexUIFrame(title, closeLabel, width, content, actions, clickSound, true, textShadow);
    }

    /** Gives text over illustrated backgrounds a dark one-pixel shadow. */
    public VexUIFrame withTextShadow() {
        return new VexUIFrame(title, closeLabel, width, content, actions, clickSound, compactCloseButton, DARK_TEXT_SHADOW);
    }

    /** Applies the frame shadow to native captions and hover hints while respecting explicit child styles. */
    public Component styleText(final Component text) {
        return textShadow == null ? text : text.shadowColor(textShadow);
    }
}
