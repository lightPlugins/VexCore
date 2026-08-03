package dev.vexsoft.core.inventory.page.control;

import dev.vexsoft.core.inventory.InventoryKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores the active page control mode selected by each viewer
 */
public interface PageControlStateStore {

  /** Finds the active mode stored for the given viewer and control */
  public Optional<String> getActiveMode(
      UUID viewerId,
      InventoryKey inventoryKey,
      String areaId,
      String controlId
  );

  /** Stores the active mode for the given viewer and control */
  public void setActiveMode(
      UUID viewerId,
      InventoryKey inventoryKey,
      String areaId,
      String controlId,
      String modeId
  );

  /** Removes every control state associated with the given viewer */
  public void clear(UUID viewerId);

  /** Selects and stores the next mode exposed by this control */
  public default String cycleNext(
      final UUID viewerId,
      final InventoryKey inventoryKey,
      final String areaId,
      final PageControl control
  ) {
    return cycle(viewerId, inventoryKey, areaId, control, 1);
  }

  /** Selects and stores the previous mode exposed by this control */
  public default String cyclePrevious(
      final UUID viewerId,
      final InventoryKey inventoryKey,
      final String areaId,
      final PageControl control
  ) {
    return cycle(viewerId, inventoryKey, areaId, control, -1);
  }

  private String cycle(
      final UUID viewerId,
      final InventoryKey inventoryKey,
      final String areaId,
      final PageControl control,
      final int direction
  ) {
    control.validate();
    List<String> modes = control.getModeIds();
    String current = getActiveMode(viewerId, inventoryKey, areaId, control.getControlId())
        .orElse(control.getDefaultModeId());
    int index = modes.indexOf(current);
    if (index < 0) {
      index = modes.indexOf(control.getDefaultModeId());
    }
    String next = modes.get(Math.floorMod(index + direction, modes.size()));
    setActiveMode(viewerId, inventoryKey, areaId, control.getControlId(), next);
    return next;
  }
}
