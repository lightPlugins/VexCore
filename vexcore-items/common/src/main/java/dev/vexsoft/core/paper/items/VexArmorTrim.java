package dev.vexsoft.core.paper.items;

import java.util.Objects;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

/** Registry-resolved trim appearance, independent of Paper data components. */
public record VexArmorTrim(TrimPattern pattern, TrimMaterial material) {
  /** Validates that both registry entries are present. */
  public VexArmorTrim {
    Objects.requireNonNull(pattern, "pattern");
    Objects.requireNonNull(material, "material");
  }
}
