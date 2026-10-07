package dev.vexsoft.core.paper.nms.v26_2.goal;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;

/** Preserves custom mob pitch while retaining the carrier's native look controller. */
public final class V26_2MobLookControl extends LookControl {

    private final LookControl delegate;
    private final Supplier<Float> pitchReader;
    private final Consumer<Float> pitchWriter;

    public V26_2MobLookControl(final Mob mob, final LookControl delegate) {
        this(mob, delegate, mob::getXRot, mob::setXRot);
    }

    V26_2MobLookControl(
        final Mob mob,
        final LookControl delegate,
        final Supplier<Float> pitchReader,
        final Consumer<Float> pitchWriter
    ) {
        super(mob);
        this.delegate = delegate;
        this.pitchReader = pitchReader;
        this.pitchWriter = pitchWriter;
    }

    @Override
    public void tick() {
        float previousPitch = pitchReader.get();
        boolean looking = delegate.isLookingAtTarget();
        delegate.tick();
        if (!looking) {
            // Vanilla LookControl resets pitch each idle tick, losing configured spawn rotation.
            pitchWriter.accept(previousPitch);
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
        delegate.setLookAt(x, y, z, yawSpeed, pitchSpeed);
    }

    @Override
    public boolean isLookingAtTarget() {
        return delegate.isLookingAtTarget();
    }

    @Override
    public double getWantedX() {
        return delegate.getWantedX();
    }

    @Override
    public double getWantedY() {
        return delegate.getWantedY();
    }

    @Override
    public double getWantedZ() {
        return delegate.getWantedZ();
    }
}
