package dev.vexsoft.core.paper.interactiveui;

import java.util.function.Consumer;

/** Player-owned interactive canvas. Mutations and close must run on the viewer's entity scheduler. */
public interface InteractiveUi extends AutoCloseable {

    /** Creates or replaces one element; at most 128 elements are allowed. */
    void put(InteractiveUiElement element);

    /** Removes an element and cancels its pointer capture when necessary. */
    void remove(String id);

    /** Replaces the input callback, executed on the viewer's entity scheduler. */
    void onInput(Consumer<InteractiveUiInput> callback);

    /** Returns the latest immutable pointer snapshot; safe to read from any thread. */
    InteractiveUiState state();

    /** Returns whether this handle has been retired. */
    boolean isClosed();

    /** Restores the viewer's presentation and removes this session's runtime resources. */
    @Override
    void close();
}
