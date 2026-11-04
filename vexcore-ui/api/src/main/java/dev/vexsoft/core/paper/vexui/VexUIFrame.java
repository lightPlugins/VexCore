package dev.vexsoft.core.paper.vexui;

import dev.vexsoft.core.paper.vexui.action.VexUIAction;
import dev.vexsoft.core.paper.vexui.action.VexUIClickSound;
import dev.vexsoft.core.paper.vexui.element.VexUIElement;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Immutable render snapshot; action identifiers refer to this frame only. */
public record VexUIFrame(
    Component title,
    Component closeLabel,
    int width,
    VexUIElement content,
    Map<String, VexUIAction> actions,
    VexUIClickSound clickSound,
    boolean compactCloseButton
) {

    public static final int API_VERSION = 2;
    public static final int PACK_FORMAT_VERSION = 3;

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
        return new VexUIFrame(title, closeLabel, width, content, actions, sound, compactCloseButton);
    }

    /** Keeps the native cancellation action compact when the frame already supplies its own close control. */
    public VexUIFrame withCompactNativeCloseButton() {
        return new VexUIFrame(title, closeLabel, width, content, actions, clickSound, true);
    }
}
