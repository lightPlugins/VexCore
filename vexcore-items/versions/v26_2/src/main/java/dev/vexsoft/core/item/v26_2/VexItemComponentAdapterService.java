package dev.vexsoft.core.item.v26_2;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.item.VexComponentKey;
import dev.vexsoft.core.item.VexCustomModelData;
import dev.vexsoft.core.item.VexEnchantments;
import dev.vexsoft.core.item.internal.ItemComponentAdapterService;
import dev.vexsoft.core.item.internal.VexComponentOperation;
import dev.vexsoft.core.item.internal.VexComponentOperationType;
import dev.vexsoft.core.item.internal.VexComponentPatch;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.key.Key;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;

@Dependencies
@SuppressWarnings("UnstableApiUsage")
public class VexItemComponentAdapterService implements ItemComponentAdapterService {

  public VexItemComponentAdapterService(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public void apply(final ItemStack itemStack, final VexComponentPatch patch) {
    ItemStack checkedItem = Objects.requireNonNull(itemStack, "itemStack");
    Objects.requireNonNull(patch, "patch").getOperations().forEach((component, operation) -> {
      if (component.getTarget() == dev.vexsoft.core.item.VexComponentTarget.ITEM) {
        apply(checkedItem, component.getKey(), operation);
      }
    });
  }

  @Override
  public void clearPresentation(final ItemStack itemStack) {
    ItemStack checkedItem = Objects.requireNonNull(itemStack, "itemStack");
    checkedItem.resetData(DataComponentTypes.CUSTOM_NAME);
    checkedItem.resetData(DataComponentTypes.LORE);
  }

  protected void apply(
      final ItemStack itemStack,
      final VexComponentKey key,
      final VexComponentOperation operation
  ) {
    switch (key) {
      case MAX_STACK_SIZE -> applyValue(
          itemStack,
          DataComponentTypes.MAX_STACK_SIZE,
          operation,
          Integer.class
      );
      case DAMAGE -> applyValue(itemStack, DataComponentTypes.DAMAGE, operation, Integer.class);
      case MAX_DAMAGE -> applyValue(
          itemStack,
          DataComponentTypes.MAX_DAMAGE,
          operation,
          Integer.class
      );
      case ENCHANTMENT_GLINT -> applyValue(
          itemStack,
          DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE,
          operation,
          Boolean.class
      );
      case ITEM_MODEL -> applyValue(
          itemStack,
          DataComponentTypes.ITEM_MODEL,
          operation,
          Key.class
      );
      case TOOLTIP_STYLE -> applyValue(
          itemStack,
          DataComponentTypes.TOOLTIP_STYLE,
          operation,
          Key.class
      );
      case RARITY -> applyValue(
          itemStack,
          DataComponentTypes.RARITY,
          operation,
          ItemRarity.class
      );
      case ENCHANTMENTS -> applyEnchantments(itemStack, operation);
      case CUSTOM_MODEL_DATA -> applyCustomModelData(itemStack, operation);
      case UNBREAKABLE -> applyFlag(itemStack, DataComponentTypes.UNBREAKABLE, operation);
      case DISPLAY_NAME, LORE -> throw new IllegalArgumentException(
          key + " must be handled by the packet presentation layer"
      );
    }
  }

  private <T> void applyValue(
      final ItemStack itemStack,
      final DataComponentType.Valued<T> component,
      final VexComponentOperation operation,
      final Class<T> valueType
  ) {
    switch (operation.getType()) {
      case SET -> itemStack.setData(component, valueType.cast(operation.getValue()));
      case UNSET -> itemStack.unsetData(component);
      case RESET -> itemStack.resetData(component);
    }
  }

  private void applyFlag(
      final ItemStack itemStack,
      final DataComponentType.NonValued component,
      final VexComponentOperation operation
  ) {
    switch (operation.getType()) {
      case SET -> itemStack.setData(component);
      case UNSET -> itemStack.unsetData(component);
      case RESET -> itemStack.resetData(component);
    }
  }

  private void applyEnchantments(
      final ItemStack itemStack,
      final VexComponentOperation operation
  ) {
    if (operation.getType() != VexComponentOperationType.SET) {
      applyWithoutValue(itemStack, DataComponentTypes.ENCHANTMENTS, operation);
      return;
    }
    VexEnchantments enchantments = (VexEnchantments) operation.getValue();
    ItemEnchantments.Builder builder = ItemEnchantments.itemEnchantments();
    for (Map.Entry<Enchantment, Integer> entry
        : enchantments.getEnchantments().entrySet()) {
      builder.add(entry.getKey(), entry.getValue());
    }
    itemStack.setData(DataComponentTypes.ENCHANTMENTS, builder.build());
  }

  private void applyCustomModelData(
      final ItemStack itemStack,
      final VexComponentOperation operation
  ) {
    if (operation.getType() != VexComponentOperationType.SET) {
      applyWithoutValue(itemStack, DataComponentTypes.CUSTOM_MODEL_DATA, operation);
      return;
    }
    VexCustomModelData value = (VexCustomModelData) operation.getValue();
    CustomModelData.Builder builder = CustomModelData.customModelData()
        .addFloats(value.getFloatValues())
        .addFlags(value.getFlagValues())
        .addStrings(value.getStringValues())
        .addColors(value.getColorValues());
    itemStack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, builder.build());
  }

  private void applyWithoutValue(
      final ItemStack itemStack,
      final DataComponentType component,
      final VexComponentOperation operation
  ) {
    switch (operation.getType()) {
      case SET -> throw new IllegalArgumentException("A value is required for this component");
      case UNSET -> itemStack.unsetData(component);
      case RESET -> itemStack.resetData(component);
    }
  }
}
