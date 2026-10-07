package dev.vexsoft.core.paper.nms.v26_2.goal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.entity.ai.control.LookControl;
import org.junit.jupiter.api.Test;

/** Reproduces native idle pitch resets without requiring a running Minecraft world. */
public final class V26_2MobLookControlTest {

    @Test
    void preservesUpwardAndDownwardSpawnPitchAcrossIdleTicks() {
        for (float configuredPitch : new float[]{-40.0F, 0.0F, 35.0F}) {
            var pitch = new AtomicReference<>(configuredPitch);
            var delegate = new TrackingLookControl(pitch);
            var control = new V26_2MobLookControl(null, delegate, pitch::get, pitch::set);
            for (int tick = 0; tick < 30; tick++) {
                control.tick();
                assertEquals(configuredPitch, pitch.get());
            }
            assertEquals(30, delegate.ticks);
            assertFalse(control.isLookingAtTarget());
        }
    }

    @Test
    void forwardsActiveLookRequestsAndRetainsTheLastPitchWhenTrackingEnds() {
        var pitch = new AtomicReference<>(35.0F);
        var delegate = new TrackingLookControl(pitch);
        var control = new V26_2MobLookControl(null, delegate, pitch::get, pitch::set);
        control.setLookAt(10, 80, -5, 9, 180);
        assertTrue(control.isLookingAtTarget());
        assertEquals(10.0D, control.getWantedX());
        assertEquals(80.0D, control.getWantedY());
        assertEquals(-5.0D, control.getWantedZ());
        assertEquals(9.0F, delegate.yawSpeed);
        assertEquals(180.0F, delegate.pitchSpeed);
        control.tick();
        assertEquals(-25.0F, pitch.get(), "An active native look request must be allowed to change pitch");
        control.tick();
        assertFalse(control.isLookingAtTarget());
        control.tick();
        assertEquals(-25.0F, pitch.get(), "Idle ticking must retain the final tracked pitch");
    }

    private static final class TrackingLookControl extends LookControl {

        private final AtomicReference<Float> pitch;
        private int ticks;
        private float yawSpeed;
        private float pitchSpeed;

        private TrackingLookControl(final AtomicReference<Float> pitch) {
            super(null);
            this.pitch = pitch;
        }

        @Override
        public void tick() {
            ticks++;
            pitch.set(lookAtCooldown > 0 ? -25.0F : 0.0F);
            if (lookAtCooldown > 0) {
                lookAtCooldown--;
            }
        }

        @Override
        public void setLookAt(
            final double x,
            final double y,
            final double z,
            final float yawSpeed,
            final float pitchSpeed
        ) {
            super.setLookAt(x, y, z, yawSpeed, pitchSpeed);
            this.yawSpeed = yawSpeed;
            this.pitchSpeed = pitchSpeed;
        }
    }
}
