package dev.vexsoft.core.paper.nms.v26_2.goal;

import dev.vexsoft.core.api.mob.flight.FlightProfile;
import dev.vexsoft.core.paper.nms.goal.NmsMobGoalControl;
import java.lang.reflect.Proxy;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.entity.CraftMob;
import org.bukkit.util.Vector;
import org.bukkit.util.BoundingBox;
import sun.misc.Unsafe;

/** Runs the production controller against native motion fields and a bounded, deterministic terrain column. */
final class FlightFixture implements AutoCloseable {

    final UUID id = UUID.randomUUID();
    final Mob nativeMob;
    final V26_2FlightMoveControl control;
    final org.bukkit.entity.Mob carrier;
    final World world;
    Location position;
    boolean loaded = true;
    boolean wall;
    double ceiling = Double.POSITIVE_INFINITY;
    int pathRequests;
    int recoveries;

    FlightFixture(final double minimumHeight) throws Exception {
        this(minimumHeight, FlightProfile.defaults());
    }

    FlightFixture(final double minimumHeight, final FlightProfile profile) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        world = proxy(World.class, (instance, method, arguments) -> switch (method.getName()) {
            case "equals" -> instance == arguments[0];
            case "isChunkLoaded" -> loaded;
            case "getMinHeight" -> -64;
            case "getMaxHeight" -> 320;
            case "getBlockAt" -> {
                if (!loaded) {
                    throw new AssertionError("Flight must not load or read unavailable terrain");
                }
                int x = (int) arguments[0];
                int y = (int) arguments[1];
                int z = (int) arguments[2];
                yield proxy(Block.class, (block, call, values) -> switch (call.getName()) {
                    case "isLiquid" -> false;
                    case "isPassable" -> y >= 64 && y < ceiling && !(wall && x == 2);
                    case "getBoundingBox" -> new BoundingBox(x, y, z, x + 1, y + 1, z + 1);
                    default -> throw new AssertionError(call.getName());
                });
            }
            default -> throw new AssertionError(method.getName());
        });
        position = new Location(world, 0.5, 65, 0.5);
        var pathfinder = proxy(com.destroystokyo.paper.entity.Pathfinder.class,
            (instance, method, arguments) -> switch (method.getName()) {
                case "stopPathfinding" -> null;
                case "hasPath" -> false;
                case "moveTo" -> {
                    pathRequests++;
                    yield false;
                }
                default -> throw new AssertionError(method.getName());
            });
        carrier = proxy(org.bukkit.entity.Mob.class, (instance, method, arguments) -> switch (method.getName()) {
            case "isValid" -> true;
            case "getUniqueId" -> id;
            case "getWorld" -> world;
            case "getLocation" -> position.clone();
            case "getPathfinder" -> pathfinder;
            case "getBoundingBox" -> new BoundingBox(position.getX() - 0.3, position.getY(),
                position.getZ() - 0.3, position.getX() + 0.3, position.getY() + 0.6, position.getZ() + 0.3);
            default -> throw new AssertionError(method.getName());
        });
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe allocator = (Unsafe) field.get(null);
        // Skip world-owning native constructors. Only motion fields are exercised; no server entity is spawned.
        nativeMob = (NativeMob) allocator.allocateInstance(NativeMob.class);
        nativeMob.setId(id.hashCode());
        ((NativeMob) nativeMob).navigation = (IdleNavigation) allocator.allocateInstance(IdleNavigation.class);
        var lock = net.minecraft.world.entity.Entity.class.getDeclaredField("posLock");
        lock.setAccessible(true);
        lock.set(nativeMob, new Object());
        nativeMob.setDeltaMovement(Vec3.ZERO);
        control = new V26_2FlightMoveControl(nativeMob, carrier, minimumHeight, profile);
        setMoveControl(control);
        var look = Mob.class.getDeclaredField("lookControl");
        look.setAccessible(true);
        look.set(nativeMob, new PassiveLookControl(nativeMob));
    }

    void tick() {
        nativeMob.tickCount++;
        nativeMob.getMoveControl().tick();
        Vec3 displacement = nativeMob.getDeltaMovement();
        position.add(displacement.x, displacement.y, displacement.z);
        // Native travel damps velocity after moving. The motor must not integrate that drag as acceleration.
        nativeMob.setDeltaMovement(displacement.scale(0.91));
    }

    CraftMob craftCarrier() throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var allocator = (Unsafe) field.get(null);
        var craft = (Carrier) allocator.allocateInstance(Carrier.class);
        craft.fixture = this;
        return craft;
    }

    void wrapMoveControl() throws Exception {
        setMoveControl(new DecoratedMoveControl(nativeMob, control));
    }

    private void setMoveControl(final MoveControl<?> movement) throws Exception {
        var field = Mob.class.getDeclaredField("moveControl");
        field.setAccessible(true);
        field.set(nativeMob, movement);
    }

    @Override
    public void close() {
        NmsMobGoalControl.setPaused(carrier, false);
        V26_2FlightMoveControl.forget(nativeMob);
    }

    private static <T> T proxy(final Class<T> type, final java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    /** Models the decoration installed by ModelEngine after Core configured the carrier. */
    private static final class DecoratedMoveControl extends MoveControl<Mob> {
        private final MoveControl<?> delegate;

        private DecoratedMoveControl(final Mob mob, final MoveControl<?> delegate) {
            super(mob);
            this.delegate = delegate;
        }

        @Override
        public void tick() {
            delegate.tick();
        }

        @Override
        public void setWantedPosition(final double x, final double y, final double z, final double speed) {
            delegate.setWantedPosition(x, y, z, speed);
        }
    }

    private static final class PassiveLookControl extends LookControl {
        private PassiveLookControl(final Mob mob) {
            super(mob);
        }

        @Override
        public void setLookAt(final double x, final double y, final double z) {
        }
    }

    /** Uses the real CraftMob boundary required by the production follow constructor. */
    private static final class Carrier extends CraftMob {
        private FlightFixture fixture;

        private Carrier() {
            super(null, null);
        }

        @Override
        public Mob getHandle() {
            return fixture.nativeMob;
        }

        @Override
        public UUID getUniqueId() {
            return fixture.id;
        }

        @Override
        public World getWorld() {
            return fixture.world;
        }

        @Override
        public Location getLocation() {
            return fixture.position.clone();
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public com.destroystokyo.paper.entity.Pathfinder getPathfinder() {
            return fixture.carrier.getPathfinder();
        }

        @Override
        public BoundingBox getBoundingBox() {
            return fixture.carrier.getBoundingBox();
        }

        @Override
        public boolean teleport(final Location destination) {
            fixture.recoveries++;
            fixture.position = destination.clone();
            return true;
        }

        @Override
        public void setVelocity(final Vector velocity) {
            fixture.nativeMob.setDeltaMovement(new Vec3(velocity.getX(), velocity.getY(), velocity.getZ()));
        }
    }

    private static final class NativeMob extends Mob {
        private PathNavigation navigation;

        private NativeMob() {
            super(null, null);
        }

        @Override
        public HumanoidArm getMainArm() {
            return HumanoidArm.RIGHT;
        }

        @Override
        public void setNoGravity(final boolean value) {
        }

        @Override
        public boolean isFallFlying() {
            return false;
        }

        @Override
        public void setSpeed(final float value) {
        }

        @Override
        public PathNavigation getNavigation() {
            return navigation;
        }
    }

    private static final class IdleNavigation extends FlyingPathNavigation {
        private IdleNavigation() {
            super(null, null);
        }

        @Override
        public boolean isDone() {
            return true;
        }
    }
}
