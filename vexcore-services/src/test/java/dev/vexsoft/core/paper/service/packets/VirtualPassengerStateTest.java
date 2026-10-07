package dev.vexsoft.core.paper.service.packets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Exercises client replacement and packet ordering independently for each viewer. */
public final class VirtualPassengerStateTest {

    @Test
    void replacementPivotRestoresMountEvenWhenServerPivotDidNotChange() {
        VirtualPassengerState state = mounted();
        assertEquals(List.of(90), state.removed(42));
        assertNull(state.mount(42));
        assertFalse(state.isSpawned(90));
        assertFalse(state.add(42, 90));
        assertTrue(state.spawned(42).isEmpty());
        assertEquals(Map.of(42, List.of(10, 90)), state.spawned(90));
        assertEquals(Map.of(42, List.of(10, 90)), state.spawned(42));
    }

    @Test
    void waitsForLastPassengerWhenSpawnOrderIsReversed() {
        VirtualPassengerState state = new VirtualPassengerState();
        state.add(42, 90);
        state.recordNative(42, List.of(10));
        assertTrue(state.spawned(42).isEmpty());
        assertTrue(state.spawned(90).isEmpty());
        assertEquals(Map.of(42, List.of(10, 90)), state.spawned(10));
    }

    @Test
    void removedOverlayIsNotRestoredByLaterPivotPackets() {
        VirtualPassengerState state = mounted();
        assertTrue(state.remove(42, 90));
        assertEquals(List.of(10), state.mount(42));
        state.removed(90);
        state.removed(42);
        assertTrue(state.spawned(42).isEmpty());
        assertNull(state.mount(42));
    }

    @Test
    void nativeUpdatesContainingOverlayStillReplaceNativePassengers() {
        VirtualPassengerState state = mounted();
        state.spawned(11);
        state.recordNative(42, List.of(11, 90));
        assertEquals(List.of(11, 90), state.mount(42));
        state.remove(42, 90);
        assertEquals(List.of(11), state.mount(42));
    }

    @Test
    void worldResetForgetsOldNativePassengersAndEntities() {
        VirtualPassengerState state = mounted();
        state.resetEntities();
        assertNull(state.mount(42));
        assertTrue(state.spawned(42).isEmpty());
        assertEquals(Map.of(42, List.of(90)), state.spawned(90));
        state.recordNative(42, List.of(10));
        assertEquals(Map.of(42, List.of(10, 90)), state.spawned(10));
    }

    @Test
    void oneViewerUntrackingDoesNotChangeAnotherViewer() {
        VirtualPassengerState moving = mounted();
        VirtualPassengerState stationary = mounted();
        moving.removed(42);
        assertNull(moving.mount(42));
        assertEquals(List.of(10, 90), stationary.mount(42));
        assertTrue(moving.spawned(42).isEmpty());
        assertEquals(Map.of(42, List.of(10, 90)), moving.spawned(90));
    }

    @Test
    void mountsToPivotThatHasNoNativePassengers() {
        VirtualPassengerState state = new VirtualPassengerState();
        state.add(42, 90);
        assertTrue(state.spawned(42).isEmpty());
        assertEquals(Map.of(42, List.of(90)), state.spawned(90));
    }

    @Test
    void queuedOldMountCannotCacheRetiredHologramAsNativePassenger() {
        VirtualPassengerState state = mounted();
        state.remove(42, 90);
        // Registration is removed on the server before the queued mount reaches the Netty interceptor.
        state.recordNative(42, List.of(10, 90));
        assertEquals(List.of(10), state.mount(42));
        state.removed(90);
        state.add(42, 91);
        assertEquals(Map.of(42, List.of(10, 91)), state.spawned(91));
    }

    private static VirtualPassengerState mounted() {
        VirtualPassengerState state = new VirtualPassengerState();
        state.spawned(42);
        state.spawned(10);
        state.spawned(90);
        state.recordNative(42, List.of(10));
        assertTrue(state.add(42, 90));
        assertEquals(List.of(10, 90), state.mount(42));
        return state;
    }
}
