package dev.vexsoft.core.paper.inventory;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.inventory.InventoryContext;
import dev.vexsoft.core.inventory.InventoryElement;
import dev.vexsoft.core.inventory.InventoryService;
import dev.vexsoft.core.inventory.InventoryView;
import dev.vexsoft.core.inventory.MutableInventoryView;
import java.util.Map;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@Dependencies({InventoryService.class})
public final class VexInventoryListener implements Listener {

  private final VexInventoryService inventories;

  public VexInventoryListener(final VexServiceRegistry services) {
    InventoryService service = Objects.requireNonNull(services, "services")
        .require(InventoryService.class);
    if (!(service instanceof VexInventoryService inventoryService)) {
      throw new IllegalStateException("Unsupported InventoryService implementation");
    }
    this.inventories = inventoryService;
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onClick(final InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }
    if (!(event.getView().getTopInventory().getHolder() instanceof VexInventoryHolder holder)) {
      return;
    }
    if (!holder.getViewerId().equals(player.getUniqueId())) {
      event.setCancelled(true);
      return;
    }

    VexInventorySession session = inventories.getSession(holder.getViewerId());
    if (session == null || session.getCurrentView() == null) {
      event.setCancelled(true);
      return;
    }
    InventoryView view = session.getCurrentView();
    InventoryContext context = inventories.createContext(player);
    if (view instanceof MutableInventoryView mutableView) {
      mutableView.onInventoryClick(context, event);
      return;
    }
    event.setCancelled(true);
    if (event.getClick() == ClickType.DOUBLE_CLICK) {
      return;
    }

    int rawSlot = event.getRawSlot();
    if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
      return;
    }
    Map<Integer, InventoryElement> elements = session.getRenderedElements();
    InventoryElement element = elements.get(rawSlot);
    if (element != null && element.isClickable()) {
      element.onClick(context, event);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onDrag(final InventoryDragEvent event) {
    if (!(event.getView().getTopInventory().getHolder() instanceof VexInventoryHolder holder)) {
      return;
    }
    if (!(event.getWhoClicked() instanceof Player player)) {
      event.setCancelled(true);
      return;
    }
    VexInventorySession session = inventories.getSession(holder.getViewerId());
    if (session == null || session.getCurrentView() == null) {
      event.setCancelled(true);
      return;
    }
    if (session.getCurrentView() instanceof MutableInventoryView mutableView) {
      mutableView.onInventoryDrag(inventories.createContext(player), event);
      return;
    }
    event.setCancelled(true);
  }

  @EventHandler
  public void onClose(final InventoryCloseEvent event) {
    if (!(event.getPlayer() instanceof Player player)) {
      return;
    }
    if (event.getView().getTopInventory().getHolder() instanceof VexInventoryHolder holder) {
      inventories.handleClose(player, holder);
    }
  }

  @EventHandler
  public void onQuit(final PlayerQuitEvent event) {
    inventories.handleQuit(event.getPlayer());
  }
}
