package dev.vexsoft.core.paper.nms.v26_2.goal;

import dev.vexsoft.core.paper.nms.goal.NmsMobGoalControl;
import dev.vexsoft.core.paper.nms.position.FlightPositionSafety;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

/** Keeps native flight navigation and collision physics while honoring speed and optional terrain clearance. */
public final class V26_2FlightMoveControl extends MoveControl<Mob> {

    private final MoveControl<?> delegate;
    private final LivingEntity carrier;
    private final double minimumHeight;

    public V26_2FlightMoveControl(final Mob mob, final double minimumHeight) {
        this(mob, (LivingEntity) mob.getBukkitEntity(), mob.getMoveControl(), minimumHeight);
    }

    V26_2FlightMoveControl(
        final Mob mob,
        final LivingEntity carrier,
        final MoveControl<?> delegate,
        final double minimumHeight
    ) {
        super(mob);
        this.delegate = delegate;
        this.carrier = carrier;
        this.minimumHeight = minimumHeight;
    }

    @Override
    public void setWantedPosition(final double x, final double y, final double z, final double speed) {
        if (minimumHeight <= 0.0D) {
            delegate.setWantedPosition(x, y, z, speed);
            return;
        }
        double floor = FlightPositionSafety.minimumY(new Location(carrier.getWorld(), x, y, z), minimumHeight);
        if (Double.isFinite(floor)) {
            delegate.setWantedPosition(x, Math.max(y, floor), z, speed);
        }
    }

    @Override
    public void tick() {
        // Pausing goals hands movement to the caller (for example, a retreat path); it does not freeze the carrier.
        if (!NmsMobGoalControl.isPaused(carrier) && minimumHeight > 0.0D) {
            Location current = carrier.getLocation();
            double floor = FlightPositionSafety.minimumY(current, minimumHeight);
            if (Double.isFinite(floor) && current.getY() < floor - 0.05D) {
                delegate.setWantedPosition(current.getX(), floor, current.getZ(),
                    Math.max(1.0D, delegate.getSpeedModifier()));
            }
        }
        boolean moving = delegate.hasWanted();
        delegate.tick();
        if (moving && delegate instanceof FlyingMoveControl<?> && (mob instanceof Bee || mob instanceof Parrot)
            && !mob.onGround() && !mob.isInLiquid() && !mob.isFallFlying() && !mob.isPassenger()) {
            Vec3 targetDelta = new Vec3(delegate.getWantedX() - mob.getX(), delegate.getWantedY() - mob.getY(),
                delegate.getWantedZ() - mob.getZ());
            Vec3 input = airInput(new Vec3(mob.xxa, mob.yya, mob.zza), targetDelta, mob.getSpeed());
            mob.setXxa((float) input.x);
            mob.setYya((float) input.y);
            mob.setZza((float) input.z);
            // LivingEntity's airborne travel uses 0.02 acceleration and normalizes inputs above length one.
            // Add exactly the lost acceleration before normal travel; collision and drag still run natively.
            mob.moveRelative(additionalAirAcceleration(input), input);
        }
    }

    @Override
    public boolean hasWanted() {
        return delegate.hasWanted();
    }

    @Override
    public double getSpeedModifier() {
        return delegate.getSpeedModifier();
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

    @Override
    public void strafe(final float forwards, final float right) {
        delegate.strafe(forwards, right);
    }

    @Override
    public void setWait() {
        delegate.setWait();
    }

    static Vec3 airInput(final Vec3 nativeInput, final Vec3 targetDelta, final double speed) {
        // Native FlyingMoveControl sends full vertical input even at equal height and forward input on pure ascents.
        double vertical = Math.clamp(targetDelta.y, -speed, speed);
        double forward = targetDelta.horizontalDistanceSqr() > 1.0E-7D ? nativeInput.z : 0.0D;
        return new Vec3(nativeInput.x, vertical, forward);
    }

    static float additionalAirAcceleration(final Vec3 input) {
        return (float) (0.02D * Math.max(0.0D, input.length() - 1.0D));
    }
}
