package dev.vexsoft.core.paper.vexui;

/** Viewer-owned state that builds immutable render snapshots. Create a fresh view for every viewer. */
@FunctionalInterface
public interface VexUIView {

    /** Builds the current localized layout and its allowed actions on the viewer's entity scheduler. */
    VexUIFrame render(VexUISession session);

    /** Handles the native dialog's close button or Escape. A view may dismiss its open popup first. */
    default void closeRequested(VexUISession session) {
        session.close();
    }

    /** Releases resources on navigation or owner shutdown; this hook must not access Bukkit world state. */
    default void closed() {
    }
}
