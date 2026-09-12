package dev.vexsoft.core.paper.service.mob;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.mob.MobDamageResult;
import dev.vexsoft.core.paper.mob.MobDefinition;
import dev.vexsoft.core.paper.mob.MobHandle;
import dev.vexsoft.core.paper.mob.MobKey;
import dev.vexsoft.core.paper.mob.MobRemovalReason;
import dev.vexsoft.core.paper.mob.MobSnapshot;
import dev.vexsoft.core.paper.mob.MobSpawnRequest;
import dev.vexsoft.core.paper.nms.service.NmsMobAdapterService;
import dev.vexsoft.core.paper.packets.display.DisplayGlowColor;
import dev.vexsoft.core.paper.packets.service.PacketTransportAdapterService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.util.Vector;

/** Default owner-scoped custom mob lifecycle facade. */
@Dependencies({MobRegistryCoordinatorService.class, MobRuntimeCoordinatorService.class, NmsMobAdapterService.class,
    PacketTransportAdapterService.class})
public final class VexMobService implements MobService, Listener, AutoCloseable {

    private boolean visualListeners;
    private final Map<MobHandle, Visual> visuals = new ConcurrentHashMap<>();
    private final NmsMobAdapterService nms;
    private final PacketTransportAdapterService packets;
    private final ServiceOwner owner;
    private final MobRegistryCoordinatorService definitions;
    private final MobRuntimeCoordinatorService runtime;

    public VexMobService(final VexServiceRegistry services) {
        VexServiceRegistry checked = Objects.requireNonNull(services, "services");

        owner = checked.getOwner();
        nms = checked.require(NmsMobAdapterService.class);
        packets = checked.require(PacketTransportAdapterService.class);
        definitions = checked.require(MobRegistryCoordinatorService.class);
        runtime = checked.require(MobRuntimeCoordinatorService.class);
    }

    @Override
    public MobHandle spawnVisual(final Player viewer,
                                 final MobKey key,
                                 final Location location, final double scale) {
        if (!definitions.owns(owner, key) || !Double.isFinite(scale) || scale <= 0 || scale > 16
            || location.getWorld() != viewer.getWorld()) {
            throw new IllegalArgumentException("Invalid visual mob definition, scale or world");
        }
        location.checkFinite();
        startVisualListeners();
        MobDefinition definition = definitions.find(key).orElseThrow();
        Entity carrier = location.getWorld().createEntity(location, definition.entityType().getEntityClass());
        if (!(carrier instanceof Mob mob)) {
            throw new IllegalArgumentException("Visual definition must be a mob");
        }
        if (mob instanceof Ageable ageable) {
            if (definition.baby()) {
                ageable.setBaby();
            } else {
                ageable.setAdult();
            }
        }
        definition.initializer().ifPresent(initializer -> initializer.initialize(mob));
        nms.neutralize(mob);
        mob.setGravity(false);
        mob.setSilent(true);
        mob.setInvulnerable(true);
        mob.setCollidable(false);
        mob.customName(null);
        mob.setCustomNameVisible(false);
        Objects.requireNonNull(mob.getAttribute(Attribute.SCALE)).setBaseValue(scale);
        MobHandle handle = new MobHandle(UUID.randomUUID(), key);
        try {
            packets.sendBundle(viewer, nms.visualSpawnPackets(mob));
            visuals.put(handle, new Visual(viewer, mob));
        } catch (RuntimeException failure) {
            packets.send(viewer, nms.visualRemovePacket(mob));
            throw failure;
        }
        return handle;
    }

    @Override
    public void moveVisual(final MobHandle handle, final Location location) {
        Visual visual = visuals.get(handle);
        if (visual == null) {
            return;
        }
        location.checkFinite();
        if (location.getWorld() != visual.viewer().getWorld() || location.getWorld() != visual.mob().getWorld()
            || !visual.viewer().isOnline()) {
            removeVisual(handle);
            return;
        }
        packets.send(visual.viewer(), nms.visualMovePacket(visual.mob(), location));
    }

    @Override
    public void removeVisual(final MobHandle handle) {
        Visual visual = visuals.remove(handle);
        if (visual != null && visual.viewer().isOnline()) {
            packets.send(visual.viewer(), nms.visualRemovePacket(visual.mob()));
        }
    }

    @Override
    public MobHandle spawn(final MobSpawnRequest request) {
        MobSpawnRequest checked = Objects.requireNonNull(request, "request");

        if (!definitions.owns(owner, checked.definitionKey())) {
            throw new IllegalArgumentException(
                "Mob definition is not owned by this plugin: " + checked.definitionKey());
        }

        MobDefinition definition = definitions.find(checked.definitionKey()).orElseThrow();

        return runtime.spawn(owner, definition, checked);
    }

    @Override
    public Optional<MobSnapshot> find(final MobHandle handle) {
        return runtime.find(owner, handle);
    }

    @Override
    public Optional<MobSnapshot> find(final Entity entity) {
        return runtime.find(entity);
    }

    @Override
    public Collection<MobSnapshot> getActiveMobs() {
        return runtime.getActiveMobs(owner);
    }

    @Override
    public MobDamageResult damage(final MobHandle handle, final double amount) {
        return runtime.damage(owner, handle, amount);
    }

    @Override
    public MobSnapshot setHealth(final MobHandle handle, final double health) {
        return runtime.setHealth(owner, handle, health);
    }

    @Override
    public MobSnapshot setScale(final MobHandle handle, final double scale) {
        return runtime.setScale(owner, handle, scale);
    }

    @Override
    public MobSnapshot setVelocity(final MobHandle handle, final Vector velocity) {
        return runtime.setVelocity(owner, handle, velocity);
    }

    @Override
    public MobSnapshot setGlow(final MobHandle handle, final DisplayGlowColor color) {
        return runtime.setGlow(owner, handle, color);
    }

    @Override
    public MobSnapshot clearGlow(final MobHandle handle) {
        return runtime.clearGlow(owner, handle);
    }

    @Override
    public void refreshPresentation(final MobHandle handle) {
        runtime.refreshPresentation(owner, handle);
    }

    @Override
    public boolean remove(final MobHandle handle, final MobRemovalReason reason) {
        return runtime.remove(owner, handle, reason);
    }

    @Override
    public int removeAll(final MobRemovalReason reason) {
        return runtime.removeAll(owner, reason);
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
        List.copyOf(visuals.keySet()).forEach(this::removeVisual);
        runtime.removeAll(owner, MobRemovalReason.PLUGIN_DISABLE);
    }

    private synchronized void startVisualListeners() {
        if (!visualListeners) {
            var plugin = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("VexCore"));
            Bukkit.getPluginManager().registerEvents(this, plugin);
            visualListeners = true;
        }
    }

    @EventHandler
    private void onVisualQuit(final PlayerQuitEvent event) {
        removeViewerVisuals(event.getPlayer());
    }

    @EventHandler
    private void onVisualWorldChange(final PlayerChangedWorldEvent event) {
        removeViewerVisuals(event.getPlayer());
    }

    @EventHandler
    private void onVisualDeath(final PlayerDeathEvent event) {
        removeViewerVisuals(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onVisualWorldUnload(final WorldUnloadEvent event) {
        visuals.entrySet().stream().filter(entry -> entry.getValue().mob().getWorld() == event.getWorld())
            .map(Map.Entry::getKey).toList().forEach(this::removeVisual);
    }

    private void removeViewerVisuals(final Player player) {
        visuals.entrySet().stream().filter(entry -> entry.getValue().viewer() == player)
            .map(Map.Entry::getKey).toList().forEach(this::removeVisual);
    }

    private record Visual(Player viewer, Mob mob) {
    }
}
