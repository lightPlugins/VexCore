package dev.vexsoft.core.paper.nms.v26_2.goal;

import dev.vexsoft.core.api.mob.flight.FlightBounds;
import dev.vexsoft.core.api.mob.flight.FlightMotion;
import dev.vexsoft.core.api.mob.flight.FlightProfile;
import dev.vexsoft.core.api.mob.flight.FlightState;
import dev.vexsoft.core.api.mob.flight.FlightVector;
import dev.vexsoft.core.paper.nms.goal.NmsMobGoalControl;
import dev.vexsoft.core.paper.nms.position.FlightPositionSafety;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.util.Vector;

/** Authoritative flight motor: native navigation supplies waypoints, never acceleration or velocity. */
public final class V26_2FlightMoveControl extends MoveControl<Mob> {

    // Model plugins decorate the native controller after installation. Keep its identity independently
    // of that wrapper. Both sides are weak: a strong value would retain its own weak-key carrier.
    private static final Map<Mob, WeakReference<V26_2FlightMoveControl>> CONTROLLERS =
        Collections.synchronizedMap(new WeakHashMap<>());

    private final org.bukkit.entity.Mob carrier;
    private final FlightProfile profile;
    private final double minimumHeight;
    private FlightVector velocity = FlightVector.ZERO;
    private FlightVector trackingVelocity = FlightVector.ZERO;
    private Location trackingTarget;
    private double trackingSpeed;
    private boolean trackingDirect;
    private Location destination;
    private FlightBounds bounds;
    private Location idle;
    private Location previousPosition;
    private FlightState state = FlightState.MOVING;
    private int routeAfter;
    private int stalled;
    private int waypointTick = -1;
    private boolean direct;
    private boolean clearRoute;

    public V26_2FlightMoveControl(final Mob mob, final double minimumHeight, final FlightProfile profile) {
        this(mob, (org.bukkit.entity.Mob) mob.getBukkitEntity(), minimumHeight, profile);
    }

    V26_2FlightMoveControl(final Mob mob, final org.bukkit.entity.Mob carrier,
        final double minimumHeight, final FlightProfile profile) {
        super(mob);
        this.carrier = carrier;
        this.profile = profile;
        this.minimumHeight = minimumHeight;
        CONTROLLERS.put(mob, new WeakReference<>(this));
    }

    /** Returns the installed motor even when another plugin has decorated the native controller. */
    public static V26_2FlightMoveControl find(final Mob mob) {
        var reference = CONTROLLERS.get(mob);
        return reference == null ? null : reference.get();
    }

    /** Retires controller lookup state when the native carrier is neutralized or removed. */
    public static void forget(final Mob mob) {
        CONTROLLERS.remove(mob);
    }

    /** Returns the configured terrain clearance independently of navigation wrappers. */
    public double minimumHeight() {
        return minimumHeight;
    }

    /** Returns whether the motor currently owns a travel heading. */
    public boolean isMoving() {
        return velocity.length() > 0.005;
    }

    /** Resolves the same local minimum clearance for goals, orders and arrival checks. */
    public Location resolveTarget(final Location target) {
        Location resolved = target.clone();
        if (minimumHeight > 0) {
            double floor = FlightPositionSafety.minimumY(resolved, minimumHeight);
            if (Double.isFinite(floor)) {
                resolved.setY(Math.max(resolved.getY(), floor));
            }
        }
        return resolved;
    }

    /** Resolves a nearby free follow altitude without treating a low ceiling as a floor above the owner. */
    public Location resolveTrackingTarget(final Location target) {
        var body = carrier.getBoundingBox();
        double width = Math.max(body.getWidthX(), body.getWidthZ());
        for (double offset : new double[]{0, -0.5, -1, 0.5, 1}) {
            Location candidate = target.clone().add(0, offset, 0);
            if (!FlightPositionSafety.isClear(candidate, width, body.getHeight())) {
                continue;
            }
            candidate = resolveTarget(candidate);
            if (candidate.getY() <= target.getY() + 1
                && FlightPositionSafety.isClear(candidate, width, body.getHeight())) {
                return candidate;
            }
        }
        return target.clone();
    }

    /** Replaces the external order while preserving acceleration state. */
    public FlightState flyTo(final Location target, final double speed) {
        return flyTo(target, speed, null);
    }

    /** Replaces an order together with its optional allowed volume. */
    public FlightState flyTo(final Location target, final double speed, final FlightBounds bounds) {
        stopTracking();
        if (!Objects.equals(this.bounds, bounds)) {
            routeAfter = 0;
        }
        this.bounds = bounds;
        Location resolved = resolveTarget(target);
        boolean changed = destination == null || destination.distanceSquared(resolved) > 0.01;
        if (changed) {
            state = FlightState.MOVING;
            routeAfter = 0;
            stalled = 0;
        }
        destination = resolved;
        speedModifier = speed;
        boolean arrived = speed == 0 ? velocity.length() <= 0.005
            : FlightMotion.arrived(carrier.getLocation().distance(destination), velocity, profile);
        if (arrived) {
            state = FlightState.ARRIVED;
        } else if (state == FlightState.ARRIVED) {
            state = FlightState.MOVING;
        }
        return state;
    }

    /** Clears old movement only after an explicit spawn/recovery placement, never during ordinary order changes. */
    public void resetAfterPlacement() {
        release();
        velocity = FlightVector.ZERO;
        previousPosition = null;
        Location current = carrier.getLocation();
        wantedX = current.getX();
        wantedY = current.getY();
        wantedZ = current.getZ();
        speedModifier = 0;
        waypointTick = -1;
        state = FlightState.MOVING;
        mob.setDeltaMovement(Vec3.ZERO);
    }

    /** Releases caller-owned flight without injecting a stop or restarting acceleration. */
    public void release() {
        destination = null;
        bounds = null;
        trackingTarget = null;
        trackingVelocity = FlightVector.ZERO;
        trackingDirect = false;
        operation = Operation.WAIT;
        idle = null;
        routeAfter = 0;
        stalled = 0;
        direct = false;
        clearRoute = false;
        carrier.getPathfinder().stopPathfinding();
    }

    /** Releases only the follow goal's target, preserving any explicit order and current momentum. */
    public void stopTracking() {
        if (trackingTarget == null) {
            return;
        }
        trackingTarget = null;
        trackingVelocity = FlightVector.ZERO;
        trackingDirect = false;
        operation = Operation.WAIT;
        idle = null;
        carrier.getPathfinder().stopPathfinding();
    }

    /** Supplies a moving direct goal; false leaves native pathfinding in charge around obstacles. */
    public boolean track(final Location target, final Vec3 movement, final double speed) {
        Location current = carrier.getLocation();
        Location resolved = resolveTrackingTarget(target);
        boolean changed = trackingTarget == null || trackingTarget.distanceSquared(resolved) > 0.25;
        boolean wasDirect = trackingDirect;
        trackingTarget = resolved;
        trackingSpeed = speed;
        trackingDirect = clearLine(current, resolved);
        trackingVelocity = new FlightVector(movement.x, movement.y, movement.z);
        if (!trackingDirect) {
            if (wasDirect || changed) {
                // A failed new pursuit must not keep flying toward the previous waypoint or idle anchor.
                carrier.getPathfinder().stopPathfinding();
                setWait();
            }
            return false;
        }
        carrier.getPathfinder().stopPathfinding();
        setWantedPosition(resolved.getX(), resolved.getY(), resolved.getZ(), speed);
        return true;
    }

    @Override
    public void setWantedPosition(final double x, final double y, final double z, final double speed) {
        super.setWantedPosition(x, y, z, speed);
        waypointTick = mob.tickCount;
    }

    @Override
    public void setWait() {
        operation = Operation.WAIT;
    }

    @Override
    public void tick() {
        // No native FlyingMoveControl or normalized air inputs may run alongside this motor.
        mob.setXxa(0);
        mob.setYya(0);
        mob.setZza(0);
        mob.setSpeed(0);
        if (mob.isPassenger() || mob.isFallFlying()) {
            return;
        }
        if (NmsMobGoalControl.isPaused(carrier) && destination == null && waypointTick != mob.tickCount) {
            stopTracking();
            idle = null;
            velocity = FlightVector.ZERO;
            return;
        }
        mob.setNoGravity(true);
        Location current = carrier.getLocation();
        Location target;
        FlightVector targetVelocity = FlightVector.ZERO;
        double speed = speedModifier;
        if (destination != null) {
            target = externalWaypoint(current);
            speed = speedModifier;
        } else if (trackingTarget != null) {
            // Navigation ticks after goals and can submit late waypoints. Direct following owns its target
            // and feed-forward separately, so those waypoints cannot overwrite the current owner position.
            idle = null;
            speed = trackingSpeed;
            if (trackingDirect) {
                target = trackingTarget.clone();
                targetVelocity = trackingVelocity;
            } else if (!mob.getNavigation().isDone() && hasWanted()) {
                target = new Location(current.getWorld(), wantedX, wantedY, wantedZ);
            } else {
                target = current.clone();
                speed = 0;
            }
        } else if (hasWanted()) {
            target = new Location(current.getWorld(), wantedX, wantedY, wantedZ);
            if (current.distanceSquared(target) < 0.01 && mob.getNavigation().isDone()) {
                operation = Operation.WAIT;
            }
            idle = null;
        } else {
            if (idle == null) {
                idle = current.clone();
            }
            target = idle.clone();
            speed = profile.maxSpeed();
            if (profile.hoverHeight() > 0) {
                double floor = FlightPositionSafety.hoverY(current, profile.hoverHeight());
                if (Double.isFinite(floor)) {
                    target.setY(floor);
                }
            }
        }
        if (minimumHeight > 0) {
            double floor = FlightPositionSafety.minimumY(target, minimumHeight);
            if (Double.isFinite(floor)) {
                target.setY(Math.max(target.getY(), floor));
            }
            double currentFloor = FlightPositionSafety.minimumY(current, minimumHeight);
            if (Double.isFinite(currentFloor) && current.getY() < currentFloor) {
                target.setY(Math.max(target.getY(), currentFloor));
            }
        }
        var delta = new FlightVector(target.getX() - current.getX(), target.getY() - current.getY(),
            target.getZ() - current.getZ());
        // Adopt collision feedback and explicit native impulses (damage/launch), but never integrate ordinary drag.
        if (mob.horizontalCollision || mob.verticalCollision || mob.hurtMarked) {
            Vec3 actual = mob.getDeltaMovement();
            velocity = new FlightVector(actual.x, actual.y, actual.z);
        }
        var path = mob.getNavigation().getPath();
        boolean stopAtTarget = destination != null && direct || path == null || path.isDone()
            || path.getNextNodeIndex() >= path.getNodeCount() - 1;
        velocity = FlightMotion.advance(delta, velocity, targetVelocity, speed, profile, stopAtTarget);
        Location next = current.clone().add(velocity.x(), velocity.y(), velocity.z());
        if (!clearLine(current, next)) {
            velocity = FlightVector.ZERO;
            state = FlightState.BLOCKED;
            routeAfter = 0;
        }
        if (destination != null) {
            boolean moved = previousPosition == null || previousPosition.distanceSquared(current) > 0.000001;
            stalled = moved || speed == 0 ? 0 : stalled + 1;
            boolean arrived = speed == 0 ? velocity.length() <= 0.005
                : FlightMotion.arrived(current.distance(destination), velocity, profile);
            if (arrived) {
                state = FlightState.ARRIVED;
            } else if (stalled >= 40 || !clearRoute) {
                state = FlightState.BLOCKED;
            } else if (state != FlightState.BLOCKED) {
                state = FlightState.MOVING;
            }
        }
        previousPosition = current.clone();
        // Native travel still handles collisions and drag after this tick's commanded displacement.
        // The next motor tick supplies the next displacement, so drag never becomes a feature-specific multiplier.
        mob.setDeltaMovement(new Vec3(velocity.x(), velocity.y(), velocity.z()));
        if (velocity.length() > 0.0001) {
            Location facing = current.clone().setDirection(
                new Vector(velocity.x(), velocity.y(), velocity.z()));
            mob.setYRot(FlightMotion.heading(mob.getYRot(), facing.getYaw(), profile));
            mob.setXRot(FlightMotion.heading(mob.getXRot(), facing.getPitch(), profile));
            mob.setYBodyRot(mob.getYRot());
            mob.setYHeadRot(mob.getYRot());
        }
    }

    private Location externalWaypoint(final Location current) {
        if (mob.tickCount >= routeAfter) {
            routeAfter = mob.tickCount + 5;
            direct = clearLine(current, destination);
            if (direct) {
                carrier.getPathfinder().stopPathfinding();
                clearRoute = true;
            } else if (destination.getWorld().isChunkLoaded(destination.getBlockX() >> 4, destination.getBlockZ() >> 4)) {
                clearRoute = carrier.getPathfinder().moveTo(destination, speedModifier);
            } else {
                carrier.getPathfinder().stopPathfinding();
                clearRoute = false;
            }
            state = clearRoute ? FlightState.MOVING : FlightState.BLOCKED;
        }
        if (direct) {
            return destination.clone();
        }
        if (clearRoute && !mob.getNavigation().isDone() && hasWanted()) {
            return new Location(current.getWorld(), wantedX, wantedY, wantedZ);
        }
        return current.clone();
    }

    private boolean clearLine(final Location from, final Location to) {
        if (bounds != null && !bounds.contains(to.getX(), to.getY(), to.getZ())) {
            return false;
        }
        double distance = from.distance(to);
        if (distance > 32) {
            return false;
        }
        int steps = Math.max(1, (int) Math.ceil(distance / 0.25));
        var bounds = carrier.getBoundingBox();
        double width = Math.max(bounds.getWidthX(), bounds.getWidthZ());
        for (int step = 1; step <= steps; step++) {
            double fraction = (double) step / steps;
            Location point = from.clone().add((to.getX() - from.getX()) * fraction,
                (to.getY() - from.getY()) * fraction, (to.getZ() - from.getZ()) * fraction);
            if (this.bounds != null && !this.bounds.contains(point.getX(), point.getY(), point.getZ())
                || !FlightPositionSafety.isClear(point, width, bounds.getHeight())) {
                return false;
            }
        }
        return true;
    }
}
