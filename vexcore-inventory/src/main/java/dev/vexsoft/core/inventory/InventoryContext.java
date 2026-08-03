package dev.vexsoft.core.inventory;

import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.util.Objects;
import lombok.Getter;
import org.bukkit.entity.Player;

@Getter
public final class InventoryContext {

  private final VexServiceRegistry services;
  private final Player viewer;
  private final InventoryService inventoryService;

  public InventoryContext(
      final VexServiceRegistry services,
      final Player viewer,
      final InventoryService inventoryService
  ) {
    this.services = Objects.requireNonNull(services, "services");
    this.viewer = Objects.requireNonNull(viewer, "viewer");
    this.inventoryService = Objects.requireNonNull(inventoryService, "inventoryService");
  }
}
