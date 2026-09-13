package dev.vexsoft.core.paper.service.mob;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class MobPresentationGateTest {

    @Test
    void waitsForScheduledTickAndRejectsOldTrackAfterRetracking() {
        MobPresentationGate gate = new MobPresentationGate();
        UUID viewer = UUID.randomUUID();
        List<Runnable> tasks = new ArrayList<>();
        int[] shown = {0};
        gate.defer(viewer, (ready, retired) -> tasks.add(ready), () -> true, () -> shown[0]++);
        assertTrue(gate.isPending(viewer));
        assertEquals(0, shown[0]);
        gate.cancel(viewer);
        gate.defer(viewer, (ready, retired) -> tasks.add(ready), () -> true, () -> shown[0]++);
        tasks.getFirst().run();
        assertTrue(gate.isPending(viewer));
        assertEquals(0, shown[0]);
        tasks.getLast().run();
        assertFalse(gate.isPending(viewer));
        assertEquals(1, shown[0]);
    }

    @Test
    void cancelledInvalidAndRetiredCallbacksCannotCreateGhostDisplays() {
        for (int scenario = 0; scenario < 3; scenario++) {
            MobPresentationGate gate = new MobPresentationGate();
            UUID viewer = UUID.randomUUID();
            List<Runnable> callbacks = new ArrayList<>();
            boolean valid = scenario != 1;
            gate.defer(viewer, (ready, retired) -> {
                callbacks.add(ready);
                callbacks.add(retired);
            }, () -> valid, () -> fail("stale display"));
            if (scenario == 0) {
                gate.cancel(viewer);
            } else if (scenario == 2) {
                callbacks.getLast().run();
            }
            callbacks.getFirst().run();
            assertFalse(gate.isPending(viewer));
        }
    }
}
