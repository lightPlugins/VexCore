package dev.vexsoft.core.paper.inventory.page.control;

import dev.vexsoft.core.paper.inventory.InventoryKey;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Thread-safe, process-local storage for each viewer's active page-control modes. */
public final class InMemoryPageControlStateStore implements PageControlStateStore {

  private final ConcurrentMap<StateKey, String> states = new ConcurrentHashMap<>();

  @Override
  public Optional<String> getActiveMode(
      final UUID viewerId,
      final InventoryKey inventoryKey,
      final String areaId,
      final String controlId
  ) {
    return Optional.ofNullable(states.get(new StateKey(viewerId, inventoryKey, areaId, controlId)));
  }

  @Override
  public void setActiveMode(
      final UUID viewerId,
      final InventoryKey inventoryKey,
      final String areaId,
      final String controlId,
      final String modeId
  ) {
    states.put(
        new StateKey(viewerId, inventoryKey, areaId, controlId),
        Objects.requireNonNull(modeId, "modeId")
    );
  }

  @Override
  public void clear(final UUID viewerId) {
    states.keySet().removeIf(key -> key.viewerId().equals(viewerId));
  }

  private record StateKey(
      UUID viewerId,
      InventoryKey inventoryKey,
      String areaId,
      String controlId
  ) {
    private StateKey {
      Objects.requireNonNull(viewerId, "viewerId");
      Objects.requireNonNull(inventoryKey, "inventoryKey");
      Objects.requireNonNull(areaId, "areaId");
      Objects.requireNonNull(controlId, "controlId");
    }
  }
}
