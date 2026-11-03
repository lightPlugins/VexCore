package dev.vexsoft.core.paper.vexui.template;

import dev.vexsoft.core.paper.vexui.VexUIObservable;

/** Viewer-bound domain adapter for the paginated travel workspace. */
public interface VexUITravelProvider extends VexUIObservable<VexUITravelSnapshot>, VexUITemplateText, AutoCloseable {

    /** Returns category geometry for categories, or destination geometry for the selected category ID. */
    VexUITemplateLayout layout(String section);

    /** Revalidates and starts one authoritative journey; the template never performs teleportation itself. */
    void travel(String destination);

    /** Cancels a currently cancellable authoritative journey. */
    void cancel();

    /** Releases presentation subscriptions without cancelling a journey when the menu closes. */
    @Override
    void close();
}
