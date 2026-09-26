package dev.vexsoft.core.api.mob;

import java.util.UUID;

/** Starts an optional melee windup without coupling VexCore to an animation plugin. */
@FunctionalInterface
public interface MobMeleeAttackSequence {

    /** Starts one attack animation, or returns null when the animation cannot be started. */
    Attack begin(UUID mobId, UUID targetId);

    /** One active attack whose damage timing and completion are owned by the caller. */
    interface Attack {

        /** Returns the number of ticks after the start at which damage should be applied. */
        int damageDelayTicks();

        /** Returns whether movement may resume after the animation. */
        boolean isFinished();

        /** Stops the animation when the mob's attack goal is interrupted. */
        void cancel();
    }
}
