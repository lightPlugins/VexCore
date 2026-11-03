package dev.vexsoft.core.paper.vexui.template;

import dev.vexsoft.core.paper.vexui.VexUIObservable;

/** Plugin-owned presentation adapter; gameplay and persistence remain outside the template. */
public interface VexUITemplateProvider<T> extends VexUIObservable<T>, VexUITemplateText, AutoCloseable {

    /** Supplies the grid's logical dimensions; unfilled pages retain this capacity. */
    VexUITemplateLayout layout();

    /** Releases resources when the root view closes, including hidden children. */
    @Override
    default void close() {
    }
}
