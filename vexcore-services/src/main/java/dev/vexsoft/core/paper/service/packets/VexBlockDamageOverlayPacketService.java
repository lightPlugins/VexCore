package dev.vexsoft.core.paper.service.packets;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.display.FakeBlockDamageOverlayRequest;
import dev.vexsoft.core.paper.packets.display.FakeBlockDamageOverlayUpdate;
import dev.vexsoft.core.paper.packets.display.FakeDisplayHandle;
import dev.vexsoft.core.paper.packets.display.FakeItemDisplayRequest;
import dev.vexsoft.core.paper.packets.display.FakeItemDisplayUpdate;
import dev.vexsoft.core.paper.packets.display.ItemDisplayTransform;
import dev.vexsoft.core.paper.packets.service.BlockDamageOverlayPacketService;
import dev.vexsoft.core.paper.packets.service.ItemDisplayPacketService;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Default owner-scoped resource-pack damage-overlay service. */
@Dependencies(ItemDisplayPacketService.class)
public final class VexBlockDamageOverlayPacketService
    implements BlockDamageOverlayPacketService, AutoCloseable {

  private final ItemDisplayPacketService itemDisplays;
  private final Map<FakeDisplayHandle, NamespacedKey> overlays = new ConcurrentHashMap<>();

  /** Creates the overlay service through VexCore's service registry. */
  public VexBlockDamageOverlayPacketService(final VexServiceRegistry services) {
    itemDisplays = Objects.requireNonNull(services, "services")
        .require(ItemDisplayPacketService.class);
  }

  @Override
  public FakeDisplayHandle spawn(
      final Player viewer,
      final FakeBlockDamageOverlayRequest request
  ) {
    Objects.requireNonNull(viewer, "viewer");
    FakeBlockDamageOverlayRequest checkedRequest = Objects.requireNonNull(request, "request");
    FakeDisplayHandle handle = itemDisplays.spawn(
        viewer,
        FakeItemDisplayRequest.builder(
            checkedRequest.getLocation(),
            overlayItem(checkedRequest.getModelPrefix(), checkedRequest.getDamageStage())
        )
            .itemTransform(ItemDisplayTransform.NONE)
            .transformation(checkedRequest.getTransformation())
            .viewRange(checkedRequest.getViewRange())
            .shadowRadius(0.0F)
            .shadowStrength(0.0F)
            .interpolationDelay(checkedRequest.getInterpolationDelay())
            .interpolationDuration(checkedRequest.getInterpolationDuration())
            .teleportDuration(checkedRequest.getTeleportDuration())
            .lifecycle(checkedRequest.getLifecycle())
            .build()
    );
    overlays.put(handle, checkedRequest.getModelPrefix());
    return handle;
  }

  @Override
  public void update(
      final FakeDisplayHandle handle,
      final FakeBlockDamageOverlayUpdate update
  ) {
    NamespacedKey modelPrefix = requireTracked(handle);
    FakeBlockDamageOverlayUpdate checkedUpdate = Objects.requireNonNull(update, "update");
    FakeItemDisplayUpdate.FakeItemDisplayUpdateBuilder builder =
        FakeItemDisplayUpdate.builder()
            .transformation(checkedUpdate.getTransformation())
            .interpolationDelay(checkedUpdate.getInterpolationDelay())
            .interpolationDuration(checkedUpdate.getInterpolationDuration())
            .teleportDuration(checkedUpdate.getTeleportDuration());
    if (checkedUpdate.getDamageStage() != null) {
      builder.itemStack(overlayItem(modelPrefix, checkedUpdate.getDamageStage()));
    }
    itemDisplays.update(handle, builder.build());
  }

  @Override
  public void teleport(final FakeDisplayHandle handle, final Location location) {
    requireTracked(handle);
    itemDisplays.teleport(handle, Objects.requireNonNull(location, "location"));
  }

  @Override
  public void remove(final FakeDisplayHandle handle) {
    if (overlays.remove(Objects.requireNonNull(handle, "handle")) != null) {
      itemDisplays.remove(handle);
    }
  }

  @Override
  public void removeAll(final Player viewer) {
    Objects.requireNonNull(viewer, "viewer");
    overlays.keySet().stream()
        .filter(handle -> handle.getViewerId().equals(viewer.getUniqueId()))
        .toList()
        .forEach(this::remove);
  }

  @Override
  public void close() {
    overlays.keySet().stream().toList().forEach(this::remove);
  }

  private NamespacedKey requireTracked(final FakeDisplayHandle handle) {
    NamespacedKey modelPrefix = overlays.get(Objects.requireNonNull(handle, "handle"));
    if (modelPrefix == null) {
      throw new IllegalArgumentException("Unknown block damage overlay handle");
    }
    return modelPrefix;
  }

  private static ItemStack overlayItem(
      final NamespacedKey modelPrefix,
      final int damageStage
  ) {
    if (damageStage < -1 || damageStage > 9) {
      throw new IllegalArgumentException("damageStage must be between -1 and 9");
    }
    if (damageStage < 0) {
      return new ItemStack(Material.AIR);
    }
    NamespacedKey model = new NamespacedKey(
        modelPrefix.getNamespace(),
        modelPrefix.getKey() + "_" + damageStage
    );
    ItemStack itemStack = new ItemStack(Material.PAPER);
    ItemMeta itemMeta = itemStack.getItemMeta();
    itemMeta.setItemModel(model);
    if (!itemStack.setItemMeta(itemMeta)) {
      throw new IllegalStateException("Unable to apply block damage overlay item model");
    }
    return itemStack;
  }
}
