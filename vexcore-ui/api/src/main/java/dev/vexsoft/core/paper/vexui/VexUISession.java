package dev.vexsoft.core.paper.vexui;

import dev.vexsoft.core.paper.vexui.component.VexUISearch;
import dev.vexsoft.core.paper.vexui.state.VexUIObservable;
import dev.vexsoft.core.paper.vexui.style.VexUIStyleSheet;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Viewer-specific navigation and updates; operations are dispatched to the viewer's entity scheduler. */
public interface VexUISession extends AutoCloseable {

    /** Returns the session's unguessable identity. */
    UUID id();

    /** Returns the viewer; Bukkit mutations belong on the entity scheduler. */
    Player player();

    /** Returns this plugin owner's current component stylesheet. */
    VexUIStyleSheet styleSheet();

    /** Overrides this session's stylesheet; null restores the owning plugin's default. */
    void setStyleSheet(VexUIStyleSheet styleSheet);

    /** Reads and observes a snapshot during render; unused and hidden-view bindings retire automatically. */
    <T> T bind(VexUIObservable<T> observable);

    /** Captures this view activation for asynchronous work; obtain it on the viewer's entity scheduler. */
    VexUIScope scope();

    /** Wraps complete styled text using the same glyph measurements as the renderer. */
    List<Component> wrap(Component text, int width);

    /** Enables or disables developer overlays for this session and requests a refresh. */
    void setDebugOptions(VexUIDebugOptions options);

    /** Coalesces a render request without closing the current dialog. */
    void refresh();

    /** Applies a state mutation on the viewer's scheduler and refreshes if this session is still active. */
    void update(Runnable mutation);

    /** Opens a child view while retaining the current view for Back navigation. */
    void navigate(VexUIView view);

    /** Restores the previous view, or closes this session when no previous view exists. */
    void back();

    /** Shows a localized native search form and returns the submitted text on the viewer's scheduler. */
    void search(VexUISearch search);

    /** Returns whether this session has retired. */
    boolean isClosed();

    /** Closes the view stack, dialog and associated actions. */
    @Override
    void close();
}
