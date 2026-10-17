package dev.vexsoft.core.paper.service.effect;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.effect.RingBurst;
import dev.vexsoft.core.paper.scheduler.VexTask;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/** Finite entity-scheduled animations with bounded packet counts and explicit owner cleanup. */
@Dependencies(ScheduleService.class)
public final class VexParticleEffectService implements ParticleEffectService, AutoCloseable {

    private final ScheduleService schedules;
    private final Map<UUID, Animation> animations = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public VexParticleEffectService(final VexServiceRegistry services) {
        schedules = services.require(ScheduleService.class);
    }

    @Override
    public void ringBurst(final Player player, final RingBurst settings) {
        schedules.runFor(player, () -> {
            if (closed || !player.isOnline()) {
                return;
            }
            var previous = animations.remove(player.getUniqueId());
            if (previous != null) {
                stop(previous);
            }
            var animation = new Animation(player, player.getLocation().clone().add(0, settings.offsetY(), 0), settings);
            animations.put(player.getUniqueId(), animation);
            animation.task = schedules.runForTimer(player, 1, 2, () -> tick(animation), () -> stop(animation))
                .orElse(null);
            if (animation.task == null) {
                stop(animation);
            }
        });
    }

    @Override
    public void close() {
        closed = true;
        animations.values().forEach(this::stop);
        animations.clear();
    }

    private void tick(final Animation animation) {
        if (closed || !animation.player.isOnline()
            || animation.player.getWorld() != animation.center.getWorld()
            || animations.get(animation.player.getUniqueId()) != animation) {
            stop(animation);
            return;
        }
        double progress = ++animation.frame / (double) animation.settings.frames();
        boolean fountains = animation.settings.fountains() > 0;
        double radius = animation.settings.radius() * (fountains ? 1 - Math.pow(1 - progress, 2) : progress);
        // Dust has no alpha: smaller particles and a sparse inner wake make the wave visually dissolve.
        float fade = (float) (1 - 0.8 * progress * progress);
        var edge = fountains ? dust(animation, fade) : animation.dust;
        var wake = fountains ? dust(animation, fade * 0.4F) : animation.dust;
        for (int index = 0; index < animation.settings.points(); index++) {
            emit(animation, index, radius, 0, edge);
            if (fountains && index % 4 == animation.frame % 4) {
                emit(animation, index, radius * 0.88, 0, wake);
            }
        }
        if (fountains) {
            fountains(animation, radius, fade);
        }
        if (animation.frame >= animation.settings.frames()) {
            stop(animation);
        }
    }

    private void fountains(final Animation animation, final double radius, final float fade) {
        var head = dust(animation, fade * 0.8F);
        var tail = dust(animation, fade * 0.4F);
        for (int fountain = 0; fountain < animation.settings.fountains(); fountain++) {
            int index = (fountain * animation.settings.points() / animation.settings.fountains() + animation.frame)
                % animation.settings.points();
            // Staggered rising sparks rotate around the crest, leaning outwards as they climb.
            double rise = ((animation.frame + fountain * 3) % 12) / 11.0;
            double height = animation.settings.fountainHeight() * rise;
            emit(animation, index, radius + height * 0.15, height, head);
            if (rise > 0.12) {
                double tailHeight = animation.settings.fountainHeight() * (rise - 0.12);
                emit(animation, index, radius + tailHeight * 0.15, tailHeight, tail);
            }
        }
    }

    private Particle.DustOptions dust(final Animation animation, final float scale) {
        return new Particle.DustOptions(animation.dust.getColor(),
            Math.max(0.1F, animation.settings.particleSize() * scale));
    }

    private void emit(
        final Animation animation,
        final int index,
        final double radius,
        final double height,
        final Particle.DustOptions dust
    ) {
        animation.player.spawnParticle(Particle.DUST,
            animation.center.getX() + radius * animation.cosines[index], animation.center.getY() + height,
            animation.center.getZ() + radius * animation.sines[index], 1, 0, 0, 0, 0, dust);
    }

    private void stop(final Animation animation) {
        animations.remove(animation.player.getUniqueId(), animation);
        if (animation.task != null) {
            animation.task.cancel();
        }
    }

    private static final class Animation {

        private final Player player;
        private final Location center;
        private final RingBurst settings;
        private final Particle.DustOptions dust;
        private final double[] cosines;
        private final double[] sines;
        private VexTask task;
        private int frame;

        private Animation(final Player player, final Location center, final RingBurst settings) {
            this.player = player;
            this.center = center;
            this.settings = settings;
            dust = new Particle.DustOptions(Color.fromRGB(settings.color()), settings.particleSize());
            cosines = new double[settings.points()];
            sines = new double[settings.points()];
            for (int index = 0; index < settings.points(); index++) {
                double angle = Math.PI * 2 * index / settings.points();
                cosines[index] = Math.cos(angle);
                sines[index] = Math.sin(angle);
            }
        }
    }
}
