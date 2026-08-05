package dev.vexsoft.core.item;

import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemRarity;

/**
 * Defines a typed and version-independent item component
 */
@Getter
public class VexComponentData<T> {

  public static final VexComponentData<Component> DISPLAY_NAME = value(
      VexComponentKey.DISPLAY_NAME,
      VexComponentTarget.PACKET_PRESENTATION,
      Component.class
  );
  public static final VexComponentData<List<Component>> LORE = new VexComponentData<>(
      VexComponentKey.LORE,
      VexComponentTarget.PACKET_PRESENTATION,
      List.class,
      VexComponentData::copyLore
  );
  public static final VexComponentData<Integer> MAX_STACK_SIZE = integer(
      VexComponentKey.MAX_STACK_SIZE,
      1,
      99
  );
  public static final VexComponentData<Integer> DAMAGE = integer(
      VexComponentKey.DAMAGE,
      0,
      Integer.MAX_VALUE
  );
  public static final VexComponentData<Integer> MAX_DAMAGE = integer(
      VexComponentKey.MAX_DAMAGE,
      1,
      Integer.MAX_VALUE
  );
  public static final VexComponentData<VexEnchantments> ENCHANTMENTS = new VexComponentData<>(
      VexComponentKey.ENCHANTMENTS,
      VexComponentTarget.ITEM,
      VexEnchantments.class,
      VexComponentData::validateEnchantments
  );
  public static final VexComponentData<Boolean> ENCHANTMENT_GLINT = value(
      VexComponentKey.ENCHANTMENT_GLINT,
      VexComponentTarget.ITEM,
      Boolean.class
  );
  public static final VexComponentData<NamespacedKey> ITEM_MODEL = value(
      VexComponentKey.ITEM_MODEL,
      VexComponentTarget.ITEM,
      NamespacedKey.class
  );
  public static final VexComponentData<NamespacedKey> TOOLTIP_STYLE = value(
      VexComponentKey.TOOLTIP_STYLE,
      VexComponentTarget.ITEM,
      NamespacedKey.class
  );
  public static final VexComponentData<VexCustomModelData> CUSTOM_MODEL_DATA = value(
      VexComponentKey.CUSTOM_MODEL_DATA,
      VexComponentTarget.ITEM,
      VexCustomModelData.class
  );
  public static final VexComponentData<ItemRarity> RARITY = value(
      VexComponentKey.RARITY,
      VexComponentTarget.ITEM,
      ItemRarity.class
  );
  public static final VexFlagComponentData UNBREAKABLE = new VexFlagComponentData(
      VexComponentKey.UNBREAKABLE,
      VexComponentTarget.ITEM
  );

  private final VexComponentKey key;
  private final VexComponentTarget target;
  private final Class<?> valueType;
  private final UnaryOperator<T> normalizer;

  protected VexComponentData(
      final VexComponentKey key,
      final VexComponentTarget target,
      final Class<?> valueType,
      final UnaryOperator<T> normalizer
  ) {
    this.key = Objects.requireNonNull(key, "key");
    this.target = Objects.requireNonNull(target, "target");
    this.valueType = Objects.requireNonNull(valueType, "valueType");
    this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
  }

  /** Validates and copies a component value before it enters a builder */
  public T normalize(final T value) {
    T checkedValue = Objects.requireNonNull(value, "value");
    if (!valueType.isInstance(checkedValue)) {
      throw new IllegalArgumentException(
          "Expected " + valueType.getSimpleName() + " for " + key
      );
    }
    return normalizer.apply(checkedValue);
  }

  private static <T> VexComponentData<T> value(
      final VexComponentKey key,
      final VexComponentTarget target,
      final Class<T> valueType
  ) {
    return new VexComponentData<>(key, target, valueType, UnaryOperator.identity());
  }

  private static VexComponentData<Integer> integer(
      final VexComponentKey key,
      final int minimum,
      final int maximum
  ) {
    return new VexComponentData<>(key, VexComponentTarget.ITEM, Integer.class, value -> {
      if (value < minimum || value > maximum) {
        throw new IllegalArgumentException(
            key + " must be between " + minimum + " and " + maximum
        );
      }
      return value;
    });
  }

  private static List<Component> copyLore(final List<Component> lore) {
    for (Component line : lore) {
      Objects.requireNonNull(line, "lore line");
    }
    return List.copyOf(lore);
  }

  private static VexEnchantments validateEnchantments(final VexEnchantments value) {
    value.getEnchantments().forEach((enchantment, level) -> {
      Objects.requireNonNull(enchantment, "enchantment");
      if (level == null || level < 1 || level > 255) {
        throw new IllegalArgumentException("Enchantment levels must be between 1 and 255");
      }
    });
    return value;
  }
}
