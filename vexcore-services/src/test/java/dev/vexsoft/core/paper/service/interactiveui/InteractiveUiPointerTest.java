package dev.vexsoft.core.paper.service.interactiveui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.paper.interactiveui.InteractiveUiElement;
import dev.vexsoft.core.paper.interactiveui.InteractiveUiInput;
import dev.vexsoft.core.paper.interactiveui.UiBounds;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

final class InteractiveUiPointerTest {

    private static final InteractiveUiElement CANVAS = element("canvas", 0, 0, 320, 180, true);

    @Test
    void crossingTheYawSeamMovesByTheShortestAngleInBothDirections() {
        InteractiveUiPointer clockwise = new InteractiveUiPointer(179, 0);
        InteractiveUiPointer anticlockwise = new InteractiveUiPointer(-179, 0);

        clockwise.rotate(-179, 0, List.of());
        anticlockwise.rotate(179, 0, List.of());

        assertEquals(166, clockwise.state().x());
        assertEquals(154, anticlockwise.state().x());
        assertEquals(90, clockwise.state().y());
        assertEquals(90, anticlockwise.state().y());
    }

    @Test
    void aClampedCursorCanImmediatelyReverseDirection() {
        InteractiveUiPointer pointer = new InteractiveUiPointer(0, 0);

        pointer.rotate(100, 100, List.of());

        assertEquals(319.99, pointer.state().x());
        assertEquals(179.99, pointer.state().y());

        pointer.rotate(99, 99, List.of());

        assertEquals(316.99, pointer.state().x(), 0.0001);
        assertEquals(176.99, pointer.state().y(), 0.0001);
    }

    @Test
    void nonFiniteRotationDoesNotPoisonTheNextMovement() {
        InteractiveUiPointer pointer = new InteractiveUiPointer(0, 0);

        assertTrue(pointer.rotate(Float.NaN, 0, List.of()).isEmpty());
        assertTrue(pointer.rotate(0, Float.POSITIVE_INFINITY, List.of()).isEmpty());
        assertEquals(0, pointer.state().receivedInputs());

        pointer.rotate(1, 2, List.of());

        assertEquals(163, pointer.state().x());
        assertEquals(96, pointer.state().y());
        assertEquals(1, pointer.state().receivedInputs());
    }

    @Test
    void duplicateButtonPacketsDoNotCreateExtraPressesOrClicks() {
        InteractiveUiPointer pointer = new InteractiveUiPointer(0, 0);
        List<InteractiveUiElement> elements = List.of(CANVAS);

        assertEvents(pointer.press(elements), "ENTER:canvas", "PRESS:canvas");
        assertTrue(pointer.press(elements).isEmpty());
        assertTrue(pointer.rotate(0, 0, elements).isEmpty());
        assertEvents(pointer.release(elements), "RELEASE:canvas", "CLICK:canvas");
        assertTrue(pointer.release(elements).isEmpty());
        assertFalse(pointer.state().pressed());
        assertEquals(5, pointer.state().receivedInputs());
    }

    @Test
    void draggingAcrossElementsKeepsThePressedTargetCaptured() {
        InteractiveUiPointer pointer = new InteractiveUiPointer(0, 0);
        List<InteractiveUiElement> elements = List.of(
            element("left", 0, 0, 170, 180, true),
            element("right", 170, 0, 150, 180, true));

        pointer.press(elements);

        assertEvents(pointer.rotate(10, 0, elements),
            "LEAVE:left", "ENTER:right", "MOVE:right", "DRAG:left");
        assertEquals("right", pointer.state().hoveredId());
        assertTrue(pointer.state().pressed());
        assertEvents(pointer.release(elements), "RELEASE:left");
        assertFalse(pointer.state().pressed());
    }

    @Test
    void returningToThePressedElementAfterADragDoesNotClick() {
        InteractiveUiPointer pointer = new InteractiveUiPointer(0, 0);
        List<InteractiveUiElement> elements = List.of(CANVAS);

        pointer.press(elements);
        pointer.rotate(2, 0, elements);
        pointer.rotate(0, 0, elements);

        assertEvents(pointer.release(elements), "RELEASE:canvas");
    }

    @Test
    void smallPointerJitterStillAllowsAClick() {
        InteractiveUiPointer pointer = new InteractiveUiPointer(0, 0);
        List<InteractiveUiElement> elements = List.of(CANVAS);

        pointer.press(elements);
        pointer.rotate(0.5f, 0.5f, elements);

        assertEvents(pointer.release(elements), "RELEASE:canvas", "CLICK:canvas");
    }

    @Test
    void removingTheCapturedElementDoesNotTransferTheDragToItsReplacement() {
        InteractiveUiPointer pointer = new InteractiveUiPointer(0, 0);
        List<InteractiveUiElement> original = List.of(CANVAS);
        List<InteractiveUiElement> replacement = List.of(element("replacement", 0, 0, 320, 180, true));

        pointer.press(original);

        assertEvents(pointer.refresh(replacement), "LEAVE:canvas", "ENTER:replacement");
        assertEvents(pointer.rotate(2, 0, replacement), "MOVE:replacement");
        assertEvents(pointer.release(replacement), "RELEASE:null");
        assertEvents(pointer.press(replacement), "PRESS:replacement");
        assertEvents(pointer.release(replacement), "RELEASE:replacement", "CLICK:replacement");
    }

    @Test
    void disablingTheCapturedElementCancelsItsCapture() {
        InteractiveUiPointer pointer = new InteractiveUiPointer(0, 0);

        pointer.press(List.of(CANVAS));

        assertEvents(pointer.release(List.of(element("canvas", 0, 0, 320, 180, false))),
            "LEAVE:canvas", "RELEASE:null");
        assertNull(pointer.state().hoveredId());
    }

    @Test
    void anEmptySpacePressCannotStartDraggingAnElementEnteredLater() {
        InteractiveUiPointer pointer = new InteractiveUiPointer(0, 0);
        List<InteractiveUiElement> elements = List.of(element("right", 170, 0, 150, 180, true));

        assertEvents(pointer.press(elements), "PRESS:null");
        assertEvents(pointer.rotate(10, 0, elements), "ENTER:right", "MOVE:right");
        assertEvents(pointer.release(elements), "RELEASE:null");
    }

    @Test
    void theLastInteractiveElementWinsWhileDecorationsRemainTransparentToInput() {
        InteractiveUiPointer pointer = new InteractiveUiPointer(0, 0);
        List<InteractiveUiElement> elements = List.of(CANVAS,
            element("button", 150, 80, 20, 20, true),
            element("label", 150, 80, 20, 20, false));

        assertEvents(pointer.press(elements), "ENTER:button", "PRESS:button");
        assertEvents(pointer.rotate(4, 0, elements),
            "LEAVE:button", "ENTER:canvas", "MOVE:canvas", "DRAG:button");
    }

    private static InteractiveUiElement element(String id, double x, double y, double width, double height,
                                                boolean interactive) {
        return new InteractiveUiElement(id, new UiBounds(x, y, width, height), Component.empty(), 0, interactive);
    }

    private static void assertEvents(List<InteractiveUiInput> inputs, String... expected) {
        assertEquals(List.of(expected), inputs.stream().map(input -> input.kind() + ":" + input.targetId()).toList());
        for (InteractiveUiInput input : inputs) {
            assertTrue(input.x() >= 0 && input.x() < 320);
            assertTrue(input.y() >= 0 && input.y() < 180);
        }
    }
}
