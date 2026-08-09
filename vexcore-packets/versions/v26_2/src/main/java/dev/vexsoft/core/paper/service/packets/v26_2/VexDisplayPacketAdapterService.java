package dev.vexsoft.core.paper.service.packets.v26_2;

import dev.vexsoft.core.paper.packets.v26_2.display.V26_2DisplayMapper;
import dev.vexsoft.core.paper.packets.v26_2.display.V26_2DisplayPackets;
import dev.vexsoft.core.paper.packets.v26_2.display.V26_2DisplayUpdates;
import dev.vexsoft.core.paper.packets.v26_2.display.V26_2PassengerPackets;

import java.util.ArrayList;
import org.joml.Vector3f;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.display.DisplayLifecycle;
import dev.vexsoft.core.paper.packets.display.DisplayGlowColor;
import dev.vexsoft.core.paper.packets.v26_2.effect.V26_2GlowPackets;
import dev.vexsoft.core.paper.packets.display.FakeItemDisplayRequest;
import dev.vexsoft.core.paper.packets.display.FakeItemDisplayUpdate;
import dev.vexsoft.core.paper.packets.display.FakeTextDisplayRequest;
import dev.vexsoft.core.paper.packets.display.FakeTextDisplayUpdate;
import dev.vexsoft.core.paper.service.packets.DisplayPacketAdapterService;
import dev.vexsoft.core.paper.service.packets.PacketTransportAdapterService;
import io.papermc.paper.adventure.PaperAdventure;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Interaction;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;

@Dependencies(PacketTransportAdapterService.class)
public final class VexDisplayPacketAdapterService implements DisplayPacketAdapterService {

  private final PacketTransportAdapterService transport;
  private final AtomicInteger entityIds = new AtomicInteger(Integer.MAX_VALUE);
  private final Map<FakeDisplayHandle, Display> displays = new ConcurrentHashMap<>();
  private final Map<FakeDisplayHandle, Set<DisplayLifecycle>> lifecycles =
      new ConcurrentHashMap<>();
  private final Map<FakeDisplayHandle, DisplayGlowColor> glowColors = new ConcurrentHashMap<>();
  private final Set<FakeDisplayHandle> glowTeams = ConcurrentHashMap.newKeySet();
  private final Map<Integer, Interaction> interactions = new ConcurrentHashMap<>();
  private final Map<Integer, UUID> interactionViewers = new ConcurrentHashMap<>();

  public VexDisplayPacketAdapterService(final VexServiceRegistry services) {
    this.transport = services.require(PacketTransportAdapterService.class);
  }

  @Override
  public int allocateEntityId() {
    return entityIds.getAndDecrement();
  }

  @Override
  public void spawnText(
      final Player viewer,
      final FakeDisplayHandle handle,
      final FakeTextDisplayRequest request
  ) {
    requireViewer(viewer, handle, request.getLocation());
    Display.TextDisplay display = new Display.TextDisplay(
        EntityTypes.TEXT_DISPLAY,
        ((CraftWorld) request.getLocation().getWorld()).getHandle()
    );
    prepare(display, handle, request.getLocation());
    display.setText(PaperAdventure.asVanilla(request.getText()));
    display.getEntityData().set(Display.TextDisplay.DATA_LINE_WIDTH_ID, request.getLineWidth(), true);
    display.getEntityData().set(
        Display.TextDisplay.DATA_BACKGROUND_COLOR_ID,
        request.getBackgroundColor(),
        true
    );
    display.setTextOpacity(request.getTextOpacity());
    display.setFlags(V26_2DisplayUpdates.textFlags(request));
    V26_2DisplayPackets.applyBase(
        display, request.getTransformation(), request.getBillboard(), request.getBrightness(),
        request.getViewRange(), request.getShadowRadius(), request.getShadowStrength(),
        request.getDisplayWidth(), request.getDisplayHeight(), request.getInterpolationDelay(),
        request.getInterpolationDuration(), request.getTeleportDuration()
    );
    displays.put(handle, display);
    lifecycles.put(handle, request.getLifecycle());
    transport.sendBundle(viewer, V26_2DisplayPackets.spawn(display));
  }

  @Override
  public void spawnItem(
      final Player viewer,
      final FakeDisplayHandle handle,
      final FakeItemDisplayRequest request
  ) {
    requireViewer(viewer, handle, request.getLocation());
    Display.ItemDisplay display = new Display.ItemDisplay(
        EntityTypes.ITEM_DISPLAY,
        ((CraftWorld) request.getLocation().getWorld()).getHandle()
    );
    prepare(display, handle, request.getLocation());
    display.setItemStack(CraftItemStack.asNMSCopy(request.getItemStack()));
    display.setItemTransform(V26_2DisplayMapper.toNms(request.getItemTransform()));
    display.setGlowingTag(request.isGlowing());
    V26_2DisplayPackets.applyBase(
        display, request.getTransformation(), request.getBillboard(), request.getBrightness(),
        request.getViewRange(), request.getShadowRadius(), request.getShadowStrength(),
        request.getDisplayWidth(), request.getDisplayHeight(), request.getInterpolationDelay(),
        request.getInterpolationDuration(), request.getTeleportDuration()
    );
    displays.put(handle, display);
    lifecycles.put(handle, request.getLifecycle());
    List<Object> packets = V26_2DisplayPackets.spawn(display);
    if (request.isGlowing()) {
      glowTeams.add(handle);
      if (request.getGlowColor() != null) {
        glowColors.put(handle, request.getGlowColor());
      }
      packets.add(V26_2GlowPackets.addTeam(display, request.getGlowColor()));
    }
    transport.sendBundle(viewer, packets);
  }

  @Override
  public void updateText(
      final Player viewer,
      final FakeDisplayHandle handle,
      final FakeTextDisplayUpdate update
  ) {
    Display display = displays.get(handle);
    if (!(display instanceof Display.TextDisplay textDisplay)) {
      return;
    }
    V26_2DisplayUpdates.applyText(textDisplay, update);
    sendMetadata(viewer, textDisplay);
  }

  @Override
  public void updateItem(
      final Player viewer,
      final FakeDisplayHandle handle,
      final FakeItemDisplayUpdate update
  ) {
    Display display = displays.get(handle);
    if (!(display instanceof Display.ItemDisplay itemDisplay)) {
      return;
    }
    boolean wasGlowing = itemDisplay.hasGlowingTag();
    V26_2DisplayUpdates.applyItem(itemDisplay, update);
    if (update.getGlowColor() != null) {
      glowColors.put(handle, update.getGlowColor());
    }
    List<Object> packets = new ArrayList<>();
    Object metadata = V26_2DisplayPackets.metadata(itemDisplay);
    if (metadata != null) {
      packets.add(metadata);
    }
    if (itemDisplay.hasGlowingTag() && !glowTeams.contains(handle)) {
      glowTeams.add(handle);
      packets.add(V26_2GlowPackets.addTeam(itemDisplay, glowColors.get(handle)));
    } else if (itemDisplay.hasGlowingTag() && update.getGlowColor() != null) {
      packets.add(V26_2GlowPackets.updateTeam(itemDisplay, update.getGlowColor()));
    } else if (wasGlowing && !itemDisplay.hasGlowingTag() && glowTeams.remove(handle)) {
      packets.add(V26_2GlowPackets.removeTeam(itemDisplay));
    }
    if (!packets.isEmpty()) {
      transport.sendBundle(viewer, packets);
    }
  }

  @Override
  public void teleport(
      final Player viewer,
      final FakeDisplayHandle handle,
      final Location location
  ) {
    Display display = displays.get(handle);
    if (display == null) {
      return;
    }
    requireViewer(viewer, handle, location);
    position(display, location);
    transport.send(viewer, V26_2DisplayPackets.teleport(handle.getEntityId(), location));
  }

  @Override
  public void remove(final Player viewer, final int... entityIds) {
    List<Object> packets = new ArrayList<>();
    for (int entityId : entityIds) {
      displays.entrySet().stream()
          .filter(entry -> entry.getKey().getEntityId() == entityId)
          .findFirst()
          .ifPresent(entry -> {
            if (glowTeams.remove(entry.getKey())) {
              packets.add(V26_2GlowPackets.removeTeam(entry.getValue()));
            }
            glowColors.remove(entry.getKey());
          });
      displays.keySet().removeIf(handle -> handle.getEntityId() == entityId);
      lifecycles.keySet().removeIf(handle -> handle.getEntityId() == entityId);
      interactions.remove(entityId);
      interactionViewers.remove(entityId);
    }
    packets.add(V26_2DisplayPackets.remove(entityIds));
    transport.sendBundle(viewer, packets);
  }

  @Override
  public void spawnInteraction(
      final Player viewer,
      final int entityId,
      final UUID entityUuid,
      final Location location,
      final float width,
      final float height
  ) {
    Interaction interaction = new Interaction(
        EntityTypes.INTERACTION,
        ((CraftWorld) location.getWorld()).getHandle()
    );
    interaction.setId(entityId);
    interaction.setUUID(entityUuid);
    position(interaction, location);
    interaction.setWidth(width);
    interaction.setHeight(height);
    interactions.put(entityId, interaction);
    interactionViewers.put(entityId, viewer.getUniqueId());
    transport.sendBundle(viewer, V26_2DisplayPackets.spawn(interaction));
  }

  @Override
  public void updateInteraction(
      final Player viewer,
      final int entityId,
      final float width,
      final float height
  ) {
    Interaction interaction = interactions.get(entityId);
    if (interaction == null) {
      return;
    }
    interaction.setWidth(width);
    interaction.setHeight(height);
    sendMetadata(viewer, interaction);
  }

  @Override
  public void teleport(final Player viewer, final int entityId, final Location location) {
    Entity entity = interactions.get(entityId);
    if (entity != null) {
      position(entity, location);
    }
    transport.send(viewer, V26_2DisplayPackets.teleport(entityId, location));
  }

  @Override
  public void setPassengers(
      final Player viewer,
      final int vehicleEntityId,
      final List<Integer> passengerEntityIds
  ) {
    Entity vehicle = findEntity(vehicleEntityId);
    if (vehicle == null) {
      // The constructor only reads entity state before we replace the actual vehicle id
      vehicle = new Interaction(
          EntityTypes.INTERACTION,
          ((CraftWorld) viewer.getWorld()).getHandle()
      );
    }
    transport.send(viewer, V26_2PassengerPackets.create(
        vehicle,
        vehicleEntityId,
        passengerEntityIds.stream().mapToInt(Integer::intValue).toArray()
    ));
  }

  @Override
  public void setTranslation(
      final Player viewer,
      final FakeDisplayHandle handle,
      final float offsetX,
      final float offsetY,
      final float offsetZ
  ) {
    Display display = displays.get(handle);
    if (display == null) {
      return;
    }
    V26_2DisplayPackets.setTranslation(
        display,
        new Vector3f(offsetX, offsetY, offsetZ)
    );
    sendMetadata(viewer, display);
  }

  @Override
  public void removeOwned(final ServiceOwner owner) {
    displays.keySet().removeIf(handle -> handle.getOwner().equals(owner));
    lifecycles.keySet().removeIf(handle -> handle.getOwner().equals(owner));
    glowColors.keySet().removeIf(handle -> handle.getOwner().equals(owner));
    glowTeams.removeIf(handle -> handle.getOwner().equals(owner));
  }

  @Override
  public void removeViewer(final UUID viewerId) {
    displays.keySet().removeIf(handle -> handle.getViewerId().equals(viewerId));
    lifecycles.keySet().removeIf(handle -> handle.getViewerId().equals(viewerId));
    glowColors.keySet().removeIf(handle -> handle.getViewerId().equals(viewerId));
    glowTeams.removeIf(handle -> handle.getViewerId().equals(viewerId));
    interactionViewers.entrySet().removeIf(entry -> {
      if (!entry.getValue().equals(viewerId)) {
        return false;
      }
      interactions.remove(entry.getKey());
      return true;
    });
  }

  @Override
  public void removeViewer(final Player viewer, final DisplayLifecycle lifecycle) {
    int[] entityIds = lifecycles.entrySet().stream()
        .filter(entry -> entry.getKey().getViewerId().equals(viewer.getUniqueId()))
        .filter(entry -> entry.getValue().contains(lifecycle))
        .map(Map.Entry::getKey)
        .mapToInt(FakeDisplayHandle::getEntityId)
        .toArray();
    if (entityIds.length > 0) {
      remove(viewer, entityIds);
    }
  }

  private void sendMetadata(final Player viewer, final Entity entity) {
    Object packet = V26_2DisplayPackets.metadata(entity);
    if (packet != null) {
      transport.send(viewer, packet);
    }
  }

  private Entity findEntity(final int entityId) {
    Interaction interaction = interactions.get(entityId);
    if (interaction != null) {
      return interaction;
    }
    return displays.entrySet().stream()
        .filter(entry -> entry.getKey().getEntityId() == entityId)
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }

  private static void prepare(
      final Entity entity,
      final FakeDisplayHandle handle,
      final Location location
  ) {
    entity.setId(handle.getEntityId());
    entity.setUUID(handle.getEntityUuid());
    position(entity, location);
  }

  private static void position(final Entity entity, final Location location) {
    entity.setPos(location.getX(), location.getY(), location.getZ());
    entity.setRot(location.getYaw(), location.getPitch());
    entity.setYHeadRot(location.getYaw());
  }

  private static void requireViewer(
      final Player viewer,
      final FakeDisplayHandle handle,
      final Location location
  ) {
    if (!viewer.getUniqueId().equals(handle.getViewerId())) {
      throw new IllegalArgumentException("Display handle belongs to another viewer");
    }
    if (location.getWorld() == null || !viewer.getWorld().equals(location.getWorld())) {
      throw new IllegalArgumentException("Fake displays must be in the viewer's current world");
    }
  }
}
