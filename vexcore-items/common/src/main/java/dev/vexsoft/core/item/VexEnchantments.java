package dev.vexsoft.core.item;

import java.util.Map;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.bukkit.enchantments.Enchantment;

/**
 * Contains enchantments without exposing a version-specific component type
 */
@Value
@Builder(toBuilder = true)
public class VexEnchantments {
  @Singular("enchantment")
  Map<Enchantment, Integer> enchantments;
}