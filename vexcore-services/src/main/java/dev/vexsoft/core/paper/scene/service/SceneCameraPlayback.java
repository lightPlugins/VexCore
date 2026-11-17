package dev.vexsoft.core.paper.scene.service;

import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.display.text.FakeTextDisplayRequest;
import dev.vexsoft.core.paper.packets.service.display.TextDisplayPacketService;
import dev.vexsoft.core.paper.packets.service.player.CameraPacketService;
import dev.vexsoft.core.paper.scene.SceneCinematic;
import dev.vexsoft.core.paper.scene.SceneCinematic.Pose;
import dev.vexsoft.core.paper.scene.SceneCinematic.Shot;
import dev.vexsoft.core.paper.service.actionbar.ActionBarService;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Moves one viewer-local camera and narration emitter along a scene's configured shots. */
@RequiredArgsConstructor
final class SceneCameraPlayback implements AutoCloseable {

    private static final String HUD_CHANNEL = "scene_cinematic";
    private static final int MILLIS_PER_TICK = 50;
    // The client snaps teleports beyond 64 blocks instead of interpolating them.
    private static final double MAX_GLIDE_DISTANCE = 60.0;
    private final Player viewer;
    private final SceneCinematic cinematic;
    private final long durationMillis;
    private final TextDisplayPacketService displays;
    private final CameraPacketService cameras;
    private final ActionBarService actionBars;
    private World world;
    private FakeDisplayHandle target;
    private boolean suppressed;
    private int shotIndex;
    private long movementUntil;

    void start() {
        world = viewer.getWorld();
        suppressed = true;
        actionBars.setSuppressed(viewer, HUD_CHANNEL, true);
        viewer.sendActionBar(Component.empty());
        var shot = cinematic.shots().getFirst();
        var first = location(shot.from());
        target = displays.spawn(viewer, FakeTextDisplayRequest.builder(first, Component.empty())
            .backgroundColor(0).viewRange(0).shadowStrength(0).build());
        // The spawn packet quantizes yaw and pitch, so send the precise starting pose before attaching.
        cameras.cut(viewer, target, first);
        if (cinematic.playerTime() != null) {
            viewer.setPlayerTime(cinematic.playerTime(), false);
        }
        cameras.attach(viewer, target, true);
        glide(shot, shot.from(), 0, shotEnd(0));
    }

    void playSound(final Sound sound) {
        cameras.playSound(viewer, target, sound);
    }

    void advance(final long elapsedMillis) {
        var shots = cinematic.shots();
        int next = shotIndex;
        while (next + 1 < shots.size() && shots.get(next + 1).startMillis() <= elapsedMillis) {
            next++;
        }
        if (next == shotIndex && elapsedMillis < movementUntil) {
            return;
        }
        var shot = shots.get(next);
        long end = shotEnd(next);
        var current = sample(shot, elapsedMillis, end);
        if (shotIndex != next) {
            cameras.cut(viewer, target, location(current));
            shotIndex = next;
        }
        glide(shot, current, elapsedMillis, end);
    }

    @Override
    public void close() {
        try {
            if (target != null) {
                try {
                    cameras.reset(viewer);
                } finally {
                    displays.remove(target);
                    target = null;
                }
            }
        } finally {
            if (suppressed) {
                suppressed = false;
                actionBars.setSuppressed(viewer, HUD_CHANNEL, false);
            }
        }
    }

    private Location location(final Pose pose) {
        return new Location(world, pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch());
    }

    private long shotEnd(final int index) {
        var shots = cinematic.shots();
        return index + 1 < shots.size() ? shots.get(index + 1).startMillis() : durationMillis;
    }

    private void glide(final Shot shot, final Pose current, final long elapsedMillis, final long end) {
        var finish = shot.to();
        double distance = Math.hypot(Math.hypot(finish.x() - current.x(), finish.y() - current.y()), finish.z() - current.z());
        long remaining = end - elapsedMillis;
        long movement = distance <= MAX_GLIDE_DISTANCE ? remaining
            : Math.max(1, (long) (remaining * (MAX_GLIDE_DISTANCE / distance)));
        movementUntil = elapsedMillis + movement;
        int ticks = (int) Math.min(Integer.MAX_VALUE, Math.ceilDiv(movement, MILLIS_PER_TICK));
        var destination = movementUntil == end ? finish : sample(shot, movementUntil, end);
        cameras.glide(viewer, target, location(destination), ticks);
    }

    private static Pose sample(final Shot shot, final long elapsedMillis, final long end) {
        double progress = Math.clamp((double) (elapsedMillis - shot.startMillis()) / (end - shot.startMillis()), 0, 1);
        var from = shot.from();
        var to = shot.to();
        return new Pose(
            interpolate(from.x(), to.x(), progress),
            interpolate(from.y(), to.y(), progress),
            interpolate(from.z(), to.z(), progress),
            (float) (from.yaw() + Math.IEEEremainder(to.yaw() - from.yaw(), 360) * progress),
            (float) interpolate(from.pitch(), to.pitch(), progress)
        );
    }

    private static double interpolate(final double from, final double to, final double fraction) {
        return from + (to - from) * fraction;
    }
}
