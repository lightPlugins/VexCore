package dev.vexsoft.core.paper.items;

import lombok.experimental.UtilityClass;
import org.bukkit.NamespacedKey;

/**
 * Contains the persistent keys shared by every Vex item
 */
@UtilityClass
public class VexItemKeys {
  public static final NamespacedKey ITEM_ID = new NamespacedKey("vexcore", "item_id");

  /** Marks stack-localized variants whose stored presentation must survive normalization. */
  public static final NamespacedKey PRESERVE_PRESENTATION = new NamespacedKey(
      "vexcore",
      "preserve_presentation"
  );
}
