package dev.vexsoft.core.paper.service.interactiveui;

import dev.vexsoft.core.paper.interactiveui.InteractiveUiElement;
import dev.vexsoft.core.paper.interactiveui.InteractiveUiInput;
import dev.vexsoft.core.paper.interactiveui.InteractiveUiInput.Kind;
import dev.vexsoft.core.paper.interactiveui.InteractiveUiState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure pointer state machine with ordered button edges and capture across element boundaries. */
public final class InteractiveUiPointer {

    private InteractiveUiCursor cursor;
    private double x = 160;
    private double y = 90;
    private double pressedX;
    private double pressedY;
    private boolean pressed;
    private boolean dragged;
    private String hovered;
    private String captured;
    private long receivedInputs;

    public InteractiveUiPointer(float initialYaw, float initialPitch) {
        cursor = new InteractiveUiCursor(x, y, initialYaw, initialPitch);
    }

    public List<InteractiveUiInput> rotate(float nextYaw, float nextPitch, List<InteractiveUiElement> elements) {
        if (!Float.isFinite(nextYaw) || !Float.isFinite(nextPitch)) {
            return List.of();
        }
        return move(cursor.rotate(nextYaw, nextPitch), elements);
    }

    List<InteractiveUiInput> move(InteractiveUiCursor next, List<InteractiveUiElement> elements) {
        receivedInputs++;
        double previousX = x;
        double previousY = y;

        cursor = next;
        x = next.x();
        y = next.y();
        List<InteractiveUiInput> events = new ArrayList<>(refresh(elements));

        if (x != previousX || y != previousY) {
            events.add(event(Kind.MOVE, hovered));
            if (pressed) {
                dragged |= Math.hypot(x - pressedX, y - pressedY) > 3;
                if (captured != null) {
                    events.add(event(Kind.DRAG, captured));
                }
            }
        }
        return List.copyOf(events);
    }

    public List<InteractiveUiInput> press(List<InteractiveUiElement> elements) {
        receivedInputs++;
        List<InteractiveUiInput> events = new ArrayList<>(refresh(elements));

        if (!pressed) {
            pressed = true;
            dragged = false;
            pressedX = x;
            pressedY = y;
            captured = hovered;
            events.add(event(Kind.PRESS, captured));
        }
        return List.copyOf(events);
    }

    public List<InteractiveUiInput> release(List<InteractiveUiElement> elements) {
        receivedInputs++;
        List<InteractiveUiInput> events = new ArrayList<>(refresh(elements));

        if (pressed) {
            pressed = false;
            events.add(event(Kind.RELEASE, captured));
            if (captured != null && Objects.equals(captured, hovered) && !dragged) {
                events.add(event(Kind.CLICK, captured));
            }
            captured = null;
        }
        return List.copyOf(events);
    }

    public List<InteractiveUiInput> refresh(List<InteractiveUiElement> elements) {
        String next = null;
        boolean captureExists = captured == null;

        for (InteractiveUiElement element : elements) {
            if (element.interactive()) {
                captureExists |= element.id().equals(captured);
                if (element.bounds().contains(x, y)) {
                    next = element.id();
                }
            }
        }
        if (!captureExists) {
            captured = null;
        }
        if (Objects.equals(next, hovered)) {
            return List.of();
        }
        List<InteractiveUiInput> events = new ArrayList<>();

        if (hovered != null) {
            events.add(event(Kind.LEAVE, hovered));
        }
        hovered = next;
        if (hovered != null) {
            events.add(event(Kind.ENTER, hovered));
        }
        return List.copyOf(events);
    }

    public InteractiveUiState state() {
        return new InteractiveUiState(x, y, pressed, hovered, receivedInputs);
    }

    public void cancelCapture(String elementId) {
        if (Objects.equals(captured, elementId)) {
            captured = null;
        }
    }

    private InteractiveUiInput event(Kind kind, String target) {
        return new InteractiveUiInput(kind, target, x, y);
    }
}
