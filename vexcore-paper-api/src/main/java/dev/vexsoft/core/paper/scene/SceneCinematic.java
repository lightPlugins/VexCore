package dev.vexsoft.core.paper.scene;

import java.util.List;
import java.util.Objects;

/** Camera shots on a scene's timeline and an optional fixed viewer time that remains after playback. */
public record SceneCinematic(List<Shot> shots, Long playerTime) {

    public SceneCinematic {
        shots = List.copyOf(shots);
        if (shots.isEmpty() || shots.getFirst().startMillis() != 0
            || playerTime != null && (playerTime < 0 || playerTime >= 24_000)) {
            throw new IllegalArgumentException("A cinematic must start at zero and use a valid player time");
        }
        long previous = -1;
        for (var shot : shots) {
            if (shot.startMillis() <= previous) {
                throw new IllegalArgumentException("Cinematic shots must be in strictly increasing order");
            }
            previous = shot.startMillis();
        }
    }

    /** One movement ending at the next cut or the scene's end. */
    public record Shot(long startMillis, Pose from, Pose to) {

        public Shot {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
        }
    }

    /** Camera position and rotation in the viewer's current world. */
    public record Pose(double x, double y, double z, float yaw, float pitch) {

        public Pose {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw)
                || !Float.isFinite(pitch) || Math.abs(pitch) > 90.0F) {
                throw new IllegalArgumentException("Invalid cinematic camera position or rotation");
            }
        }
    }
}
