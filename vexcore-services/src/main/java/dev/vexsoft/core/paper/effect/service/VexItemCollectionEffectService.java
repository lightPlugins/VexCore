package dev.vexsoft.core.paper.effect.service;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.effect.ItemCollectionRequest;
import dev.vexsoft.core.paper.packets.display.DisplayTransformation;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.display.item.FakeItemDisplayRequest;
import dev.vexsoft.core.paper.packets.display.item.FakeItemDisplayUpdate;
import dev.vexsoft.core.paper.packets.service.display.ItemDisplayPacketService;
import dev.vexsoft.core.paper.scheduler.VexTask;
import dev.vexsoft.core.paper.service.effect.ItemCollectionEffectService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Shared finite collection flights; one timer per viewer, bounded admission, region checks and packet-only displays. */
@Dependencies({ItemDisplayPacketService.class, ScheduleService.class})
public final class VexItemCollectionEffectService implements ItemCollectionEffectService, AutoCloseable {

    private static final int PER_PLAYER = 1024;
    private static final int GLOBAL = 4096;
    private static final int VISIBLE_PER_PLAYER = 64;
    private static final int VISIBLE_GLOBAL = 512;
    private static final long MAX_NANOS = 6_000_000_000L;
    private static final double VELOCITY_RETAINED = Math.sqrt(0.45);
    private final ItemDisplayPacketService displays;
    private final ScheduleService schedules;
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger visible = new AtomicInteger();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public VexItemCollectionEffectService(VexServiceRegistry services) {
        displays = services.require(ItemDisplayPacketService.class);
        schedules = services.require(ScheduleService.class);
    }

    @Override
    public boolean hasCapacity(Player player) {
        if (closed || active.get() >= GLOBAL) {
            return false;
        }
        var session = sessions.get(player.getUniqueId());
        if (session == null) {
            return true;
        }
        synchronized (session) {
            return session.flights.size() < PER_PLAYER;
        }
    }

    @Override
    public void play(Player player, ItemCollectionRequest request) {
        if (closed || !player.isOnline() || player.isDead() || !active(request)) {
            return;
        }
        Location origin = request.origin();
        Location target = player.getLocation().add(0, 1, 0);
        if (origin.getWorld() != target.getWorld() || origin.distanceSquared(target) > 1600) {
            return;
        }
        var session = sessions.computeIfAbsent(player.getUniqueId(), ignored -> new Session(player));
        synchronized (session) {
            if (session.player != player || !session.world.equals(target.getWorld().getUID())) {
                retire(session);
                play(player, request);
                return;
            }
            if (session.flights.size() >= PER_PLAYER || !reserve()) {
                idle(session);
                return;
            }
            var flight = new Flight(request, session.sequence++);
            session.flights.add(flight);
            if (closed) {
                retire(session);
                return;
            }
            if (session.task == null) {
                var task = schedules.runForTimer(player, 1, 1, () -> tick(session), () -> retire(session));
                if (task.isEmpty()) {
                    retire(session);
                    return;
                }
                session.task = task.get();
            }
            admit(session);
        }
    }

    private void admit(Session session) {
        int bucket = session.player.getTicksLived() / 2;
        if (session.admissionTick != bucket) {
            session.admissionTick = bucket;
            session.admissions = 0;
        }
        int count = session.visibleCount;
        if (count >= VISIBLE_PER_PLAYER || session.admissions >= 16 || visible.get() >= VISIBLE_GLOBAL) {
            return;
        }
        Location target = session.player.getLocation().add(0, 1, 0);
        for (var flight : List.copyOf(session.flights)) {
            if (flight.started) {
                continue;
            }
            Location origin = flight.request.origin();
            if (!valid(session, flight) || expired(flight) || origin.getWorld() != target.getWorld()
                || origin.distanceSquared(target) > 1600) {
                finish(session, flight, false);
                continue;
            }
            if (count >= VISIBLE_PER_PLAYER || session.admissions >= 16 || !reserveVisible()) {
                break;
            }
            count++;
            session.visibleCount++;
            session.admissions++;
            flight.started = true;
            flight.born = System.nanoTime();
            region(session, flight, origin, () -> {
                CollectionFlightSpace.Launch launch;
                try {
                    launch = CollectionFlightSpace.launch(origin, target, flight.request.blocks(), flight.scatter);
                } catch (RuntimeException failure) {
                    launch = null;
                }
                var result = launch;
                viewer(session, () -> initialize(session, flight, result));
            });
        }
    }

    @Override
    public void close() {
        closed = true;
        List.copyOf(sessions.values()).forEach(this::retire);
    }

    private boolean reserve() {
        int count;
        do {
            count = active.get();
            if (count >= GLOBAL) {
                return false;
            }
        } while (!active.compareAndSet(count, count + 1));
        return true;
    }

    private boolean reserveVisible() {
        int count;
        do {
            count = visible.get();
            if (count >= VISIBLE_GLOBAL) {
                return false;
            }
        } while (!visible.compareAndSet(count, count + 1));
        return true;
    }

    private void initialize(Session session, Flight flight, CollectionFlightSpace.Launch launch) {
        synchronized (session) {
            if (!session.flights.contains(flight)) {
                return;
            }
            if (!valid(session, flight)) {
                finish(session, flight, false);
            } else if (launch == null || expired(flight)
                || launch.start().distanceSquared(session.player.getLocation().add(0, 1, 0)) > 1600) {
                finish(session, flight, false);
            } else {
                try {
                    flight.launch = launch;
                    flight.position = launch.start();
                    flight.velocity = new Vector();
                    flight.throughTerrain = launch.throughTerrain();
                    flight.handle = displays.spawn(session.player, FakeItemDisplayRequest.builder(flight.position,
                        flight.request.item()).transformation(DisplayTransformation.scale(CollectionFlightSpace.ITEM_SCALE))
                        .shadowStrength(0).interpolationDuration(1).teleportDuration(1).build());
                    flight.lastMoveTick = session.player.getTicksLived();
                    flight.pending = false;
                } catch (RuntimeException failure) {
                    finish(session, flight, false);
                }
            }
        }
    }

    private void tick(Session session) {
        synchronized (session) {
            if (closed || !session.player.isOnline() || session.player.isDead()
                || !session.world.equals(session.player.getWorld().getUID())) {
                retire(session);
                return;
            }
            Location target = session.player.getLocation().add(0, 1, 0);
            if (session.previousTarget != null && target.distanceSquared(session.previousTarget) > 16) {
                retire(session);
                return;
            }
            session.targetMotion = session.previousTarget == null ? new Vector()
                : target.toVector().subtract(session.previousTarget.toVector());
            session.previousTarget = target;
            admit(session);
            for (var flight : List.copyOf(session.flights)) {
                if (flight.started) {
                    flight.age++;
                }
                if (!valid(session, flight)) {
                    finish(session, flight, false);
                } else if (expired(flight)) {
                    finish(session, flight, false);
                } else if (flight.started && !flight.pending) {
                    advance(session, flight, target);
                }
            }
        }
    }

    private boolean valid(Session session, Flight flight) {
        return !closed && sessions.get(session.player.getUniqueId()) == session && session.player.isOnline()
            && !session.player.isDead() && session.world.equals(session.player.getWorld().getUID())
            && active(flight.request);
    }

    private boolean expired(Flight flight) {
        return flight.started ? flight.age > 120 || System.nanoTime() - flight.born > MAX_NANOS
            : System.nanoTime() - flight.enqueued > 10_000_000_000L;
    }

    private boolean active(ItemCollectionRequest request) {
        try {
            return request.active().getAsBoolean();
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private void region(Session session, Flight flight, Location at, Runnable operation) {
        try {
            if (closed || flight.retired) {
                return;
            }
            if (Bukkit.isOwnedByCurrentRegion(at)) {
                operation.run();
                return;
            }
            schedules.runAt(at, () -> {
                if (closed || flight.retired) {
                    return;
                }
                try {
                    operation.run();
                } catch (RuntimeException failure) {
                    retire(session);
                }
            });
        } catch (RuntimeException failure) {
            finish(session, flight, false);
        }
    }

    private void viewer(Session session, Runnable operation) {
        if (Bukkit.isOwnedByCurrentRegion(session.player)) {
            operation.run();
        } else {
            schedules.runFor(session.player, operation, () -> retire(session));
        }
    }

    private void advance(Session session, Flight flight, Location target) {
        double distance = flight.position.distance(target);
        if (distance > 40) {
            finish(session, flight, false);
            return;
        }
        if (flight.hoverTicks >= 10 && distance <= 0.7) {
            finish(session, flight, true);
            return;
        }
        Location next;
        if (flight.launchFrames < 4) {
            flight.launchFrames++;
            next = flight.launch.start().clone().add(flight.launch.end().toVector()
                .subtract(flight.launch.start().toVector()).multiply(flight.launchFrames / 4.0));
        } else if (flight.hoverTicks < 10) {
            flight.hoverTicks++;
            move(session, flight, flight.position.clone());
            return;
        } else {
            flight.growing = distance > flight.previousDistance + 0.175 ? flight.growing + 1 : 0;
            if (flight.growing >= 8) {
                finish(session, flight, false);
                return;
            }
            Vector toward = target.toVector().subtract(flight.position.toVector());
            double arc = Math.min(2.0, distance * 0.4);
            Vector sideways = new Vector(-toward.getZ(), 0, toward.getX());
            if (sideways.lengthSquared() > 0.0001) {
                sideways.normalize().multiply(flight.bend * arc);
            }
            Vector desired = toward.add(sideways).add(flight.scatter.clone().multiply(arc * 0.6))
                .add(new Vector(0, flight.height * arc, 0)).normalize()
                .multiply(Math.min(0.65, 0.18 + distance * 0.045))
                .add(session.targetMotion.clone().multiply(0.8));
            if (desired.lengthSquared() > 0.64) {
                desired.normalize().multiply(0.8);
            }
            // Preserve the damping time and travel speed when updating every tick instead of every two ticks.
            flight.velocity.multiply(VELOCITY_RETAINED).add(desired.multiply(1 - VELOCITY_RETAINED));
            Vector movement = flight.velocity.clone();
            if (movement.length() > distance) {
                movement = target.toVector().subtract(flight.position.toVector());
            }
            next = flight.position.clone().add(movement);
        }
        flight.previousDistance = distance;
        checkStep(session, flight, next);
    }

    private void checkStep(Session session, Flight flight, Location next) {
        flight.pending = true;
        Location from = flight.position.clone();
        region(session, flight, from, () -> {
            boolean clear;
            boolean terrain = flight.throughTerrain;
            try {
                clear = terrain ? CollectionFlightSpace.available(from, next)
                    : CollectionFlightSpace.clear(from, next, flight.request.blocks());
                if (!clear && CollectionFlightSpace.available(from, next)) {
                    terrain = true;
                    clear = true;
                }
            } catch (RuntimeException failure) {
                clear = false;
            }
            boolean result = clear;
            boolean throughTerrain = terrain;
            viewer(session, () -> {
                synchronized (session) {
                    if (!session.flights.contains(flight)) {
                        return;
                    }
                    if (!valid(session, flight)) {
                        finish(session, flight, false);
                    } else if (expired(flight)) {
                        finish(session, flight, false);
                    } else if (result) {
                        flight.throughTerrain = throughTerrain;
                        move(session, flight, next);
                    } else {
                        finish(session, flight, false);
                    }
                }
            });
        });
    }

    private void move(Session session, Flight flight, Location next) {
        try {
            double traveled = flight.position.distance(next);
            int running = session.visibleCount;
            int budget = Math.clamp(96 / Math.max(1, running), 1, 4);
            int points = Math.min(budget, Math.max(1, (int) Math.ceil(traveled / 0.18)));
            Vector delta = next.toVector().subtract(flight.position.toVector());
            for (int index = 1; index <= points; index++) {
                Location point = flight.position.clone().add(delta.clone().multiply(index / (double) points));
                session.player.spawnParticle(Particle.DUST, point, traveled > 0.00001 ? 2 : 1, 0.025, 0.025, 0.025, 0,
                    index == points ? flight.accent : flight.dust, true);
            }
            float angle = flight.phase + flight.age * flight.spin;
            float sine = (float) Math.sin(angle / 2);
            float scale = (float) (CollectionFlightSpace.ITEM_SCALE * Math.min(1, Math.max(0.12,
                next.distance(session.player.getLocation().add(0, 1, 0)) / 1.2)));
            var transform = DisplayTransformation.scale(scale).toBuilder()
                .leftRotationX((float) flight.axis.getX() * sine)
                .leftRotationY((float) flight.axis.getY() * sine)
                .leftRotationZ((float) flight.axis.getZ() * sine).leftRotationW((float) Math.cos(angle / 2)).build();
            int tick = session.player.getTicksLived();
            int interpolation = Math.clamp(tick - flight.lastMoveTick, 1, 6);
            displays.update(flight.handle, FakeItemDisplayUpdate.builder().transformation(transform)
                .interpolationDelay(0).interpolationDuration(interpolation).teleportDuration(interpolation).build());
            if (traveled > 0.00001) {
                displays.teleport(flight.handle, next);
            }
            flight.lastMoveTick = tick;
            flight.position = next;
            flight.pending = false;
        } catch (RuntimeException failure) {
            finish(session, flight, false);
        }
    }

    private void finish(Session session, Flight flight, boolean arrived) {
        if (!session.flights.remove(flight)) {
            return;
        }
        flight.retired = true;
        active.decrementAndGet();
        if (flight.started) {
            visible.decrementAndGet();
            session.visibleCount--;
        }
        if (flight.handle != null) {
            displays.remove(flight.handle);
        }
        if (arrived) {
            var at = session.player.getLocation().add(0, 1, 0);
            session.player.spawnParticle(Particle.DUST, at, 4, 0.08, 0.1, 0.08, 0, flight.accent, true);
            session.player.playSound(Sound.sound(Key.key("minecraft:block.amethyst_block.chime"), Sound.Source.PLAYER,
                0.12F, ThreadLocalRandom.current().nextFloat(1.25F, 1.55F)));
        }
        idle(session);
    }

    private void idle(Session session) {
        if (session.flights.isEmpty() && sessions.remove(session.player.getUniqueId(), session)
            && session.task != null) {
            session.task.cancel();
        }
    }

    private void retire(Session session) {
        synchronized (session) {
            for (var flight : List.copyOf(session.flights)) {
                finish(session, flight, false);
            }
            idle(session);
        }
    }

    private static final class Session {
        private final Player player;
        private final UUID world;
        private final List<Flight> flights = new ArrayList<>();
        private VexTask task;
        private Location previousTarget;
        private Vector targetMotion = new Vector();
        private long sequence;
        private int visibleCount;
        private int admissionTick = Integer.MIN_VALUE;
        private int admissions;

        private Session(Player player) {
            this.player = player;
            world = player.getWorld().getUID();
        }
    }

    private static final class Flight {
        private final ItemCollectionRequest request;
        private final long enqueued = System.nanoTime();
        private long born;
        private boolean started;
        private final double bend;
        private final double height;
        private final float spin;
        private final float phase;
        private final Vector axis;
        private final Vector scatter;
        private final Particle.DustOptions dust;
        private final Particle.DustOptions accent;
        private FakeDisplayHandle handle;
        private CollectionFlightSpace.Launch launch;
        private Location position;
        private Vector velocity;
        private boolean pending = true;
        private volatile boolean retired;
        private boolean throughTerrain;
        private int age;
        private int launchFrames;
        private int hoverTicks;
        private int lastMoveTick;
        private int growing;
        private double previousDistance = Double.POSITIVE_INFINITY;

        private Flight(ItemCollectionRequest request, long sequence) {
            this.request = request;
            var random = ThreadLocalRandom.current();
            double direction = sequence * 2.399963229728653 + random.nextDouble(-0.2, 0.2);
            double radius = random.nextDouble(0.5, 0.9);
            scatter = new Vector(Math.cos(direction) * radius, random.nextDouble(0.2, 0.6),
                Math.sin(direction) * radius);
            bend = random.nextDouble(0.4, 1.0) * (random.nextBoolean() ? 1 : -1);
            height = random.nextDouble(0.15, 0.5);
            spin = (float) random.nextDouble(Math.PI * 0.2, Math.PI * 0.3);
            phase = (float) random.nextDouble(Math.PI * 2);
            axis = new Vector(random.nextDouble(-0.4, 0.4), 1, random.nextDouble(-0.4, 0.4)).normalize();
            dust = new Particle.DustOptions(Color.fromRGB(request.color()), 0.75F);
            accent = new Particle.DustOptions(Color.fromRGB(request.accentColor()), 0.5F);
        }
    }
}
