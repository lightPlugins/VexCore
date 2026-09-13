package dev.vexsoft.core.paper.nms.v26_2.goal;

import java.util.function.Predicate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Bounded homing flight with swept target hits and exactly-once completion. */
final class OwnerProjectileFlight {

    private final double speed;
    private int remainingTicks;
    private boolean finished;

    OwnerProjectileFlight(final double speed, final int lifetimeTicks) {
        this.speed = speed;
        remainingTicks = lifetimeTicks;
    }

    void tick(final boolean valid, final Vec3 from, final AABB target,
        final Predicate<Vec3> move, final Runnable impact, final Runnable remove) {
        if (finished) {
            return;
        }
        if (!valid || remainingTicks-- <= 0) {
            finished = true;
            remove.run();
            return;
        }
        Vec3 delta = target.getCenter().subtract(from);
        Vec3 next = from.add(delta.normalize().scale(Math.min(speed, delta.length())));
        if (!move.test(next)) {
            finished = true;
            remove.run();
            return;
        }
        if (target.contains(from) || target.clip(from, next).isPresent()) {
            finished = true;
            try {
                impact.run();
            } finally {
                remove.run();
            }
        }
    }
}
