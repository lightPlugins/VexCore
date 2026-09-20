package dev.vexsoft.core.paper.service.interactiveui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.paper.packets.interactiveui.InteractiveUiPacketInput;
import dev.vexsoft.core.paper.packets.interactiveui.InteractiveUiPacketInput.Kind;
import java.util.List;
import org.junit.jupiter.api.Test;

final class InteractiveUiInputBufferTest {

    @Test
    void startupIgnoresInFlightInputUntilCameraResetWithoutLosingTheNextRotation() {
        InteractiveUiInputBuffer buffer = new InteractiveUiInputBuffer(true);

        buffer.offer(input(Kind.ROTATION, 150, -1));
        buffer.offer(input(Kind.PRESS, 0, 7));
        buffer.offer(input(Kind.ROTATION, 0, -1));
        buffer.offer(input(Kind.ROTATION, 2, -1));
        InteractiveUiInputBuffer.Batch batch = buffer.drain();

        assertEquals(List.of(input(Kind.ROTATION, 2, -1)), packets(batch));
        assertEquals(7, batch.acknowledgement());
    }

    @Test
    void closedBufferDrainsFinalAcknowledgementAndRejectsLaterPackets() {
        InteractiveUiInputBuffer buffer = new InteractiveUiInputBuffer();

        assertTrue(buffer.offer(input(Kind.PRESS, 0, 20)));
        assertEquals(20, buffer.close().acknowledgement());
        assertFalse(buffer.offer(input(Kind.RELEASE, 0, 21)));
        assertEquals(-1, buffer.drain().acknowledgement());
    }

    @Test
    void unpressedRotationsCoalesceWhileButtonEdgesAndDragExcursionsRemainOrdered() {
        InteractiveUiInputBuffer buffer = new InteractiveUiInputBuffer();
        InteractiveUiPacketInput beforePress = input(Kind.ROTATION, 2, -1);
        InteractiveUiPacketInput press = input(Kind.PRESS, 0, 10);
        InteractiveUiPacketInput drag = input(Kind.ROTATION, 4, -1);
        InteractiveUiPacketInput release = input(Kind.RELEASE, 0, 11);
        InteractiveUiPacketInput afterRelease = input(Kind.ROTATION, 6, -1);

        buffer.offer(input(Kind.ROTATION, 1, -1));
        buffer.offer(beforePress);
        buffer.offer(press);
        buffer.offer(input(Kind.ROTATION, 3, -1));
        buffer.offer(drag);
        buffer.offer(release);
        buffer.offer(input(Kind.ROTATION, 5, -1));
        buffer.offer(afterRelease);

        InteractiveUiInputBuffer.Batch batch = buffer.drain();

        assertEquals(List.of(beforePress, press, input(Kind.ROTATION, 3, -1), drag, release, afterRelease),
            packets(batch));
        assertEquals(11, batch.acknowledgement());
        assertFalse(batch.overflowed());
    }

    @Test
    void aRotationFloodUsesOneSlotAndPreservesTheLatestPosition() {
        InteractiveUiInputBuffer buffer = new InteractiveUiInputBuffer();

        for (int index = 0; index < 10_000; index++) {
            buffer.offer(input(Kind.ROTATION, index, -1));
        }
        InteractiveUiInputBuffer.Batch batch = buffer.drain();

        assertEquals(List.of(input(Kind.ROTATION, 9_999, -1)), packets(batch));
        assertFalse(batch.overflowed());
        assertEquals(-1, batch.acknowledgement());
    }

    @Test
    void releaseOverflowClosesTheBatchAndStillAcknowledgesEveryObservedBlockAction() {
        InteractiveUiInputBuffer buffer = new InteractiveUiInputBuffer();

        for (int sequence = 0; sequence < 256; sequence++) {
            buffer.offer(input(Kind.PRESS, 0, sequence));
        }
        buffer.offer(input(Kind.RELEASE, 0, 256));
        buffer.offer(input(Kind.BLOCK_ACTION, 0, 300));
        buffer.offer(input(Kind.ROTATION, 1, -1));
        InteractiveUiInputBuffer.Batch batch = buffer.drain();

        assertTrue(batch.overflowed());
        assertTrue(batch.inputs().isEmpty());
        assertEquals(300, batch.acknowledgement());

        buffer.offer(input(Kind.PRESS, 0, 301));
        InteractiveUiInputBuffer.Batch afterOverflow = buffer.drain();

        assertTrue(afterOverflow.overflowed());
        assertTrue(afterOverflow.inputs().isEmpty());
        assertEquals(301, afterOverflow.acknowledgement());
    }

    @Test
    void aFullBatchWithAReleaseInItsLastSlotRemainsDeliverable() {
        InteractiveUiInputBuffer buffer = new InteractiveUiInputBuffer();

        for (int sequence = 0; sequence < 255; sequence++) {
            buffer.offer(input(Kind.PRESS, 0, sequence));
        }
        InteractiveUiPacketInput release = input(Kind.RELEASE, 0, 255);

        buffer.offer(release);
        InteractiveUiInputBuffer.Batch batch = buffer.drain();

        assertFalse(batch.overflowed());
        assertEquals(256, batch.inputs().size());
        assertEquals(release, batch.inputs().getLast().packet());
        assertEquals(255, batch.acknowledgement());
    }

    @Test
    void replacingTheLastRotationOfAFullBatchDoesNotOverflow() {
        InteractiveUiInputBuffer buffer = new InteractiveUiInputBuffer();

        for (int sequence = 0; sequence < 255; sequence++) {
            buffer.offer(input(Kind.BLOCK_ACTION, 0, sequence));
        }
        buffer.offer(input(Kind.ROTATION, 1, -1));
        buffer.offer(input(Kind.ROTATION, 2, -1));
        InteractiveUiInputBuffer.Batch batch = buffer.drain();

        assertFalse(batch.overflowed());
        assertEquals(256, batch.inputs().size());
        assertEquals(input(Kind.ROTATION, 2, -1), batch.inputs().getLast().packet());
    }

    @Test
    void drainResetsPendingInputAndAcknowledgementWithoutMutatingThePreviousBatch() {
        InteractiveUiInputBuffer buffer = new InteractiveUiInputBuffer();
        InteractiveUiPacketInput press = input(Kind.PRESS, 0, 20);

        buffer.offer(press);
        buffer.offer(input(Kind.BLOCK_ACTION, 0, 10));
        InteractiveUiInputBuffer.Batch first = buffer.drain();
        InteractiveUiInputBuffer.Batch empty = buffer.drain();

        assertEquals(20, first.acknowledgement());
        assertEquals(2, first.inputs().size());
        assertThrows(UnsupportedOperationException.class, () -> first.inputs().clear());
        assertTrue(empty.inputs().isEmpty());
        assertFalse(empty.overflowed());
        assertEquals(-1, empty.acknowledgement());

        buffer.offer(input(Kind.RELEASE, 0, 21));

        assertEquals(21, buffer.drain().acknowledgement());
        assertEquals(press, first.inputs().getFirst().packet());
    }

    @Test
    void cursorProjectionKeepsEdgeReversalEvenWhenRotationPacketsCoalesce() {
        InteractiveUiInputBuffer buffer = new InteractiveUiInputBuffer();

        buffer.offer(input(Kind.ROTATION, 100, -1));
        buffer.offer(input(Kind.ROTATION, 99, -1));
        InteractiveUiInputBuffer.Batch batch = buffer.drain();

        assertEquals(1, batch.inputs().size());
        assertEquals(316.99, batch.inputs().getFirst().cursor().x(), 0.001);
        assertEquals(batch.inputs().getFirst().cursor(), buffer.cursor().orElseThrow());
    }

    @Test
    void invalidRotationCannotPoisonImmediateCursorFeedback() {
        InteractiveUiInputBuffer buffer = new InteractiveUiInputBuffer();

        buffer.offer(input(Kind.ROTATION, Float.NaN, -1));
        buffer.offer(input(Kind.ROTATION, 1, -1));

        assertEquals(163, buffer.cursor().orElseThrow().x());
        assertEquals(1, buffer.drain().inputs().size());
        buffer.close();
        assertTrue(buffer.cursor().isEmpty());
    }

    private static List<InteractiveUiPacketInput> packets(InteractiveUiInputBuffer.Batch batch) {
        return batch.inputs().stream().map(InteractiveUiInputBuffer.Input::packet).toList();
    }

    private static InteractiveUiPacketInput input(Kind kind, float yaw, int sequence) {
        return new InteractiveUiPacketInput(kind, yaw, 0, sequence, 12, 64, 34);
    }
}
