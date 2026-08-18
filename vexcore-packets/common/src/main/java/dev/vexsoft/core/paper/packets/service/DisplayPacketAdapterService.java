package dev.vexsoft.core.paper.packets.service;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.packets.display.FakeBlockDisplayRequest;
import dev.vexsoft.core.paper.packets.display.FakeBlockDisplayUpdate;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.display.DisplayLifecycle;
import dev.vexsoft.core.paper.packets.display.FakeItemDisplayRequest;
import dev.vexsoft.core.paper.packets.display.FakeItemDisplayUpdate;
import dev.vexsoft.core.paper.packets.display.FakeTextDisplayRequest;
import dev.vexsoft.core.paper.packets.display.FakeTextDisplayUpdate;
import java.util.List;
import java.util.UUID;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Encodes version-specific display, interaction and passenger packets
 */
public interface DisplayPacketAdapterService extends VexService {

  /** Allocates an entity id that cannot collide with native server entities */
  int allocateEntityId();

  /** Spawns a virtual text display using the supplied handle */
  void spawnText(Player viewer, FakeDisplayHandle handle, FakeTextDisplayRequest request);

  /** Spawns a virtual block display using the supplied handle */
  void spawnBlock(Player viewer, FakeDisplayHandle handle, FakeBlockDisplayRequest request);

  /** Spawns a virtual item display using the supplied handle */
  void spawnItem(Player viewer, FakeDisplayHandle handle, FakeItemDisplayRequest request);

  /** Applies an update to a virtual text display */
  void updateText(Player viewer, FakeDisplayHandle handle, FakeTextDisplayUpdate update);

  /** Applies an update to a virtual block display */
  void updateBlock(Player viewer, FakeDisplayHandle handle, FakeBlockDisplayUpdate update);

  /** Applies an update to a virtual item display */
  void updateItem(Player viewer, FakeDisplayHandle handle, FakeItemDisplayUpdate update);

  /** Teleports a virtual display entity */
  void teleport(Player viewer, FakeDisplayHandle handle, Location location);

  /** Removes one or more virtual entity ids */
  void remove(Player viewer, int... entityIds);

  /** Spawns a virtual interaction hitbox */
  void spawnInteraction(
      Player viewer,
      int entityId,
      UUID entityUuid,
      Location location,
      float width,
      float height
  );

  /** Updates a virtual interaction hitbox */
  void updateInteraction(Player viewer, int entityId, float width, float height);

  /** Teleports a virtual entity id without requiring a display handle */
  void teleport(Player viewer, int entityId, Location location);

  /** Replaces the passengers mounted onto an entity id */
  void setPassengers(Player viewer, int vehicleEntityId, List<Integer> passengerEntityIds);

  /** Applies a local passenger translation to a virtual display */
  void setTranslation(
      Player viewer,
      FakeDisplayHandle handle,
      float offsetX,
      float offsetY,
      float offsetZ
  );

  /** Removes native display state owned by one plugin */
  void removeOwned(ServiceOwner owner);

  /** Removes native display state associated with one viewer */
  void removeViewer(UUID viewerId);

  /** Removes displays configured for a viewer lifecycle event */
  void removeViewer(Player viewer, DisplayLifecycle lifecycle);
}
