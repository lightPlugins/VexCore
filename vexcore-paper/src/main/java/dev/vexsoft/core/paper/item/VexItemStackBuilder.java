package dev.vexsoft.core.paper.item;

import dev.vexsoft.core.item.ItemStackBuilder;
import dev.vexsoft.core.item.VexComponentData;
import dev.vexsoft.core.item.VexComponentTarget;
import dev.vexsoft.core.item.VexFlagComponentData;
import dev.vexsoft.core.item.VexItemKeys;
import dev.vexsoft.core.item.internal.ItemComponentAdapterService;
import dev.vexsoft.core.item.internal.VexComponentOperation;
import dev.vexsoft.core.item.internal.VexComponentOperationType;
import dev.vexsoft.core.item.internal.VexComponentPatch;
import dev.vexsoft.core.packets.service.FakeItemMetaService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class VexItemStackBuilder implements ItemStackBuilder {

  private final NamespacedKey itemId;
  private final ItemStack source;
  private final ItemComponentAdapterService components;
  private final FakeItemMetaService presentation;
  private final Map<VexComponentData<?>, VexComponentOperation> operations =
      new LinkedHashMap<>();

  public VexItemStackBuilder(
      final NamespacedKey itemId,
      final ItemStack source,
      final ItemComponentAdapterService components,
      final FakeItemMetaService presentation
  ) {
    this.itemId = Objects.requireNonNull(itemId, "itemId");
    this.source = Objects.requireNonNull(source, "source").clone();
    this.components = Objects.requireNonNull(components, "components");
    this.presentation = Objects.requireNonNull(presentation, "presentation");
  }

  @Override
  public <T> ItemStackBuilder setData(final VexComponentData<T> component, final T value) {
    VexComponentData<T> checkedComponent = Objects.requireNonNull(component, "component");
    if (checkedComponent instanceof VexFlagComponentData) {
      throw new IllegalArgumentException("Flag components do not accept a value");
    }
    operations.put(checkedComponent, VexComponentOperation.set(checkedComponent.normalize(value)));
    return this;
  }

  @Override
  public ItemStackBuilder setData(final VexFlagComponentData component) {
    operations.put(
        Objects.requireNonNull(component, "component"),
        VexComponentOperation.setFlag()
    );
    return this;
  }

  @Override
  public ItemStackBuilder unsetData(final VexComponentData<?> component) {
    operations.put(Objects.requireNonNull(component, "component"), VexComponentOperation.unset());
    return this;
  }

  @Override
  public ItemStackBuilder resetData(final VexComponentData<?> component) {
    operations.put(Objects.requireNonNull(component, "component"), VexComponentOperation.reset());
    return this;
  }

  @Override
  public ItemStack build() {
    ItemStack itemStack = source.clone();
    components.apply(itemStack, new VexComponentPatch(operations));
    components.clearPresentation(itemStack);
    itemStack.editPersistentDataContainer(container -> container.set(
        VexItemKeys.ITEM_ID,
        PersistentDataType.STRING,
        itemId.asString()
    ));
    applyPresentation();
    return itemStack;
  }

  private void applyPresentation() {
    operations.forEach((component, operation) -> {
      if (component.getTarget() != VexComponentTarget.PACKET_PRESENTATION) {
        return;
      }
      switch (component.getKey()) {
        case DISPLAY_NAME -> applyDisplayName(operation);
        case LORE -> applyLore(operation);
        default -> throw new IllegalStateException(
            "Unsupported presentation component: " + component.getKey()
        );
      }
    });
  }

  private void applyDisplayName(final VexComponentOperation operation) {
    if (operation.getType() == VexComponentOperationType.SET) {
      presentation.setDisplayName(
          VexItemKeys.ITEM_ID,
          itemId.asString(),
          (Component) operation.getValue()
      );
      return;
    }
    presentation.clearDisplayName(VexItemKeys.ITEM_ID, itemId.asString());
  }

  @SuppressWarnings("unchecked")
  private void applyLore(final VexComponentOperation operation) {
    if (operation.getType() == VexComponentOperationType.SET) {
      presentation.setLore(
          VexItemKeys.ITEM_ID,
          itemId.asString(),
          (List<Component>) operation.getValue()
      );
      return;
    }
    presentation.clearLore(VexItemKeys.ITEM_ID, itemId.asString());
  }
}
