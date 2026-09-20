package dev.vexsoft.core.paper.service.interactiveui;

import dev.vexsoft.core.paper.packets.interactiveui.InteractiveUiPacketInput;
import dev.vexsoft.core.paper.packets.interactiveui.InteractiveUiPacketInput.Kind;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;

/** Projects network input immediately and queues entity-thread events, preserving button edges and drag excursions. */
public final class InteractiveUiInputBuffer {

    private final ArrayDeque<Input> inputs = new ArrayDeque<>();
    private InteractiveUiCursor cursor = new InteractiveUiCursor(160, 90, 0, 0);
    private boolean overflowed;
    private boolean closed;
    private boolean waitingForCameraReset;
    private boolean pressed;
    private int acknowledgement = -1;

    public InteractiveUiInputBuffer() {
        this(false);
    }

    public InteractiveUiInputBuffer(boolean waitingForCameraReset) {
        this.waitingForCameraReset = waitingForCameraReset;
    }

    public synchronized boolean offer(InteractiveUiPacketInput input) {
        if (closed) {
            return false;
        }
        acknowledgement = Math.max(acknowledgement, input.sequence());
        if (waitingForCameraReset && input.kind() != Kind.EXIT) {
            if (input.kind() == Kind.ROTATION && input.yaw() == 0 && input.pitch() == 0) {
                // The reset response is a baseline, not pointer movement; never let coalescing erase it.
                waitingForCameraReset = false;
            }
            return true;
        }
        if (overflowed) {
            return true;
        }
        if (input.kind() == Kind.ROTATION) {
            if (!Float.isFinite(input.yaw()) || !Float.isFinite(input.pitch())) {
                return true;
            }
            // Project every sample before coalescing: discarding an excursion at an edge changes the final position.
            cursor = cursor.rotate(input.yaw(), input.pitch());
        }
        if (!pressed && input.kind() == Kind.ROTATION && inputs.peekLast() != null
            && inputs.peekLast().packet().kind() == Kind.ROTATION) {
            inputs.removeLast();
        }
        if (inputs.size() >= 256) {
            overflowed = true;
            inputs.clear();
            return true;
        }
        inputs.addLast(new Input(input, cursor));
        if (input.kind() == Kind.PRESS) {
            pressed = true;
        } else if (input.kind() == Kind.RELEASE) {
            pressed = false;
        }
        return true;
    }

    synchronized Optional<InteractiveUiCursor> cursor() {
        return closed || overflowed ? Optional.empty() : Optional.of(cursor);
    }

    public synchronized Batch drain() {
        Batch batch = new Batch(List.copyOf(inputs), overflowed, acknowledgement);

        inputs.clear();
        acknowledgement = -1;
        return batch;
    }

    public synchronized Batch close() {
        closed = true;
        return drain();
    }

    public record Input(InteractiveUiPacketInput packet, InteractiveUiCursor cursor) {
    }

    public record Batch(List<Input> inputs, boolean overflowed, int acknowledgement) {
    }
}
