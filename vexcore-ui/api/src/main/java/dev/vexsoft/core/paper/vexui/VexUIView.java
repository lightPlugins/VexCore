package dev.vexsoft.core.paper.vexui;

/** Viewer-owned state that builds immutable render snapshots. Create a fresh view for every viewer. */
@FunctionalInterface
public interface VexUIView {

    /** Builds the current localized layout and its allowed actions on the viewer's entity scheduler. */
    VexUIFrame render(VexUISession session);

    /** Releases resources on navigation or owner shutdown; this hook must not access Bukkit world state. */
    default void closed() {
    }
}
