package dev.vexsoft.core.paper.item;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.item.ItemService;
import dev.vexsoft.core.item.ItemStackBuilder;
import dev.vexsoft.core.item.VexItemKeys;
import dev.vexsoft.core.item.internal.ItemComponentAdapterService;
import dev.vexsoft.core.packets.service.FakeItemMetaService;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

@Dependencies({ItemComponentAdapterService.class, FakeItemMetaService.class})
public final class VexItemService implements ItemService {

  private final ItemComponentAdapterService components;
  private final FakeItemMetaService presentation;

  public VexItemService(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    components = checkedServices.require(ItemComponentAdapterService.class);
    presentation = checkedServices.require(FakeItemMetaService.class);
  }

  @Override
  public ItemStackBuilder builder(
      final NamespacedKey itemId,
      final Material material,
      final int amount
  ) {
    Material checkedMaterial = Objects.requireNonNull(material, "material");
    if (checkedMaterial.isAir()) {
      throw new IllegalArgumentException("material must not be air");
    }
    if (amount < 1 || amount > 99) {
      throw new IllegalArgumentException("amount must be between 1 and 99");
    }
    return builder(itemId, new ItemStack(checkedMaterial, amount));
  }

  @Override
  public ItemStackBuilder builder(final NamespacedKey itemId, final ItemStack itemStack) {
    ItemStack checkedItem = Objects.requireNonNull(itemStack, "itemStack");
    if (checkedItem.getType().isAir()) {
      throw new IllegalArgumentException("itemStack must not be air");
    }
    return new VexItemStackBuilder(itemId, checkedItem, components, presentation);
  }

  @Override
  public Optional<NamespacedKey> getItemId(final ItemStack itemStack) {
    if (itemStack == null || itemStack.getType().isAir()) {
      return Optional.empty();
    }
    String value = itemStack.getPersistentDataContainer().get(
        VexItemKeys.ITEM_ID,
        PersistentDataType.STRING
    );
    return value == null ? Optional.empty() : Optional.ofNullable(NamespacedKey.fromString(value));
  }

  @Override
  public boolean isItem(final ItemStack itemStack, final NamespacedKey itemId) {
    return getItemId(itemStack).filter(Objects.requireNonNull(itemId, "itemId")::equals).isPresent();
  }
}
