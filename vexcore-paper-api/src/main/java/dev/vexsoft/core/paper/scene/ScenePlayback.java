package dev.vexsoft.core.paper.scene;

import java.util.concurrent.CompletionStage;

/** Controls an individual scene and exposes its outcome after presentation has been released. */
public interface ScenePlayback extends AutoCloseable {

    enum Result {
        COMPLETED,
        INTERRUPTED
    }

    /** Resolves when this scene has finished or has been interrupted. */
    CompletionStage<Result> completion();

    /** Interrupts this scene on the viewer's entity thread. */
    @Override
    void close();
}
