package dev.vexsoft.core.paper.service.packets;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.display.FakeDisplayKind;
import dev.vexsoft.core.paper.packets.dummy.BobRotation;
import dev.vexsoft.core.paper.packets.service.DisplayPacketAdapterService;
import dev.vexsoft.core.paper.packets.service.PlayerDummyService;
import dev.vexsoft.core.paper.packets.service.SkinService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Manages owner-bound player dummies, including their skins, equipment, and animation tasks. */
@Dependencies({DisplayPacketAdapterService.class, SkinService.class, ScheduleService.class})
public final class VexPlayerDummyService implements PlayerDummyService, AutoCloseable {

    private final ServiceOwner owner;
    private final DisplayPacketAdapterService adapter;
    private final SkinService skins;
    private final ScheduleService schedules;
    private final Map<FakeDisplayHandle, State> states = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public VexPlayerDummyService(VexServiceRegistry services) {
        owner = services.getOwner();
        adapter = services.require(DisplayPacketAdapterService.class);
        skins = services.require(SkinService.class);
        schedules = services.require(ScheduleService.class);
    }

    public FakeDisplayHandle spawn(Player viewer, Location center, BobRotation animation) {
        if (closed || center.getWorld() != viewer.getWorld()) {
            throw new IllegalArgumentException("Dummy must be in the viewer's current world");
        }

        if (!Double.isFinite(center.getX()) || !Double.isFinite(center.getY()) || !Double.isFinite(center.getZ())) {
            throw new IllegalArgumentException("Invalid dummy center");
        }

        var handle = new FakeDisplayHandle(
            owner,
            viewer.getUniqueId(),
            adapter.allocateEntityId(),
            UUID.randomUUID(),
            FakeDisplayKind.DUMMY
        );
        var state = new State(viewer, center.clone(), Objects.requireNonNull(animation));

        adapter.spawnDummy(viewer, handle, center);
        states.put(handle, state);
        skins.resolve(viewer).thenAccept(skin -> {
            if (skin.isPresent() && !closed && states.get(handle) == state) {
                schedules.runFor(
                    viewer,
                    () -> {
                        if (!closed && states.get(handle) == state && viewer.getWorld() == state.center.getWorld()) {
                            adapter.skinDummy(viewer, handle, skin.get());
                        }
                    }
                );
            }
        });

        return handle;
    }

    public void armor(FakeDisplayHandle handle, ItemStack[] armor) {
        requireOwner(handle);

        if (armor.length != 4) {
            throw new IllegalArgumentException("Expected four armor slots");
        }

        State state = states.get(handle);
        Player viewer = state == null ? null : state.viewer;

        if (state == null || viewer == null || Arrays.equals(state.armor, armor)) {
            return;
        }

        ItemStack[] copy =
            Arrays.stream(armor).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);

        adapter.armorDummy(viewer, handle, copy);
        state.armor = copy;
    }

    public void animate(FakeDisplayHandle handle) {
        requireOwner(handle);
        State state = states.get(handle);
        Player viewer = state == null ? null : state.viewer;

        if (state == null) {
            return;
        }

        if (viewer == null || viewer.getWorld() != state.center.getWorld()) {
            remove(handle);

            return;
        }

        double seconds = (System.nanoTime() - state.started) / 1_000_000_000.0;
        Location location = state.center.clone().add(0, state.animation.height(seconds), 0);

        location.setYaw(state.center.getYaw() + state.animation.yaw(seconds));
        adapter.moveDummy(viewer, handle, location);
    }

    public boolean isActive(FakeDisplayHandle handle) {
        requireOwner(handle);

        return states.containsKey(handle);
    }

    public void remove(FakeDisplayHandle handle) {
        requireOwner(handle);
        State removed = states.remove(handle);

        if (removed != null) {
            adapter.removeDummy(removed.viewer.isOnline() ? removed.viewer : null, handle);
        }
    }

    public void removeAll(UUID id) {
        states.keySet().stream().filter(h -> h.getViewerId().equals(id)).toList().forEach(this::remove);
    }

    private void requireOwner(FakeDisplayHandle handle) {
        if (!handle.getOwner().equals(owner) || handle.getKind() != FakeDisplayKind.DUMMY) {
            throw new IllegalArgumentException("Foreign dummy handle");
        }
    }

    public void close() {
        closed = true;
        states.keySet().stream().toList().forEach(this::remove);
    }

    private static final class State {

        final Player viewer;
        final Location center;
        final BobRotation animation;
        final long started = System.nanoTime();
        ItemStack[] armor;

        State(Player viewer, Location center, BobRotation animation) {
            this.viewer = viewer;
            this.center = center;
            this.animation = animation;
        }
    }
}
